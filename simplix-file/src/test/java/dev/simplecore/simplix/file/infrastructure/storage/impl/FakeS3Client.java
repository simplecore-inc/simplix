package dev.simplecore.simplix.file.infrastructure.storage.impl;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * An in-memory S3 for unit tests. Operations the tests do not use throw
 * {@link UnsupportedOperationException} through the SDK's interface defaults.
 * <p>
 * Records what it was asked to do, can be told to fail, and notes every call made after
 * {@link #close()} so a test can prove no client is used once it is closed.
 */
final class FakeS3Client implements S3Client {

    record StoredObject(byte[] data, String contentType) {
    }

    private record Upload(String key, String contentType, Map<Integer, byte[]> parts) {
    }

    final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    final List<Integer> partLengths = Collections.synchronizedList(new ArrayList<>());
    final List<Long> outstandingAtPart = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger useAfterClose = new AtomicInteger();
    final AtomicInteger aborted = new AtomicInteger();

    private final Map<String, Upload> uploads = new ConcurrentHashMap<>();
    private final AtomicInteger uploadCounter = new AtomicInteger();
    private volatile boolean closed;
    private volatile boolean bucketMissing;

    /** Reports how many bytes the code under test has taken from its source so far. */
    volatile LongSupplier sourceConsumed = () -> 0L;
    /** Called at the start of every uploadPart, with the part number; may throw. */
    volatile Consumer<Integer> beforePart = n -> { };
    volatile Consumer<String> beforeComplete = id -> { };
    volatile Consumer<String> beforeAbort = id -> { };
    volatile Consumer<String> beforePut = key -> { };

    private long uploadedBytes;

    boolean isClosed() {
        return closed;
    }

    void bucketMissing() {
        bucketMissing = true;
    }

    private void touch(String call) {
        if (closed) {
            useAfterClose.incrementAndGet();
        }
        calls.add(call);
    }

    @Override
    public String serviceName() {
        return "s3";
    }

    @Override
    public void close() {
        closed = true;
        calls.add("close");
    }

    @Override
    public HeadBucketResponse headBucket(HeadBucketRequest request) {
        touch("headBucket");
        if (bucketMissing) {
            throw NoSuchBucketException.builder().message("no bucket").build();
        }
        return HeadBucketResponse.builder().build();
    }

    @Override
    public CreateBucketResponse createBucket(CreateBucketRequest request) {
        touch("createBucket");
        bucketMissing = false;
        return CreateBucketResponse.builder().build();
    }

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
        touch("putObject");
        beforePut.accept(request.key());
        byte[] data = read(body);
        if (request.contentLength() != null && request.contentLength() != data.length) {
            throw SdkClientException.create("content length " + request.contentLength() + " but body " + data.length);
        }
        objects.put(request.key(), new StoredObject(data, request.contentType()));
        return PutObjectResponse.builder().eTag("\"single\"").build();
    }

    @Override
    public CreateMultipartUploadResponse createMultipartUpload(CreateMultipartUploadRequest request) {
        touch("createMultipartUpload");
        String id = "upload-" + uploadCounter.incrementAndGet();
        uploads.put(id, new Upload(request.key(), request.contentType(), new TreeMap<>()));
        return CreateMultipartUploadResponse.builder().uploadId(id).build();
    }

    @Override
    public UploadPartResponse uploadPart(UploadPartRequest request, RequestBody body) {
        touch("uploadPart:" + request.partNumber());
        beforePart.accept(request.partNumber());
        Upload upload = uploads.get(request.uploadId());
        if (upload == null) {
            throw SdkClientException.create("unknown upload " + request.uploadId());
        }
        byte[] data = read(body);
        if (request.contentLength() != null && request.contentLength() != data.length) {
            throw SdkClientException.create("part length mismatch");
        }
        synchronized (this) {
            outstandingAtPart.add(sourceConsumed.getAsLong() - uploadedBytes);
            uploadedBytes += data.length;
        }
        partLengths.add(data.length);
        upload.parts().put(request.partNumber(), data);
        return UploadPartResponse.builder().eTag("\"part-" + request.partNumber() + "\"").build();
    }

    @Override
    public CompleteMultipartUploadResponse completeMultipartUpload(CompleteMultipartUploadRequest request) {
        touch("completeMultipartUpload");
        beforeComplete.accept(request.uploadId());
        Upload upload = uploads.remove(request.uploadId());
        if (upload == null) {
            throw SdkClientException.create("unknown upload " + request.uploadId());
        }
        ByteArrayOutputStream assembled = new ByteArrayOutputStream();
        for (CompletedPart part : request.multipartUpload().parts()) {
            byte[] data = upload.parts().get(part.partNumber());
            if (data == null) {
                throw SdkClientException.create("missing part " + part.partNumber());
            }
            assembled.writeBytes(data);
        }
        objects.put(upload.key(), new StoredObject(assembled.toByteArray(), upload.contentType()));
        return CompleteMultipartUploadResponse.builder().build();
    }

    @Override
    public AbortMultipartUploadResponse abortMultipartUpload(AbortMultipartUploadRequest request) {
        touch("abortMultipartUpload:" + request.uploadId());
        aborted.incrementAndGet();
        beforeAbort.accept(request.uploadId());
        uploads.remove(request.uploadId());
        return AbortMultipartUploadResponse.builder().build();
    }

    /** Ids of multipart uploads that were started and neither completed nor aborted. */
    List<String> openUploads() {
        return new ArrayList<>(uploads.keySet());
    }

    @Override
    public HeadObjectResponse headObject(HeadObjectRequest request) {
        touch("headObject");
        StoredObject object = objects.get(request.key());
        if (object == null) {
            throw NoSuchKeyException.builder().message("missing").statusCode(404).build();
        }
        return HeadObjectResponse.builder()
            .contentLength((long) object.data().length)
            .contentType(object.contentType())
            .lastModified(Instant.parse("2026-01-02T03:04:05Z"))
            .build();
    }

    @Override
    public <ReturnT> ReturnT getObject(GetObjectRequest request,
                                       ResponseTransformer<GetObjectResponse, ReturnT> transformer) {
        touch("getObject");
        StoredObject object = objects.get(request.key());
        if (object == null) {
            throw NoSuchKeyException.builder().message("missing").statusCode(404).build();
        }
        GetObjectResponse response = GetObjectResponse.builder()
            .contentLength((long) object.data().length)
            .contentType(object.contentType())
            .lastModified(Instant.parse("2026-01-02T03:04:05Z"))
            .build();
        try {
            return transformer.transform(response,
                AbortableInputStream.create(new ByteArrayInputStream(object.data())));
        } catch (Exception e) {
            throw SdkClientException.create("transform failed", e);
        }
    }

    @Override
    public DeleteObjectResponse deleteObject(DeleteObjectRequest request) {
        touch("deleteObject");
        objects.remove(request.key());
        return DeleteObjectResponse.builder().build();
    }

    private static byte[] read(RequestBody body) {
        try (var in = body.contentStreamProvider().newStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw SdkClientException.create("body unreadable", e);
        }
    }
}

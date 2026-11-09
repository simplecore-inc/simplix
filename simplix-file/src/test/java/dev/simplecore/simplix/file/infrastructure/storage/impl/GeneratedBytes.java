package dev.simplecore.simplix.file.infrastructure.storage.impl;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A deterministic stream of pseudo random bytes that counts what has been read from it, so a test
 * can compare how much the code under test took from the source with how much it handed on.
 */
final class GeneratedBytes extends InputStream {

    private final long length;
    private final long failAfter;
    private final AtomicLong consumed = new AtomicLong();

    GeneratedBytes(long length) {
        this(length, -1);
    }

    /**
     * @param failAfter number of bytes after which a read throws an IOException, or -1 for never
     */
    GeneratedBytes(long length, long failAfter) {
        this.length = length;
        this.failAfter = failAfter;
    }

    long consumed() {
        return consumed.get();
    }

    static byte byteAt(long position) {
        long x = position * 0x9E3779B97F4A7C15L;
        return (byte) (x >>> 56 ^ x >>> 24);
    }

    static String sha256Hex(long length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] chunk = new byte[64 * 1024];
            for (long position = 0; position < length; ) {
                int n = (int) Math.min(chunk.length, length - position);
                for (int i = 0; i < n; i++) {
                    chunk[i] = byteAt(position + i);
                }
                digest.update(chunk, 0, n);
                position += n;
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public int read(byte[] target, int offset, int len) throws IOException {
        long position = consumed.get();
        if (failAfter >= 0 && position >= failAfter) {
            throw new IOException("Source failed after " + position + " bytes");
        }
        if (position >= length) {
            return -1;
        }
        long limit = failAfter >= 0 ? Math.min(length, failAfter) : length;
        int n = (int) Math.min(len, limit - position);
        for (int i = 0; i < n; i++) {
            target[offset + i] = byteAt(position + i);
        }
        consumed.addAndGet(n);
        return n;
    }
}

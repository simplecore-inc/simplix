package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailAttachment")
class EmailAttachmentTest {

    @Nested
    @DisplayName("of factory method")
    class OfFactory {

        @Test
        @DisplayName("Should create regular attachment")
        void shouldCreateRegularAttachment() {
            byte[] content = "file content".getBytes();
            EmailAttachment attachment = EmailAttachment.of("doc.pdf", "application/pdf", content);

            assertThat(attachment.getFilename()).isEqualTo("doc.pdf");
            assertThat(attachment.getContentType()).isEqualTo("application/pdf");
            assertThat(attachment.getContent()).isEqualTo(content);
            assertThat(attachment.isInline()).isFalse();
            assertThat(attachment.getContentId()).isNull();
        }
    }

    @Nested
    @DisplayName("inline factory method")
    class InlineFactory {

        @Test
        @DisplayName("Should create inline attachment with content ID")
        void shouldCreateInlineAttachment() {
            byte[] content = new byte[]{1, 2, 3};
            EmailAttachment attachment = EmailAttachment.inline(
                    "logo-cid", "logo.png", "image/png", content
            );

            assertThat(attachment.getContentId()).isEqualTo("logo-cid");
            assertThat(attachment.getFilename()).isEqualTo("logo.png");
            assertThat(attachment.getContentType()).isEqualTo("image/png");
            assertThat(attachment.getContent()).isEqualTo(content);
            assertThat(attachment.isInline()).isTrue();
        }
    }

    @Nested
    @DisplayName("getSize")
    class GetSize {

        @Test
        @DisplayName("Should return content length in bytes")
        void shouldReturnContentLength() {
            byte[] content = new byte[]{1, 2, 3, 4, 5};
            EmailAttachment attachment = EmailAttachment.of("file.bin", "application/octet-stream", content);

            assertThat(attachment.getSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should return 0 when content is null")
        void shouldReturnZeroWhenContentIsNull() {
            EmailAttachment attachment = EmailAttachment.builder()
                    .filename("empty.txt")
                    .contentType("text/plain")
                    .build();

            assertThat(attachment.getSize()).isZero();
        }

        @Test
        @DisplayName("Should return 0 for empty content")
        void shouldReturnZeroForEmptyContent() {
            EmailAttachment attachment = EmailAttachment.of("empty.txt", "text/plain", new byte[0]);

            assertThat(attachment.getSize()).isZero();
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("Should default inline to false")
        void shouldDefaultInlineToFalse() {
            EmailAttachment attachment = EmailAttachment.builder()
                    .filename("file.txt")
                    .contentType("text/plain")
                    .content(new byte[]{1})
                    .build();

            assertThat(attachment.isInline()).isFalse();
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            byte[] content = new byte[]{1, 2, 3};
            EmailAttachment a = EmailAttachment.of("file.txt", "text/plain", content);
            EmailAttachment b = EmailAttachment.of("file.txt", "text/plain", content);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when filename differs")
        void shouldNotBeEqualWhenFilenameDiffers() {
            byte[] content = new byte[]{1, 2, 3};
            EmailAttachment a = EmailAttachment.of("file1.txt", "text/plain", content);
            EmailAttachment b = EmailAttachment.of("file2.txt", "text/plain", content);

            assertThat(a).isNotEqualTo(b);
        }
    }
}

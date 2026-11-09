package dev.simplecore.simplix.file.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DownloadFileNames")
class DownloadFileNamesTest {

    @Test
    @DisplayName("Should write both forms of a plain name")
    void shouldWriteBothForms() {
        assertThat(DownloadFileNames.attachment("report-2026.pdf"))
            .isEqualTo("attachment; filename=\"report-2026.pdf\"; filename*=UTF-8''report-2026.pdf");
    }

    @Test
    @DisplayName("Should percent-encode characters outside the RFC 5987 set")
    void shouldPercentEncode() {
        assertThat(DownloadFileNames.attachment("a b'c(d)*e,f;g.txt"))
            .contains("filename*=UTF-8''a%20b%27c%28d%29%2Ae%2Cf%3Bg.txt");
    }

    @Test
    @DisplayName("Should encode non-ASCII as UTF-8 and use underscores in the fallback")
    void shouldEncodeUtf8() {
        String value = DownloadFileNames.attachment("caf\u00E9 \uD55C\uAE00.txt");

        assertThat(value).contains("filename*=UTF-8''caf%C3%A9%20%ED%95%9C%EA%B8%80.txt");
        assertThat(value).startsWith("attachment; filename=\"caf_ __.txt\";");
    }

    @Test
    @DisplayName("Should drop control characters, so a name cannot end the header")
    void shouldDropControlCharacters() {
        String value = DownloadFileNames.attachment("a\r\nb\u0000c\u007f.txt");

        assertThat(value).isEqualTo("attachment; filename=\"abc.txt\"; filename*=UTF-8''abc.txt");
        assertThat(value).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    @DisplayName("Should replace quotes, backslashes and slashes")
    void shouldReplaceDelimiters() {
        String value = DownloadFileNames.attachment("a\"b\\c/d.txt");

        assertThat(value).startsWith("attachment; filename=\"a_b_c_d.txt\";");
        assertThat(value).contains("filename*=UTF-8''a%22b_c_d.txt");
    }

    @Test
    @DisplayName("Should fall back to a fixed name when nothing usable is left")
    void shouldFallBack() {
        assertThat(DownloadFileNames.attachment(null)).contains("filename=\"download\"");
        assertThat(DownloadFileNames.attachment("")).contains("filename=\"download\"");
        assertThat(DownloadFileNames.attachment(" \r\n ")).contains("filename=\"download\"");
    }

    @Test
    @DisplayName("Should cut a very long name")
    void shouldLimitLength() {
        String value = DownloadFileNames.attachment("x".repeat(1000));

        assertThat(value.length()).isLessThan(800);
    }
}

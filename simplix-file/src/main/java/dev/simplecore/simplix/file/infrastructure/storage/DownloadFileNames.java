package dev.simplecore.simplix.file.infrastructure.storage;

import java.nio.charset.StandardCharsets;

/**
 * Builds the {@code Content-Disposition} value that makes a browser save a download under a chosen
 * file name.
 * <p>
 * The value carries two forms of the name: a plain ASCII {@code filename} for old clients and the
 * RFC 5987 {@code filename*} with the UTF-8 name percent-encoded for every current one. The name is
 * untrusted (it is often the name a user uploaded), so control characters are dropped, path
 * separators and quotes are neutralised, and only characters that cannot end the header value or
 * open a new parameter reach the output.
 */
public final class DownloadFileNames {

    /** Name used when nothing usable is left of the one given. */
    public static final String FALLBACK_NAME = "download";

    private static final int MAX_CODE_POINTS = 255;
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private DownloadFileNames() {
    }

    /**
     * Content-Disposition header value for a download saved as {@code fileName}.
     *
     * @param fileName file name the browser should propose, may contain any characters
     * @return a value of the form {@code attachment; filename="..."; filename*=UTF-8''...}
     */
    public static String attachment(String fileName) {
        String clean = sanitize(fileName);
        return "attachment; filename=\"" + asciiFallback(clean) + "\"; filename*=UTF-8''" + encodeRfc5987(clean);
    }

    /**
     * The file name as it is sent: control characters removed, path separators replaced, length
     * limited.
     *
     * @param fileName name to clean, may be null
     * @return a non-empty name
     */
    static String sanitize(String fileName) {
        if (fileName == null) {
            return FALLBACK_NAME;
        }
        StringBuilder out = new StringBuilder(fileName.length());
        int count = 0;
        for (int i = 0; i < fileName.length() && count < MAX_CODE_POINTS; ) {
            int cp = fileName.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isISOControl(cp)) {
                continue;
            }
            out.appendCodePoint(cp == '/' || cp == '\\' ? '_' : cp);
            count++;
        }
        String result = out.toString().strip();
        return result.isEmpty() ? FALLBACK_NAME : result;
    }

    private static String asciiFallback(String clean) {
        StringBuilder out = new StringBuilder(clean.length());
        clean.codePoints().forEach(cp -> {
            boolean safe = cp >= 0x20 && cp < 0x7f && cp != '"' && cp != '\\' && cp != '%' && cp != ';';
            out.append(safe ? (char) cp : '_');
        });
        return out.toString();
    }

    private static String encodeRfc5987(String clean) {
        StringBuilder out = new StringBuilder();
        for (byte b : clean.getBytes(StandardCharsets.UTF_8)) {
            int v = b & 0xff;
            if (isAttrChar(v)) {
                out.append((char) v);
            } else {
                out.append('%').append(HEX[v >> 4]).append(HEX[v & 0x0f]);
            }
        }
        return out.toString();
    }

    private static boolean isAttrChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
            || "!#$&+-.^_`|~".indexOf(c) >= 0;
    }
}

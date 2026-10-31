package dev.simplecore.simplix.auth.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SimpliXStatelessTokenSigner")
class SimpliXStatelessTokenSignerTest {

    private final SimpliXStatelessTokenSigner signer = new SimpliXStatelessTokenSigner("test-secret");

    @Test
    @DisplayName("verifies its own signed payload back")
    void signAndVerifyRoundTrip() {
        String token = signer.sign("content:abc-123:1893456000");
        assertThat(signer.verify(token)).contains("content:abc-123:1893456000");
    }

    @Test
    @DisplayName("rejects a tampered payload")
    void rejectsTamperedPayload() {
        String token = signer.sign("content:abc-123:1893456000");
        String otherPayloadPart = signer.sign("content:zzz-999:1893456000").split("\\.")[0];
        String tampered = otherPayloadPart + "." + token.split("\\.")[1];
        assertThat(signer.verify(tampered)).isEmpty();
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsForeignSecret() {
        String foreign = new SimpliXStatelessTokenSigner("other-secret").sign("content:abc-123:1893456000");
        assertThat(signer.verify(foreign)).isEmpty();
    }

    @Test
    @DisplayName("rejects malformed tokens without throwing")
    void rejectsMalformedTokens() {
        assertThat(signer.verify(null)).isEmpty();
        assertThat(signer.verify("")).isEmpty();
        assertThat(signer.verify("no-separator")).isEmpty();
        assertThat(signer.verify(".only-signature")).isEmpty();
        assertThat(signer.verify("only-payload.")).isEmpty();
        assertThat(signer.verify("not@base64.also#bad")).isEmpty();
    }

    @Test
    @DisplayName("refuses to sign a blank payload")
    void refusesBlankPayload() {
        assertThatThrownBy(() -> signer.sign(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generates a per-boot key when no secret is configured")
    void generatesKeyWithoutConfiguredSecret() {
        SimpliXStatelessTokenSigner generated = new SimpliXStatelessTokenSigner("");
        String token = generated.sign("payload");
        assertThat(generated.verify(token)).contains("payload");
        // A second instance has a different random key, so the token dies with the boot.
        assertThat(new SimpliXStatelessTokenSigner("").verify(token)).isEmpty();
    }

    @Test
    @DisplayName("verify returns empty instead of Optional.of(null) edge cases")
    void verifyNeverReturnsNullPayload() {
        Optional<String> result = signer.verify(signer.sign("x"));
        assertThat(result).contains("x");
    }
}

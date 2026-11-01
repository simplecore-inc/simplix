package dev.simplecore.simplix.auth.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpliXAsymmetricTokenSignerTest {

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void aConfiguredPairSignsTokensThatVerifyAndCarryThePayload() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        SimpliXAsymmetricTokenSigner signer = new SimpliXAsymmetricTokenSigner(
                b64(pair.getPrivate().getEncoded()), b64(pair.getPublic().getEncoded()), "k1");

        String token = signer.sign("{\"sub\":\"u1\"}");

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(signer.verify(token)).contains("{\"sub\":\"u1\"}");
        String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"EdDSA\"").contains("\"kid\":\"k1\"");
    }

    @Test
    void aChangedPayloadOrSignatureNoLongerVerifies() {
        SimpliXAsymmetricTokenSigner signer = new SimpliXAsymmetricTokenSigner("", "", "k1");
        String token = signer.sign("{\"plan\":\"free\"}");
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"plan\":\"pro\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(signer.verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
        assertThat(signer.verify(parts[0] + "." + parts[1] + ".AAAA")).isEmpty();
        assertThat(signer.verify("not-a-token")).isEmpty();
    }

    @Test
    void anotherKeysTokenDoesNotVerify() {
        SimpliXAsymmetricTokenSigner one = new SimpliXAsymmetricTokenSigner("", "", "k1");
        SimpliXAsymmetricTokenSigner two = new SimpliXAsymmetricTokenSigner("", "", "k1");

        assertThat(two.verify(one.sign("{\"a\":1}"))).isEmpty();
    }

    @Test
    void thePublishedJwkVerifiesATokenWithoutTheSigner() throws Exception {
        SimpliXAsymmetricTokenSigner signer = new SimpliXAsymmetricTokenSigner("", "", "k1");
        String token = signer.sign("{\"a\":1}");
        Map<String, String> jwk = signer.publicJwk();
        assertThat(jwk).containsEntry("kty", "OKP").containsEntry("crv", "Ed25519").containsEntry("kid", "k1");

        // Rebuild the key from the JWK's raw x alone, as a client would.
        byte[] raw = Base64.getUrlDecoder().decode(jwk.get("x"));
        byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
        byte[] der = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, der, 0, prefix.length);
        System.arraycopy(raw, 0, der, prefix.length, raw.length);
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        String[] parts = token.split("\\.");
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void aMismatchedPairIsRefusedAtConstruction() throws Exception {
        KeyPair a = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair b = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        assertThatThrownBy(() -> new SimpliXAsymmetricTokenSigner(b64(a.getPrivate().getEncoded()),
                b64(b.getPublic().getEncoded()), "k1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SimpliXAsymmetricTokenSigner(b64(a.getPrivate().getEncoded()), "", "k1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingKeyFileIsWrittenOnceAndItsPairSurvivesARestart(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("env/signing-key.env");

        SimpliXAsymmetricTokenSigner first = SimpliXAsymmetricTokenSigner.fromKeyFile(file, "k1");
        String token = first.sign("{\"a\":1}");
        SimpliXAsymmetricTokenSigner restarted = SimpliXAsymmetricTokenSigner.fromKeyFile(file, "k1");

        assertThat(Files.readString(file)).contains("private-key=").contains("public-key=");
        assertThat(restarted.verify(token)).contains("{\"a\":1}");
        assertThat(restarted.publicJwk()).isEqualTo(first.publicJwk());
        assertThat(Files.getPosixFilePermissions(file)).hasSize(2);
    }
}

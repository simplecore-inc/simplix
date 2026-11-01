package dev.simplecore.simplix.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Signs payloads with Ed25519 as compact JWS tokens ({@code header.payload.signature}, {@code alg}
 * {@code EdDSA}), so a client holding only the public key verifies a token without calling the
 * server; {@link SimpliXStatelessTokenSigner} is the symmetric counterpart for tokens the server
 * itself reads back.
 *
 * <p>The key pair is given as base64 DER: the private key in PKCS#8, the public key in X.509
 * SubjectPublicKeyInfo. With no keys configured a pair is generated for this boot only, which
 * suits a development server; tokens it signed stop verifying after a restart. The public key is
 * published as a JSON Web Key ({@code kty} {@code OKP}, {@code crv} {@code Ed25519}) through
 * {@link #publicJwk()}.
 */
public class SimpliXAsymmetricTokenSigner {

    private static final String ALGORITHM = "Ed25519";
    /** The DER prefix of an Ed25519 SubjectPublicKeyInfo; the raw 32-byte key follows it. */
    private static final int X509_PREFIX_LENGTH = 12;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;
    private final String encodedHeader;

    /**
     * @param privateKeyBase64 base64 PKCS#8 Ed25519 private key; blank generates a per-boot pair
     * @param publicKeyBase64 base64 X.509 Ed25519 public key; required when the private key is given
     * @param keyId the {@code kid} written into every token and the published key
     */
    public SimpliXAsymmetricTokenSigner(String privateKeyBase64, String publicKeyBase64, String keyId) {
        try {
            if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
                KeyPair generated = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
                this.privateKey = generated.getPrivate();
                this.publicKey = generated.getPublic();
            } else {
                if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
                    throw new IllegalArgumentException("An Ed25519 private key needs its public key");
                }
                KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
                this.privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64.strip())));
                this.publicKey = factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.strip())));
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("The Ed25519 key pair cannot be read", e);
        }
        this.keyId = keyId == null || keyId.isBlank() ? "default" : keyId.strip();
        this.encodedHeader = encode(("{\"alg\":\"EdDSA\",\"typ\":\"JWT\",\"kid\":\"" + this.keyId + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        // A mismatched pair would sign tokens nobody can verify; refuse it at start-up instead.
        byte[] probe = "probe".getBytes(StandardCharsets.UTF_8);
        if (!verifySignature(probe, signBytes(probe))) {
            throw new IllegalArgumentException("The Ed25519 private and public keys are not a pair");
        }
    }

    /**
     * @param payload the claims, usually a JSON object
     * @return the compact JWS
     */
    public String sign(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Token payload must not be blank");
        }
        String signingInput = encodedHeader + "." + encode(payload.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + encode(signBytes(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * @param token a compact JWS
     * @return the payload when this signer's key signed the token under this signer's header
     */
    public Optional<String> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !encodedHeader.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
            if (!verifySignature(signingInput, signature)) {
                return Optional.empty();
            }
            return Optional.of(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * @return the public key as a JSON Web Key, ready to list in a JWKS document
     */
    public Map<String, String> publicJwk() {
        byte[] der = publicKey.getEncoded();
        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("kty", "OKP");
        jwk.put("crv", "Ed25519");
        jwk.put("kid", keyId);
        jwk.put("alg", "EdDSA");
        jwk.put("use", "sig");
        jwk.put("x", encode(Arrays.copyOfRange(der, X509_PREFIX_LENGTH, der.length)));
        return jwk;
    }

    /**
     * @return the key id written into every token
     */
    public String keyId() {
        return keyId;
    }

    private byte[] signBytes(byte[] input) {
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(input);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            // Ed25519 is mandatory on every JVM since 15 and the key was read at construction.
            throw new IllegalStateException("Ed25519 signing unavailable", e);
        }
    }

    private boolean verifySignature(byte[] input, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(input);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

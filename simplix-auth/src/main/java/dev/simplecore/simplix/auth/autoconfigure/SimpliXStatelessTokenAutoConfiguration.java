package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.token.SimpliXAsymmetricTokenSigner;
import dev.simplecore.simplix.auth.token.SimpliXStatelessTokenSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * Registers the {@link SimpliXStatelessTokenSigner} from {@code simplix.auth.stateless-token.secret} and the
 * {@link SimpliXAsymmetricTokenSigner} from {@code simplix.auth.asymmetric-token.*}.
 *
 * <p>Independent of {@code simplix.auth.enabled}: an application that signs links without SimpliX's own token
 * endpoints still gets the signer.
 */
@AutoConfiguration
public class SimpliXStatelessTokenAutoConfiguration {

    /**
     * @param secret the configured HMAC key; blank generates a random per-boot key
     * @return the signer
     */
    @Bean
    @ConditionalOnMissingBean
    public SimpliXStatelessTokenSigner simpliXStatelessTokenSigner(
            @Value("${simplix.auth.stateless-token.secret:}") String secret) {
        return new SimpliXStatelessTokenSigner(secret);
    }

    /**
     * @param keyFile a properties file holding the pair, written on first start when missing; it
     *     wins over the inline keys
     * @param privateKey base64 PKCS#8 Ed25519 private key; blank with no key file generates a per-boot pair
     * @param publicKey base64 X.509 Ed25519 public key
     * @param keyId the key id written into tokens and the published key
     * @return the signer
     */
    @Bean
    @ConditionalOnMissingBean
    public SimpliXAsymmetricTokenSigner simpliXAsymmetricTokenSigner(
            @Value("${simplix.auth.asymmetric-token.key-file:}") String keyFile,
            @Value("${simplix.auth.asymmetric-token.private-key:}") String privateKey,
            @Value("${simplix.auth.asymmetric-token.public-key:}") String publicKey,
            @Value("${simplix.auth.asymmetric-token.key-id:default}") String keyId) {
        if (!keyFile.isBlank()) {
            return SimpliXAsymmetricTokenSigner.fromKeyFile(Path.of(keyFile), keyId);
        }
        return new SimpliXAsymmetricTokenSigner(privateKey, publicKey, keyId);
    }
}

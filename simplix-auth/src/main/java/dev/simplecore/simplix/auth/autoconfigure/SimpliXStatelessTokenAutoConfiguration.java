package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.token.SimpliXStatelessTokenSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@link SimpliXStatelessTokenSigner} from {@code simplix.auth.stateless-token.secret}.
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
}

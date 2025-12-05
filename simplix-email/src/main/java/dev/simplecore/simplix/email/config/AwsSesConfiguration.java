package dev.simplecore.simplix.email.config;

import dev.simplecore.simplix.email.provider.AwsSesEmailProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Configuration for AWS SES email provider.
 * <p>
 * This configuration is only loaded when AWS SDK is on the classpath
 * and AWS SES is enabled in the configuration.
 */
@Configuration
@ConditionalOnClass(name = "software.amazon.awssdk.services.sesv2.SesV2Client")
@ConditionalOnProperty(prefix = "simplix.email.aws-ses", name = "enabled", havingValue = "true")
public class AwsSesConfiguration {

    @Bean
    public SesV2Client sesV2Client(EmailProperties properties) {
        EmailProperties.AwsSesConfig sesConfig = properties.getAwsSes();

        var builder = SesV2Client.builder()
                .region(Region.of(sesConfig.getRegion()));

        if (sesConfig.getAccessKey() != null && sesConfig.getSecretKey() != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(sesConfig.getAccessKey(), sesConfig.getSecretKey())
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Bean
    public AwsSesEmailProvider awsSesEmailProvider(SesV2Client sesClient, EmailProperties properties) {
        return new AwsSesEmailProvider(
                sesClient,
                properties.getFrom().getAddress(),
                properties.getAwsSes().getConfigurationSet()
        );
    }
}

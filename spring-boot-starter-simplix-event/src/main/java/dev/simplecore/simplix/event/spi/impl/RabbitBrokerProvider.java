package dev.simplecore.simplix.event.spi.impl;

import org.springframework.context.ApplicationContext;
import org.springframework.retry.support.RetryTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import dev.simplecore.simplix.event.spi.MessageBrokerAdapter;
import dev.simplecore.simplix.event.spi.MessageBrokerProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for the RabbitMQ message broker adapter.
 */
@Slf4j
public class RabbitBrokerProvider implements MessageBrokerProvider {

    @Override
    public String getBrokerType() {
        return "rabbitmq";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Check if RabbitMQ client is available on the classpath
            Class.forName("org.springframework.amqp.rabbit.connection.ConnectionFactory");
            return true;
        } catch (ClassNotFoundException e) {
            log.debug("RabbitMQ client not available on the classpath");
            return false;
        }
    }
    
    @Override
    public MessageBrokerAdapter createAdapter(ApplicationContext context, SimpliXEventProperties properties) {
        log.debug("Creating RabbitBrokerAdapter");
        
        try {
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);
            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
            ConnectionFactory connectionFactory = context.getBean(ConnectionFactory.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            
            // MessageConverter를 Spring 컨텍스트에서 가져오기
            MessageConverter messageConverter;
            try {
                messageConverter = context.getBean(MessageConverter.class);
            } catch (Exception e) {
                log.warn("MessageConverter not found in Spring context, using default");
                messageConverter = null; // RabbitTemplate이 기본 MessageConverter를 사용하도록 함
            }
            
            // RabbitTemplate 가져오기
            RabbitTemplate rabbitTemplate;
            try {
                rabbitTemplate = context.getBean(RabbitTemplate.class);
            } catch (Exception e) {
                log.warn("RabbitTemplate not found, creating a new one");
                rabbitTemplate = new RabbitTemplate(connectionFactory);
            }
            
            return new RabbitBrokerAdapter(
                    connectionFactory, 
                    messageConverter, 
                    retryTemplate, 
                    meterRegistry, 
                    properties, 
                    context, 
                    objectMapper, 
                    rabbitTemplate);
        } catch (Exception e) {
            log.error("Failed to create RabbitBrokerAdapter", e);
            throw new RuntimeException("Failed to create RabbitBrokerAdapter", e);
        }
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
} 
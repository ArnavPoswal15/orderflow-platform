package com.shop.payment.messaging;

import com.shop.events.EventTopics;
import com.shop.payment.domain.PaymentApplicationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@ConditionalOnProperty(name = "shop.kafka.listeners.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaListenerConfig {

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(EventTopics.dlt(record.topic()), record.partition()));

        ExponentialBackOff defaultBackOff = new ExponentialBackOff(1_000L, 2.0);
        defaultBackOff.setMaxAttempts(3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, defaultBackOff);
        errorHandler.setBackOffFunction((record, exception) -> {
            if (isPaymentNotReady(exception)) {
                ExponentialBackOff waitForOrder = new ExponentialBackOff(100L, 1.5);
                waitForOrder.setMaxElapsedTime(120_000L);
                return waitForOrder;
            }
            return defaultBackOff;
        });
        errorHandler.addNotRetryableExceptions(DeserializationException.class);
        errorHandler.addNotRetryableExceptions(org.springframework.messaging.converter.MessageConversionException.class);

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    private static boolean isPaymentNotReady(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PaymentApplicationService.PaymentNotReadyException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

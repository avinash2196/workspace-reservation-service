package com.example.workspace.reservation.event;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer and ReservationCreated publisher. No broker is needed to start: with no broker, the
 * producer keeps retrying its bootstrap connection in the background, and a send that cannot obtain
 * metadata fails after max.block.ms.
 */
@Configuration
public class ReservationEventsConfiguration {

    @Bean
    public Producer<String, String> reservationEventsProducer(
            @Value("${reservation-events.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${reservation-events.kafka.max-block-ms}") long maxBlockMs) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
        return new KafkaProducer<>(config, new StringSerializer(), new StringSerializer());
    }

    @Bean
    public ReservationEventPublisher reservationEventPublisher(Producer<String, String> reservationEventsProducer) {
        return new ReservationEventPublisher(reservationEventsProducer);
    }
}

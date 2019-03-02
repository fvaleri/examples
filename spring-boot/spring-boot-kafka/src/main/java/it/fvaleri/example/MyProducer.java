package it.fvaleri.example;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class MyProducer {
    private static final Logger LOG = LoggerFactory.getLogger(MyProducer.class);
    private static final long NUM_RECORDS = 10;

    KafkaTemplate<Long, String> kafkaTemplate;

    public MyProducer(KafkaTemplate<Long, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        // this is to let Spring Boot refresh transactional producer instances inactive for more than
        // transactional.id.expiration.ms (7 days by default) that would cause InvalidPidMappingException
        ((DefaultKafkaProducerFactory<Long, String>) kafkaTemplate.getProducerFactory()).setMaxAge(Duration.ofDays(1));
    }

    @Bean
    public NewTopic myTopic() {
        return TopicBuilder.name("my-topic")
            .partitions(-1)
            .replicas(-1)
            .build();
    }

    // we send business data to Kafka, so we use the Kafka TM instead of default TM
    // when TX are enabled, Spring Boot handles a cache of transactional producer instances, instead of just one
    // Kafka does not support XA transactions, so Spring Boot just performs two transactions in the background (db first)
    @Transactional("kafkaTransactionManager")
    public void send(boolean error) throws InterruptedException {
        // read from the database, process, write the result and send messages
        for (long i = 0; i < NUM_RECORDS; i++) {
            CompletableFuture<SendResult<Long, String>> result = kafkaTemplate.send("my-topic", i, "test" + i);
            result.whenComplete((sr, ex) ->
                LOG.info("Record({}, {}) sent to {}-{} with offset {}", sr.getProducerRecord().key(), sr.getProducerRecord().value(),
                    sr.getRecordMetadata().topic(), sr.getRecordMetadata().partition(), sr.getRecordMetadata().offset())
            );
            // for testing rollback of transactions on unchecked exception
            if (error && i > 5) {
                throw new RuntimeException("boom");
            }
            TimeUnit.MILLISECONDS.sleep(1_000);
        }
    }
}

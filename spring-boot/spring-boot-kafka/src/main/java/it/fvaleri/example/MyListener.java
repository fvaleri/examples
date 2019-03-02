package it.fvaleri.example;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.backoff.FixedBackOff;

@Service
public class MyListener {
    private static final Logger LOG = LoggerFactory.getLogger(MyListener.class);

    private KafkaTemplate<Long, String> kafkaTemplate;

    public MyListener(KafkaTemplate<Long, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<Integer, String> consumerFactory(ConsumerFactory consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Integer, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            // 10 seconds pause, 10 retries.
            new DeadLetterPublishingRecoverer(kafkaTemplate), new FixedBackOff(10_000L, 10L))
        );
        return factory;
    }

    // the concurrency value determines the number of consumers in the group (default: 1)
    @KafkaListener(id = "my-consumer", groupId = "my-group", topics = "my-topic", concurrency = "3")
    // we don't send business data to Kafka, so we use the default TM instead of Kafka TM
    @Transactional("transactionManager")
    public void listen(ConsumerRecord<?, ?> record) {
        LOG.info("Record({}, {}) fetched from {}-{} with offset {}",
            record.key(), record.value(), record.topic(), record.partition(), record.offset());
        // process and write to the database
    }
}

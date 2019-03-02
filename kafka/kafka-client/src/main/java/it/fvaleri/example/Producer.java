package it.fvaleri.example;

import java.util.Properties;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.LongSerializer;

import static it.fvaleri.example.Configuration.BOOTSTRAP_SERVERS;
import static it.fvaleri.example.Configuration.CLIENT_ID;
import static it.fvaleri.example.Configuration.MESSAGE_SIZE_BYTES;
import static it.fvaleri.example.Configuration.NUM_MESSAGES;
import static it.fvaleri.example.Configuration.PROCESSING_DELAY_MS;
import static it.fvaleri.example.Configuration.PRODUCER_CONFIG;
import static it.fvaleri.example.Configuration.TOPIC_NAME;

public class Producer extends Client implements Callback {
    public Producer(String threadName) {
        super(threadName);
    }

    @Override
    public void execute() throws Exception {
        // the producer instance is thread safe
        try (var producer = createKafkaProducer()) {
            createTopics(TOPIC_NAME);
            byte[] value = randomBytes(MESSAGE_SIZE_BYTES);
            while (!closed.get() && messageCount.get() < NUM_MESSAGES) {
                sleep(PROCESSING_DELAY_MS);
                // async send but still blocks when buffer.memory is full or metadata are not available
                // InitProducerId(leader), Produce(leader)
                producer.send(new ProducerRecord<>(TOPIC_NAME, messageCount.get(), value), this);
                messageCount.incrementAndGet();
            }
        }
    }

    private KafkaProducer<Long, byte[]> createKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, CLIENT_ID);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        addConfig(props, PRODUCER_CONFIG);
        addSharedConfig(props);
        return new KafkaProducer<>(props);
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception e) {
        if (e != null) {
            LOG.error(e.getMessage());
            if (!retriable(e)) {
                shutdown(e);
            }
        } else {
            LOG.debug("Record sent to {}-{} with offset {}",
                metadata.topic(), metadata.partition(), metadata.offset());
        }
    }
}

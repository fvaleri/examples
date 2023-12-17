package it.fvaleri.example;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.NoOffsetForPartitionException;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.clients.consumer.OffsetOutOfRangeException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.LongDeserializer;

import static java.time.Duration.ofMillis;
import static java.util.Collections.singleton;

public class Consumer extends Client implements ConsumerRebalanceListener, OffsetCommitCallback {
    private KafkaConsumer<Long, byte[]> kafkaConsumer;
    private Map<TopicPartition, OffsetAndMetadata> pendingOffsets = new HashMap<>();

    public Consumer(String threadName) {
        super(threadName);
    }

    @Override
    public void execute() throws Exception {
        // the consumer instance is NOT thread safe
        try (var consumer = createKafkaConsumer()) {
            kafkaConsumer = consumer;
            consumer.subscribe(singleton(Configuration.TOPIC_NAME), this);
            LOG.info("Subscribed to {}", Configuration.TOPIC_NAME);
            while (!closed.get() && messageCount.get() < Configuration.NUM_MESSAGES) {
                try {
                    // next poll must be called within session.timeout.ms to avoid rebalance
                    // FindCoordinator(any), OffsetFetch(group-coord), Metadata(any), Fetch(leader)
                    ConsumerRecords<Long, byte[]> records = consumer.poll(ofMillis(Configuration.POLL_TIMEOUT_MS));
                    if (!records.isEmpty()) {
                        for (ConsumerRecord<Long, byte[]> record : records) {
                            LOG.debug("Record fetched from {}-{} with offset {}",
                                record.topic(), record.partition(), record.offset());
                            sleep(Configuration.PROCESSING_DELAY_MS);
                            // we only add to pending offsets after processing
                            pendingOffsets.put(new TopicPartition(record.topic(), record.partition()),
                                new OffsetAndMetadata(record.offset() + 1, null));
                            if (messageCount.incrementAndGet() == Configuration.NUM_MESSAGES) {
                                break;
                            }
                        }
                        // commit after processing (at-least-once semantics)
                        consumer.commitAsync(pendingOffsets, this); // OffsetCommit(group-coord)
                        pendingOffsets.clear();
                    }
                } catch (OffsetOutOfRangeException | NoOffsetForPartitionException e) {
                    // invalid or no offset found without auto.reset.policy
                    LOG.info("Invalid or no offset found, using latest");
                    consumer.seekToEnd(e.partitions());
                    consumer.commitSync();
                } catch (Exception e) {
                    LOG.error(e.getMessage());
                    if (!retriable(e)) {
                        shutdown(e);
                    }
                }
            }
        }
    }

    private KafkaConsumer<Long, byte[]> createKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Configuration.BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, Configuration.CLIENT_ID);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, Configuration.GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        addConfig(props, Configuration.CONSUMER_CONFIG);
        addSecurityConfig(props);
        return new KafkaConsumer<>(props);
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        LOG.debug("Assigned partitions: {}", partitions);
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        LOG.debug("Revoked partitions: {}", partitions);
        // commit pending offsets before losing the partition ownership
        kafkaConsumer.commitSync(pendingOffsets);
        pendingOffsets.clear();
    }

    @Override
    public void onPartitionsLost(Collection<TopicPartition> partitions) {
        LOG.debug("Lost partitions: {}", partitions);
        // this is called when partitions are reassigned before we had a chance to revoke them gracefully
        // we can't commit pending offsets because these partitions are probably owned by other consumers already
        // nevertheless, we may need to do some other cleanup
    }

    @Override
    public void onComplete(Map<TopicPartition, OffsetAndMetadata> map, Exception e) {
        if (e != null) {
            LOG.error("Failed to commit offsets");
            if (!retriable(e)) {
                shutdown(e);
            }
        }
    }
}

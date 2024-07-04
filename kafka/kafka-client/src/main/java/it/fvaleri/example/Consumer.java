package it.fvaleri.example;

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
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.LongDeserializer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.time.Duration.ofMillis;
import static java.util.Collections.singleton;

public class Consumer extends Client implements ConsumerRebalanceListener, OffsetCommitCallback {
    private KafkaConsumer<Long, byte[]> kafkaConsumer;
    private Map<TopicPartition, OffsetAndMetadata> pendingOffsets = new HashMap<>();

    public Consumer(String threadName) {
        super(threadName);
    }

    @Override
    public void execute() {
        // the consumer instance is NOT thread safe
        try (var consumer = createKafkaConsumer()) {
            kafkaConsumer = consumer;
            consumer.subscribe(singleton(Configuration.TOPIC_NAME), this);
            System.out.printf("Subscribed to %s%n", Configuration.TOPIC_NAME);
            while (!closed.get() && messageCount.get() < Configuration.NUM_MESSAGES) {
                try {
                    // next poll must be called within session.timeout.ms to avoid rebalance
                    // FindCoordinator(any), OffsetFetch(group-coord), Metadata(any), Fetch(leader)
                    ConsumerRecords<Long, byte[]> records = consumer.poll(ofMillis(Configuration.POLL_TIMEOUT_MS));
                    if (!records.isEmpty()) {
                        for (ConsumerRecord<Long, byte[]> record : records) {
                            System.out.printf("Record fetched from partition %s-%d offset %d%n",
                                record.topic(), record.partition(), record.offset());
                            sleepFor(Configuration.PROCESSING_DELAY_MS);
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
                    System.out.println("Invalid or no offset found without auto.reset.policy, using latest");
                    consumer.seekToEnd(e.partitions());
                    consumer.commitSync();
                } catch (RecordDeserializationException e) {
                    // parsed records are returned first, the RDE is thrown on the next poll
                    System.out.printf("Skipping invalid record at partition %s offset %d%n", e.topicPartition(), e.offset());
                    consumer.seek(e.topicPartition(), e.offset() + 1);
                    // in addition to skip the bad record you may want to send it to a DLQ (see KIP-1036)
                    if (messageCount.incrementAndGet() == Configuration.NUM_MESSAGES) {
                        break;
                    }
                } catch (Exception e) {
                    System.err.println(e.getMessage());
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
        System.out.printf("Assigned partitions: %s%n", partitions);
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        System.out.printf("Revoked partitions: %s%n", partitions);
        // commit pending offsets before losing the partition ownership
        kafkaConsumer.commitSync(pendingOffsets);
        pendingOffsets.clear();
    }

    @Override
    public void onPartitionsLost(Collection<TopicPartition> partitions) {
        System.out.printf("Lost partitions: %s%n", partitions);
        // this is called when partitions are reassigned before we had a chance to revoke them gracefully
        // we can't commit pending offsets because these partitions are probably owned by other consumers already
        // nevertheless, we may need to do some other cleanup
    }

    @Override
    public void onComplete(Map<TopicPartition, OffsetAndMetadata> map, Exception e) {
        if (e != null) {
            System.err.println("Failed to commit offsets");
            if (!retriable(e)) {
                shutdown(e);
            }
        }
    }
}

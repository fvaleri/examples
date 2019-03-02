package it.fvaleri.example;

import static java.time.Duration.ofMillis;
import static java.util.Collections.singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;

/** 
 * A multi-threaded consumer can help when the record processing time varies a lot,
 * but we still want automatic rebalancing in case of an application instance crash.
 * <br><br>
 * When consuming messages, we may need to call an external service that may be slow 
 * or temporarily not available and we have to retry indefinitely. In this case, record
 * processing can take a very long time causing periodic rebalances impacting every
 * subscribed topic.
 * <br><br>
 * <pre>
 *      /------------------<---------------------\
 *     /                                          \
 *   poll --> create and submit tasks --> commit offsets
 *                     |
 *                     V
 *       thread1 (process records from partition 1)          
 *       thread2 (process records from partition 2)
 *       thread3 (process records from partition 3)
 * </pre>
 */
public class Main implements OffsetCommitCallback, ConsumerRebalanceListener {
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String GROUP_ID = "my-group";
    private static final String TOPIC_NAME = "my-topic";
    private static final long POLL_TIMEOUT_MS = 30_000;
    private static final long COMMIT_INTERVAL_MS = 5_000;

    private ExecutorService executor = 
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private KafkaConsumer<String, String> kafkaConsumer;
    private Map<TopicPartition, Task> activeTasks = new HashMap<>();
    private Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
    private long lastCommitTimeMs = System.currentTimeMillis();
    
    public static void main(String[] args) {
        new Main().run();
    }

    // consumer's methods are only called from the main thread to avoid expensive synchronization
    public void run() {
        System.out.println("Starting application instance");
        createTopics(TOPIC_NAME);
        try (var consumer = createKafkaConsumer()) {
            kafkaConsumer = consumer;
            consumer.subscribe(singleton(TOPIC_NAME));
            while (true) {
                // the poll method is called in parallel with processing
                System.out.println("Fetching new records");
                ConsumerRecords<String, String> records = consumer.poll(ofMillis(POLL_TIMEOUT_MS));
                handleFetchedRecords(records);
                checkActiveTasks();
                commitOffsets();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private KafkaConsumer<String, String> createKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // offsets have to be committed manually at appropriate times
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // we set a small max.poll.interval.ms to prove that a long processing won't trigger rebalance
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 20_000);
        return new KafkaConsumer<>(props);
    }

    private static void createTopics(String... topicNames) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(AdminClientConfig.CLIENT_ID_CONFIG, "client" + UUID.randomUUID());
        try (Admin admin = Admin.create(props)) {
            // use default RF to avoid NOT_ENOUGH_REPLICAS error with minISR>1
            short replicationFactor = -1;
            List<NewTopic> newTopics = Arrays.stream(topicNames)
                .map(name -> new NewTopic(name, -1, replicationFactor))
                .collect(Collectors.toList());
            try {
                admin.createTopics(newTopics).all().get();
                System.out.printf("Created topics: %s%n", Arrays.toString(topicNames));
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void handleFetchedRecords(ConsumerRecords<String, String> records) {
        if (records.count() > 0) {
            records.partitions().forEach(partition -> {
                List<ConsumerRecord<String, String>> partitionRecords = records.records(partition);
                Task task = new Task(partitionRecords);
                executor.submit(task);
                activeTasks.put(partition, task);
            });
            kafkaConsumer.pause(records.partitions());
        }
    }

    private void checkActiveTasks() {
        List<TopicPartition> finishedTasksPartitions = new ArrayList<>();
        activeTasks.forEach((partition, task) -> {
            if (task.isFinished()) {
                finishedTasksPartitions.add(partition);
            }
            long offset = task.getCurrentOffset();
            if (offset > 0) {
                offsetsToCommit.put(partition, new OffsetAndMetadata(offset));
            }
        });
        finishedTasksPartitions.forEach(partition -> activeTasks.remove(partition));
        kafkaConsumer.resume(finishedTasksPartitions);
    }

    private void commitOffsets() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastCommitTimeMs > COMMIT_INTERVAL_MS) {
            System.out.println("Committing offsets");
            if (!offsetsToCommit.isEmpty()) {
                kafkaConsumer.commitAsync(offsetsToCommit, this);
                offsetsToCommit.clear();
            }
            lastCommitTimeMs = currentTimeMillis;
        }
    }

    @Override
    public void onComplete(Map<TopicPartition, OffsetAndMetadata> map, Exception e) {
        if (e != null) {
            System.err.println("Failed to commit offsets");
            e.printStackTrace();
        }
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        // noop
    }

    // this is called from poll, which means main thread in this case
    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        // stop all tasks handling records from revoked partitions
        Map<TopicPartition, Task> stoppedTasks = new HashMap<>();
        for (TopicPartition partition : partitions) {
            Task task = activeTasks.remove(partition);
            if (task != null) {
                task.stop();
                stoppedTasks.put(partition, task);
            }
        }

        // wait for stopped tasks to complete processing of current record
        stoppedTasks.forEach((partition, task) -> {
            long offset = task.waitForCompletion();
            if (offset > 0) {
                offsetsToCommit.put(partition, new OffsetAndMetadata(offset));
            }
        });

        // collect offsets for revoked partitions
        Map<TopicPartition, OffsetAndMetadata> revokedPartitionOffsets = new HashMap<>();
        partitions.forEach(partition -> {
            OffsetAndMetadata offset = offsetsToCommit.remove(partition);
            if (offset != null)
                revokedPartitionOffsets.put(partition, offset);
        });

        // commit offsets for revoked partitions
        try {
            kafkaConsumer.commitSync(revokedPartitionOffsets);
        } catch (Exception e) {
            System.err.println("Failed to commit offsets for revoked partitions");
        }
    }
    
    @Override
    public void onPartitionsLost(Collection<TopicPartition> partitions) {
        // noop
    }

    class Task implements Runnable {
        private final List<ConsumerRecord<String, String>> records;
        private AtomicBoolean stopped = new AtomicBoolean(false);
        private AtomicBoolean started = new AtomicBoolean(false);
        private final CompletableFuture<Long> completion = new CompletableFuture<>();
        private AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicLong currentOffset = new AtomicLong(-1);

        public Task(List<ConsumerRecord<String, String>> records) {
            this.records = records;
        }

        @Override
        public void run() {
            // we need sync because the task might be stopped 
            // even before the thread pool starts to process it
            if (stopped.get()){
                return;
            }
            started.set(true);

            for (ConsumerRecord<String, String> record : records) {
                if (stopped.get()) {
                    break;
                }
                processRecord(record);
                currentOffset.set(record.offset() + 1);
            }
            finished.set(true);
            completion.complete(currentOffset.get());
        }

        public void processRecord(ConsumerRecord<String, String> record) {
            try {
                System.out.printf("START processing %s from %s-%s%n", record.value(), record.topic(), record.partition());
                TimeUnit.MILLISECONDS.sleep(60_000);
                System.out.printf("STOP processing %s from %s-%s%n", record.value(), record.topic(), record.partition());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public long getCurrentOffset() {
            return currentOffset.get();
        }

        public void stop() {
            stopped.set(true);
            if (!started.get()) {
                // task is still in the executor's queue
                // we immediately mark it as finished
                finished.set(true);
                completion.complete(-1L);
            }
        }
        
        public long waitForCompletion() {
            try {
                return completion.get();
            } catch (InterruptedException | ExecutionException e) {
                return -1L;
            }
        }

        public boolean isFinished() {
            return finished.get();
        }
    }
}

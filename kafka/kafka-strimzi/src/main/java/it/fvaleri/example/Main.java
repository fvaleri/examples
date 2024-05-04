package it.fvaleri.example;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static it.fvaleri.example.Utils.clusterCleanup;
import static it.fvaleri.example.Utils.createCluster;
import static it.fvaleri.example.Utils.createKafkaTopic;
import static it.fvaleri.example.Utils.createNamespace;
import static it.fvaleri.example.Utils.deleteKafkaTopic;
import static it.fvaleri.example.Utils.deployClusterOperator;
import static it.fvaleri.example.Utils.restartEntityOperator;
import static it.fvaleri.example.Utils.sleepFor;
import static it.fvaleri.example.Utils.stopExecutor;
import static it.fvaleri.example.Utils.updateKafkaTopic;
import static java.time.Duration.ofSeconds;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final String OPERATOR_NAMESPACE = "operators";
    private static final String TEST_NAMESPACE = "test";
    private static final String CLUSTER_NAME = "my-cluster";

    public static void main(String[] args) {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            LOG.info("Preparing the environment");
            createNamespace(client, OPERATOR_NAMESPACE);
            deployClusterOperator(client, OPERATOR_NAMESPACE);
            createNamespace(client, TEST_NAMESPACE);
            createCluster(client, TEST_NAMESPACE, CLUSTER_NAME);
            
            LOG.info("Running Topic Operator tests");
            runTopicOperatorBulkTests(client, 50, 20); // 50, 100, 150, ..., 1000

            LOG.info("Cleaning up the environment");
            clusterCleanup(client, OPERATOR_NAMESPACE, TEST_NAMESPACE);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    
    private static void runTopicOperatorBulkTests(KubernetesClient client, int seed, long limit) {
        IntStream.iterate(seed, n -> n + seed).limit(limit).forEach(numEvents -> {
            try {
                int numTasks = numEvents / 2;
                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                CompletableFuture<Void>[] futures = new CompletableFuture[numTasks];
                AtomicInteger counter = new AtomicInteger(0);

                LOG.info("Generating {} topic events", numEvents);
                long t = System.nanoTime();
                // mixed workload with create and update events in parallel
                for (int i = 0; i < numTasks; i++) {
                    String topicName = "topic-" + i;
                    futures[i] = CompletableFuture.runAsync(() -> {
                        try {
                            createKafkaTopic(client, TEST_NAMESPACE, CLUSTER_NAME, topicName);
                            counter.incrementAndGet();
                            updateKafkaTopic(client, TEST_NAMESPACE, topicName);
                            counter.incrementAndGet();
                        } catch (Throwable e) {
                            LOG.error("Error while handling topic {}: {}", topicName, e.getMessage());
                        }
                    }, executor);
                }
                CompletableFuture.allOf(futures).get();
                String durationSec = new DecimalFormat("#.#").format((System.nanoTime() - t) / 1e9);
                LOG.info("Processed {} topic events in {} seconds", counter.get(), durationSec);

                LOG.info("Cleaning up test resources");
                for (int i = 0; i < numTasks; i++) {
                    String topicName = "topic-" + i;
                    futures[i] = CompletableFuture.runAsync(() -> {
                        try {
                            deleteKafkaTopic(client, TEST_NAMESPACE, topicName);
                        } catch (Throwable e) {
                            LOG.error("Error while deleting topic {}: {}", topicName, e.getMessage());
                        }
                    }, executor);
                }
                CompletableFuture.allOf(futures).get();
                // wait some time for topic deletions to be executed in Kafka
                sleepFor(ofSeconds(30).toMillis());
                // reset the topic operator after each batch of events
                restartEntityOperator(client, TEST_NAMESPACE, CLUSTER_NAME);
                stopExecutor(executor, ofSeconds(5).toMillis());
                sleepFor(ofSeconds(30).toMillis());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}

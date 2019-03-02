package it.fvaleri.example;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static it.fvaleri.example.Kubernetes.clusterCleanup;
import static it.fvaleri.example.Kubernetes.createCluster;
import static it.fvaleri.example.Kubernetes.createKafkaTopicNoWait;
import static it.fvaleri.example.Kubernetes.createNamespace;
import static it.fvaleri.example.Kubernetes.deleteKafkaTopic;
import static it.fvaleri.example.Kubernetes.deployClusterOperator;
import static it.fvaleri.example.Kubernetes.restartEntityOperator;
import static it.fvaleri.example.Kubernetes.updateKafkaTopicNoWait;
import static it.fvaleri.example.Utils.sleep;
import static it.fvaleri.example.Utils.stopExecutor;
import static java.time.Duration.ofSeconds;

public class TopicOperator {
    private static final Logger LOG = LoggerFactory.getLogger(TopicOperator.class);
    private static final boolean UTO_ENABLED = true;

    public static void main(String[] args) {
        String operatorNamespace = "operators";
        String testNamespace = "test";
        String clusterName = "my-cluster";
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            LOG.info("Preparing the environment");
            createNamespace(client, operatorNamespace);
            if (UTO_ENABLED) {
                deployClusterOperator(client, operatorNamespace, "+UnidirectionalTopicOperator");
            } else {
                deployClusterOperator(client, operatorNamespace);
            }
            createNamespace(client, testNamespace);
            createCluster(client, testNamespace, clusterName);

            IntStream.iterate(50, n -> n + 50).limit(20).forEach(n -> {
                try {
                    runBulkWorkload(client, testNamespace, clusterName, n);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            clusterCleanup(client, operatorNamespace, testNamespace);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    
    private static void runBulkWorkload(KubernetesClient client,
                                        String namespace,
                                        String clusterName,
                                        int numEvents) throws InterruptedException, ExecutionException {
        int tasks = numEvents / 2;
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CompletableFuture<Void>[] futures = new CompletableFuture[tasks];
        AtomicInteger counter = new AtomicInteger(0);

        LOG.info("Generating {} topic events", numEvents);
        long createStart = System.nanoTime();
        for (int i = 0; i < tasks; i++) {
            String topicName = "topic-" + i;
            // create KafkaTopics async, then wait for them all to be Ready, and similarly with update
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    createKafkaTopicNoWait(client, namespace, clusterName, topicName);
                    counter.incrementAndGet();
                    updateKafkaTopicNoWait(client, namespace, topicName);
                    counter.incrementAndGet();
                } catch (Throwable e) {
                    LOG.error("Error while creating topic {}: {}", topicName, e.getMessage());
                }
            }, executor);
        }
        CompletableFuture.allOf(futures).get();
        String durationSec = new DecimalFormat("#.#").format((System.nanoTime() - createStart) / 1e9);
        LOG.info("Processed {} topic events in {} seconds", counter.get(), durationSec);

        testCleanup(client, namespace, clusterName, tasks, futures, executor);
    }

    private static void testCleanup(KubernetesClient client,
                                    String namespace,
                                    String clusterName,
                                    int tasks,
                                    CompletableFuture<Void>[] futures,
                                    ExecutorService executor) throws InterruptedException, ExecutionException {
        LOG.info("Cleaning up test resources");
        for (int i = 0; i < tasks; i++) {
            String topicName = "topic-" + i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    deleteKafkaTopic(client, namespace, topicName);
                } catch (Throwable e) {
                    LOG.error("Error while deleting topic {}: {}", topicName, e.getMessage());
                }
            }, executor);
        }
        CompletableFuture.allOf(futures).get();
        // wait some time for topic deletions to be executed in Kafka
        sleep(ofSeconds(30).toMillis());
        // the BTO fails with invalid state store error when doing bulk topic deletion
        restartEntityOperator(client, namespace, clusterName);
        stopExecutor(executor, ofSeconds(5).toMillis());
        sleep(ofSeconds(30).toMillis());
    }
}

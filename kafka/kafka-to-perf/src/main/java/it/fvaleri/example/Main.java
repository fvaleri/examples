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

import static it.fvaleri.example.Utils.createCluster;
import static it.fvaleri.example.Utils.createKafkaTopic;
import static it.fvaleri.example.Utils.createNamespace;
import static it.fvaleri.example.Utils.deleteAllResources;
import static it.fvaleri.example.Utils.deleteKafkaTopic;
import static it.fvaleri.example.Utils.deployClusterOperator;
import static it.fvaleri.example.Utils.sleepFor;
import static it.fvaleri.example.Utils.stopExecutor;
import static it.fvaleri.example.Utils.updateKafkaTopic;
import static java.time.Duration.ofSeconds;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            LOG.info("Starting up");
            createNamespace(client, "strimzi");
            deployClusterOperator(client, "strimzi");
            createNamespace(client, "test");
            createCluster(client, "test", "my-cluster");
            runScalabilityTests(client, 50, 20);
            LOG.info("Shutting down");
            deleteAllResources(client, "strimzi", "test");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Scalability test with increasing number of events (batch).
     *
     * The batch size is computed by increasing by "seed" value for "limit" iterations. 
     * Example: seed=50, limit=20 ==> 50, 100, 150, ..., 1000.
     * 
     * The output is the end-to-end reconciliation time in seconds, which can be used 
     * to create a time series graph and compare the performance between changes.
     * 
     * @param client Kubernetes client.
     * @param seed Starting and increment size.
     * @param limit Total number of batches.
     */
    private static void runScalabilityTests(KubernetesClient client, int seed, long limit) {
        int numProcs = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numProcs);
        
        LOG.info("Running warm-up phase");
        for (int i = 0; i < 100; i++) {
            String topicName = "topic-" + i;
            runScalabilityTask(client, topicName, new AtomicInteger(0));
        }

        LOG.info("Running steady-state phase");
        IntStream.iterate(seed, n -> n + seed).limit(limit).forEach(batchSize -> {
            try {
                int eventsPerTask = 3;
                int numTasks = batchSize / eventsPerTask;
                int spareEvents = batchSize - numTasks * 3;
                
                CompletableFuture<Void>[] futures = new CompletableFuture[numTasks];
                AtomicInteger counter = new AtomicInteger(0);
                long t = System.nanoTime();

                LOG.info("Running {} tasks in parallel with {} executors", numTasks, numProcs);
                for (int i = 0; i < numTasks; i++) {
                    String topicName = "topic-" + i;
                    futures[i] = CompletableFuture.runAsync(() -> runScalabilityTask(client, topicName, counter), executor);
                }

                LOG.info("Consuming {} spare events", spareEvents);
                for (int j = 0; j < spareEvents; j++) {
                    futures[j] = CompletableFuture.completedFuture(null);
                    counter.incrementAndGet();
                }
               
                CompletableFuture.allOf(futures).get();
                String durationSec = new DecimalFormat("#.#").format((System.nanoTime() - t) / 1e9);
                LOG.info("Reconciled {} topic events in {} seconds", counter.get(), durationSec);

                LOG.info("Running cool-down phase");
                sleepFor(ofSeconds(10).toMillis());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        stopExecutor(executor, ofSeconds(5).toMillis());
    }
    
    private static void runScalabilityTask(KubernetesClient client, String topicName, AtomicInteger counter) {
        try {
            createKafkaTopic(client, "test", "my-cluster", topicName);
            counter.incrementAndGet();
            
            updateKafkaTopic(client, "test", topicName);
            counter.incrementAndGet();
            
            deleteKafkaTopic(client, "test", topicName);
            counter.incrementAndGet();
        } catch (Throwable e) {
            LOG.error("Error with topic {}: {}", topicName, e.getMessage());
        }
    }
}

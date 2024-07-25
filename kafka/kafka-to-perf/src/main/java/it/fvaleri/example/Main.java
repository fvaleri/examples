package it.fvaleri.example;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

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
import static it.fvaleri.example.Utils.stopExecutor;
import static it.fvaleri.example.Utils.updateKafkaTopic;
import static java.time.Duration.ofSeconds;

public class Main {
    public static void main(String[] args) {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            createNamespace(client, "strimzi");
            deployClusterOperator(client, "strimzi");
            createNamespace(client, "test");
            createCluster(client, "test", "my-cluster");
            runScalabilityTests(client, 50, 20);
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
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        System.out.println("Warming up");
        for (int i = 0; i < 100; i++) {
            String topicName = "topic-" + i;
            runTask(client, topicName, new AtomicInteger(0));
        }

        System.out.println("Running workloads");
        IntStream.iterate(seed, n -> n + seed).limit(limit).forEach(batchSize -> {
            try {
                int eventsPerTask = 3;
                int numTasks = batchSize / eventsPerTask;
                int numSpareEvents = batchSize - numTasks * 3;
                
                CompletableFuture<Void>[] futures = new CompletableFuture[numTasks];
                AtomicInteger counter = new AtomicInteger(0);
                long t = System.nanoTime();

                System.out.printf("Running %d tasks%n", numTasks);
                for (int i = 0; i < numTasks; i++) {
                    String topicName = "topic-" + i;
                    futures[i] = CompletableFuture.runAsync(() -> runTask(client, topicName, counter), executor);
                }

                System.out.printf("Consuming %d spare events%n", numSpareEvents);
                for (int j = 0; j < numSpareEvents; j++) {
                    futures[j] = CompletableFuture.completedFuture(null);
                    counter.incrementAndGet();
                }
               
                CompletableFuture.allOf(futures).get();
                long durationMs = (System.nanoTime() - t) / 1_000_000;
                System.out.printf("Reconciled %d topic events in %d ms%n", counter.get(), durationMs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        stopExecutor(executor, ofSeconds(5).toMillis());
    }
    
    private static void runTask(KubernetesClient client, String topicName, AtomicInteger counter) {
        try {
            createKafkaTopic(client, "test", "my-cluster", topicName, false);
            counter.incrementAndGet();
            
            updateKafkaTopic(client, "test", topicName, false);
            counter.incrementAndGet();
            
            deleteKafkaTopic(client, "test", topicName, false);
            counter.incrementAndGet();
        } catch (Throwable e) {
            System.err.printf("Error with topic %s: %s%n", topicName, e.getMessage());
        }
    }
}

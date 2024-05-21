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

import static it.fvaleri.example.Utils.cleanKubernetes;
import static it.fvaleri.example.Utils.createCluster;
import static it.fvaleri.example.Utils.createKafkaTopic;
import static it.fvaleri.example.Utils.createNamespace;
import static it.fvaleri.example.Utils.deleteKafkaTopic;
import static it.fvaleri.example.Utils.deployClusterOperator;
import static it.fvaleri.example.Utils.sleepFor;
import static it.fvaleri.example.Utils.stopExecutor;
import static it.fvaleri.example.Utils.updateKafkaTopic;
import static java.time.Duration.ofSeconds;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final String OPERATOR_NAMESPACE = "strimzi";
    private static final String TEST_NAMESPACE = "test";
    private static final String CLUSTER_NAME = "my-cluster";

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            LOG.info("Preparing the environment");
            cleanKubernetes(client, OPERATOR_NAMESPACE, TEST_NAMESPACE);
            createNamespace(client, OPERATOR_NAMESPACE);
            deployClusterOperator(client, OPERATOR_NAMESPACE);
            createNamespace(client, TEST_NAMESPACE);
            createCluster(client, TEST_NAMESPACE, CLUSTER_NAME);

            LOG.info("Running Topic Operator perf tests");
            runTests(client, 50, 20); // 50, 100, 150, ..., 1000

            LOG.info("Cleaning up the environment");
            cleanKubernetes(client, OPERATOR_NAMESPACE, TEST_NAMESPACE);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    
    private void runTests(KubernetesClient client, int seed, long limit) {
        // run warmup test
        LOG.info("Warming up");
        for (int i = 0; i < 100; i++) {
            String topicName = "topic-" + i;
            runTask(client, topicName, new AtomicInteger(0));
        }
        
        // generates and runs tests with increasing topic event batch size
        // for each batch , it prints out the end-to-end reconciliation time
        IntStream.iterate(seed, n -> n + seed).limit(limit).forEach(numEvents -> {
            try {
                int eventsPerTask = 3;
                int numTasks = numEvents / eventsPerTask;
                int spareEvents = numEvents - numTasks * 3;

                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                CompletableFuture<Void>[] futures = new CompletableFuture[numTasks];
                AtomicInteger counter = new AtomicInteger(0);

                LOG.info("Generating {} topic events", numEvents);
                long t = System.nanoTime();
                
                // run all tasks in parallel
                for (int i = 0; i < numTasks; i++) {
                    String topicName = "topic-" + i;
                    futures[i] = CompletableFuture.runAsync(() -> runTask(client, topicName, counter), executor);
                }
                
                // consume spare events
                for (int j = 0; j < spareEvents; j++) {
                    futures[j] = CompletableFuture.completedFuture(null);
                    counter.incrementAndGet();
                }
               
                CompletableFuture.allOf(futures).get();
                String durationSec = new DecimalFormat("#.#").format((System.nanoTime() - t) / 1e9);
                LOG.info("Processed {} topic events in {} seconds", counter.get(), durationSec);

                LOG.info("Cooling down");
                sleepFor(ofSeconds(30).toMillis());
                //restartEntityOperator(client, TEST_NAMESPACE, CLUSTER_NAME);
                stopExecutor(executor, ofSeconds(5).toMillis());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // run single task which creates, updates and delete a topic
    private void runTask(KubernetesClient client, String topicName, AtomicInteger counter) {
        try {
            createKafkaTopic(client, TEST_NAMESPACE, CLUSTER_NAME, topicName);
            counter.incrementAndGet();

            // TODO send in some data
            
            updateKafkaTopic(client, TEST_NAMESPACE, topicName);
            counter.incrementAndGet();
            
            deleteKafkaTopic(client, TEST_NAMESPACE, topicName);
            counter.incrementAndGet();
        } catch (Throwable e) {
            LOG.error("Error with topic {}: {}", topicName, e.getMessage());
        }
    }
}

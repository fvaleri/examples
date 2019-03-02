package it.fvaleri.example;

import java.util.concurrent.ExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class Utils {
    private Utils() {
    }

    public static void sleep(long millis) {
        try {
            MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    // executor services create non-daemon threads by default, which prevent JVM shutdown
    public static void stopExecutor(ExecutorService executor, long timeoutMs) {
        if (executor == null || timeoutMs < 0) {
            return;
        }
        try {
            executor.shutdown();
            executor.awaitTermination(timeoutMs, MILLISECONDS);
        } catch (InterruptedException e) {
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
        }
    }
}

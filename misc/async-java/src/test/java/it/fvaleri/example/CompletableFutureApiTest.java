package it.fvaleri.example;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class CompletableFutureApiTest {
    static ExecutorService executor;
    static Random random;

    @BeforeAll
    static void beforeAll() {
        random = new Random();
        executor = Executors.newFixedThreadPool(3, new ThreadFactory() {
            int count = 1;

            @Override
            public Thread newThread(Runnable runnable) {
                return new Thread(runnable, "custom-executor-" + count++);
            }
        });
    }

    @AfterAll
    static void afterAll() {
        executor.shutdown();
    }

    @Test
    static void completedFuture() {
        // can be used as starting stage in your computation
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message");
        assertTrue(cf.isDone());
        assertEquals("message", cf.getNow(null));
    }

    @Test
    void runAsync() {
        // when no executor is specified, the asynchronous execution uses the common
        // ForkJoinPool, which uses n.cores daemon threads (JVM doesn't wait on termination)
        CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> {
            assertTrue(Thread.currentThread().isDaemon());
            randomSleep();
        });
        assertFalse(cf.isDone());
        sleepEnough();
        assertTrue(cf.isDone());
    }

    @Test
    void thenApply() {
        // synchronous blocking pipeline of stages (computations)
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApply(s -> {
            assertFalse(Thread.currentThread().isDaemon());
            return s.toUpperCase();
        });
        assertEquals("MESSAGE", cf.getNow(null));
    }

    @Test
    void thenApplyAsync() {
        // asynchronous non-blocking pipeline of stages (computations)
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(s -> {
            assertTrue(Thread.currentThread().isDaemon());
            randomSleep();
            return s.toUpperCase();
        });
        assertNull(cf.getNow(null));
        assertEquals("MESSAGE", cf.join());
    }

    @Test
    void thenApplyAsyncWithExecutor() {
        // providing a custom executor (with IO intensive computations, set n.threads > n.cores)
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(s -> {
            assertTrue(Thread.currentThread().getName().startsWith("custom-executor-"));
            assertFalse(Thread.currentThread().isDaemon());
            randomSleep();
            return s.toUpperCase();
        }, executor);

        assertNull(cf.getNow(null));
        assertEquals("MESSAGE", cf.join());
    }

    @Test
    void thenAccept() {
        StringBuilder result = new StringBuilder();
        // we use a consumer when there is no need to return a value
        CompletableFuture.completedFuture("thenAccept message")
            .thenAccept(s -> result.append(s));
        assertTrue(result.length() > 0, "Result was empty");
    }

    @Test
    void thenAcceptAsync() {
        StringBuilder result = new StringBuilder();
        CompletableFuture<Void> cf = CompletableFuture.completedFuture("thenAcceptAsync message")
            .thenAcceptAsync(s -> result.append(s));
        cf.join();
        assertTrue(result.length() > 0, "Result was empty");
    }

    @Test
    void completeExceptionally() {
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(
            String::toUpperCase, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
        CompletableFuture<String> exceptionHandler = cf.handle((s, th) -> (th != null) ? "message upon cancel" : "");

        // forcing a failure to test the exception handler
        cf.completeExceptionally(new RuntimeException("completed exceptionally"));
        assertTrue(cf.isCompletedExceptionally(), "Was not completed exceptionally");
        try {
            cf.join();
            fail("Should have thrown an exception");
        } catch (CompletionException ex) {
            assertEquals("completed exceptionally", ex.getCause().getMessage());
        }

        assertEquals("message upon cancel", exceptionHandler.join());
    }

    @Test
    void cancel() {
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(
            String::toUpperCase, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
        CompletableFuture<String> cf2 = cf.exceptionally(throwable -> "canceled message");
        // cancel is equivalent to completeExceptionally(new CancellationException())
        assertTrue(cf.cancel(true), "Was not canceled");
        assertTrue(cf.isCompletedExceptionally(), "Was not completed exceptionally");
        assertEquals("canceled message", cf2.join());
    }

    @Test
    void applyToEither() {
        String original = "Message";
        CompletableFuture<String> cf1 = CompletableFuture.completedFuture(original)
            .thenApplyAsync(s -> delayedUpperCase(s));
        // no guarantees on which one will be passed to the function
        CompletableFuture<String> cf2 = cf1.applyToEither(
            CompletableFuture.completedFuture(original).thenApplyAsync(s -> delayedLowerCase(s)),
            s -> s + "-applyToEither");
        assertTrue(cf2.join().endsWith("-applyToEither"));
    }

    @Test
    void acceptEither() {
        String original = "Message";
        StringBuilder result = new StringBuilder();
        CompletableFuture<Void> cf = CompletableFuture.completedFuture(original)
            .thenApplyAsync(s -> delayedUpperCase(s))
            .acceptEither(CompletableFuture.completedFuture(original).thenApplyAsync(s -> delayedLowerCase(s)),
                s -> result.append(s).append("-acceptEither"));
        cf.join();
        assertTrue(result.toString().endsWith("-acceptEither"), "Result was empty");
    }

    @Test
    void runAfterBoth() {
        String original = "Message";
        StringBuilder result = new StringBuilder();
        // the runnable triggers upon completion of both of two stages
        CompletableFuture.completedFuture(original).thenApply(String::toUpperCase).runAfterBoth(
            CompletableFuture.completedFuture(original).thenApply(String::toLowerCase),
            () -> result.append("done"));
        assertTrue(result.length() > 0, "Result was empty");
    }

    @Test
    void thenAcceptBoth() {
        String original = "Message";
        StringBuilder result = new StringBuilder();
        // using a bi consumer we can process the results
        CompletableFuture.completedFuture(original).thenApply(String::toUpperCase).thenAcceptBoth(
            CompletableFuture.completedFuture(original).thenApply(String::toLowerCase),
            (s1, s2) -> result.append(s1 + s2));
        assertEquals("MESSAGEmessage", result.toString());
    }

    @Test
    void thenCombine() {
        String original = "Message";
        // we can also combine them and return the results
        CompletableFuture<String> cf = CompletableFuture.completedFuture(original).thenApply(s -> delayedUpperCase(s))
            .thenCombine(CompletableFuture.completedFuture(original).thenApply(s -> delayedLowerCase(s)),
                (s1, s2) -> s1 + s2);
        assertEquals("MESSAGEmessage", cf.getNow(null));
    }

    @Test
    void thenCombineAsync() {
        String original = "Message";
        // used when both stages can work independently
        CompletableFuture<String> cf = CompletableFuture.completedFuture(original)
            .thenApplyAsync(s -> delayedUpperCase(s))
            .thenCombine(CompletableFuture.completedFuture(original).thenApplyAsync(s -> delayedLowerCase(s)),
                (s1, s2) -> s1 + s2);
        assertEquals("MESSAGEmessage", cf.join());
    }

    @Test
    void thenCompose() {
        String original = "Message";
        // used when one stage is waiting for another stage to provide its result
        CompletableFuture<String> cf = CompletableFuture.completedFuture(original).thenApply(s -> delayedUpperCase(s))
            .thenCompose(upper -> CompletableFuture.completedFuture(original).thenApply(s -> delayedLowerCase(s))
                .thenApply(s -> upper + s));
        assertEquals("MESSAGEmessage", cf.join());
    }

    @Test
    void anyOf() {
        StringBuilder result = new StringBuilder();
        List<String> messages = Arrays.asList("a", "b", "c");
        List<CompletableFuture<String>> futures = messages.stream()
            .map(msg -> CompletableFuture.completedFuture(msg).thenApply(s -> delayedUpperCase(s)))
            .collect(Collectors.toList());
        // completes when any of several stages completes
        CompletableFuture.anyOf(futures.toArray(new CompletableFuture[futures.size()])).whenComplete((res, th) -> {
            if(th == null) {
                assertTrue(isUpperCase((String) res));
                result.append(res);
            }
        });
        assertTrue(result.length() > 0, "Result was empty");
    }

    @Test
    void allOf() {
        StringBuilder result = new StringBuilder();
        List<String> messages = Arrays.asList("a", "b", "c");
        List<CompletableFuture<String>> futures = messages.stream()
            .map(msg -> CompletableFuture.completedFuture(msg).thenApply(s -> delayedUpperCase(s)))
            .collect(Collectors.toList());
        // completes when all of several stages complete (sync)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).whenComplete((v, th) -> {
            futures.forEach(cf -> assertTrue(isUpperCase(cf.getNow(null))));
            result.append("done");
        });
        assertTrue(result.length() > 0, "Result was empty");
    }

    @Test
    void allOfAsync() {
        StringBuilder result = new StringBuilder();
        List<String> messages = Arrays.asList("a", "b", "c");
        List<CompletableFuture<String>> futures = messages.stream()
            .map(msg -> CompletableFuture.completedFuture(msg).thenApplyAsync(s -> delayedUpperCase(s)))
            .collect(Collectors.toList());
        // completes when all of several stages complete (async)
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
            .whenComplete((v, th) -> {
                futures.forEach(cf -> assertTrue(isUpperCase(cf.getNow(null))));
                result.append("done");
            });
        allOf.join();
        assertTrue(result.length() > 0, "Result was empty");
    }

    @Test
    private static boolean isUpperCase(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLowerCase(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String delayedUpperCase(String s) {
        randomSleep();
        return s.toUpperCase();
    }

    private static String delayedLowerCase(String s) {
        randomSleep();
        return s.toLowerCase();
    }

    public static void randomSleep() {
        try {
            MILLISECONDS.sleep(random.nextInt(1000));
        } catch (InterruptedException e) {
        }
    }

    private static void sleepEnough() {
        try {
            MILLISECONDS.sleep(2_000);
        } catch (InterruptedException e) {
        }
    }
}

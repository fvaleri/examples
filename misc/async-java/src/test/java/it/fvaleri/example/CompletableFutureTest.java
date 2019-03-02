package it.fvaleri.example;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * With asynchronous APIs and non-blocking IO we can process the same number of tasks with less threads
 * which improves performance and scalability (less thread contention and context switch), but the code
 * is harder to debug because handlers/callbacks are executed concurrently.
 */
public class CompletableFutureTest {
    @Test
    void runFullExample() {
        cars().thenCompose(cars -> {

            List<CompletionStage<Car>> updatedCars = cars.stream()
                .map(car -> rating(car.manufacturerId).thenApply(r -> {
                    car.setRating(r);
                    return car;
                })).collect(Collectors.toList());

            CompletableFuture<Void> done = CompletableFuture
                .allOf(updatedCars.toArray(new CompletableFuture[updatedCars.size()]));

            return done.thenApply(v -> updatedCars.stream().map(CompletionStage::toCompletableFuture)
                .map(CompletableFuture::join).collect(Collectors.toList()));

        }).whenComplete((cars, th) -> {
            if (th == null) {
                cars.forEach(System.out::println);
            } else {
                throw new RuntimeException(th);
            }
        }).toCompletableFuture().join();
    }

    static CompletionStage<List<Car>> cars() {
        // could be consuming from a REST endpoint
        List<Car> carList = new ArrayList<>();
        carList.add(new Car(1, 3, "Fiesta", 2017));
        carList.add(new Car(2, 7, "Camry", 2014));
        carList.add(new Car(3, 2, "M2", 2008));
        return CompletableFuture.supplyAsync(() -> carList);
    }

    static CompletionStage<Float> rating(int manufacturer) {
        // could be consuming from a REST endpoint
        return CompletableFuture.supplyAsync(() -> {
            try {
                simulateDelay();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            switch (manufacturer) {
                case 2:
                    return 4f;
                case 3:
                    return 4.1f;
                case 7:
                    return 4.2f;
                default:
                    return 5f;
            }
        }).exceptionally(th -> -1f);
    }

    static void simulateDelay() throws InterruptedException {
        try {
            MILLISECONDS.sleep(5_000);
        } catch (InterruptedException e) {
        }
    }

    static class Car {
        int id;
        int manufacturerId;
        String model;
        int year;
        float rating;

        public Car(int id, int manufacturerId, String model, int year) {
            this.id = id;
            this.manufacturerId = manufacturerId;
            this.model = model;
            this.year = year;
        }

        void setRating(float rating) {
            this.rating = rating;
        }

        @Override
        public String toString() {
            return "Car (id=" + id
                + ", manufacturerId=" + manufacturerId
                + ", model=" + model
                + ", year=" + year
                + ", rating=" + rating;
        }
    }
}

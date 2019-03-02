package it.fvaleri.example;

public class CounterService {
    private int counter;

    public String count() throws Exception {
        counter++;
        if (counter % 3 == 0) {
            Thread.sleep(200);
        } else if (counter % 5 == 0) {
            throw new RuntimeException("Forced");
        }
        return "test" + counter;
    }
}


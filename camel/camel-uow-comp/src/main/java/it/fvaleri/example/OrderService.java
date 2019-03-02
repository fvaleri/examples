package it.fvaleri.example;

import org.apache.camel.Header;

public class OrderService {
    public String createMail(String order) throws Exception {
        return "Order confirmed: " + order;
    }

    public void sendMail(String body, @Header("to") String to) {
        if (to.equals("FATAL")) {
            throw new IllegalArgumentException("Simulated fatal error");
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
    }
}


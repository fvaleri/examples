package it.fvaleri.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            if (Configuration.CLIENT_TYPE == null) {
                LOG.error("Empty client type");
                System.exit(1);
            }
            switch (Configuration.CLIENT_TYPE) {
                case "producer":
                    new Producer("producer-thread").start();
                    break;
                case "consumer":
                    new Consumer("consumer-thread").start();
                    break;
                default:
                    LOG.error("Unknown client type");
                    System.exit(1);
            }
        } catch (Throwable e) {
            LOG.error("Unhandled error", e);
        }
    }
}

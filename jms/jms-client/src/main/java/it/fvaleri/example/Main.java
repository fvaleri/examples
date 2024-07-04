package it.fvaleri.example;

public class Main {
    public static void main(String[] args) {
        try {
            if (Configuration.CLIENT_TYPE == null) {
                System.err.println("Empty client type");
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
                    System.err.println("Unknown client type");
                    System.exit(1);
            }
        } catch (Throwable e) {
            System.err.println("Unhandled error");
            e.printStackTrace();
        }
    }
}

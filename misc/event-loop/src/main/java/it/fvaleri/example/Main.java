package it.fvaleri.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    public static void main(String[] args) {
        EventLoop eventLoop = new EventLoop();
        try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
            var eventId = 0;

            outer:
            do {
                System.out.println("What kind of task would you like to submit to the Event Loop?");
                System.out.println(" 1. Wish me Hello");
                System.out.println(" 2. Print the contents of a file named hello.txt");
                System.out.println(" 3. Retrieve the latest news from New York Times & print it");
                System.out.println(" 4. Print output of previously submitted asynchronous task");
                System.out.println(" 5. Exit!");
                System.out.print(" > ");

                var usersChoice = reader.readLine();
                var operationType = "1";

                if (Utils.userHasNotChosenToExit(usersChoice) && !usersChoice.equals("4")) {
                    System.out.println("How would like to execute this operation?");
                    System.out.println(" 1. Synchronously (this would block the Event Loop until the operation completes)");
                    System.out.println(" 2. Asynchronously (this won't block Event Loop in any way)");
                    System.out.print(" > ");
                    operationType = reader.readLine();
                }

                var uniqueEventKey = "";
                switch (usersChoice) {
                    case "1":
                        uniqueEventKey = Utils.generateUniqueEventKey("hello", eventId++);
                        eventLoop
                                .on(uniqueEventKey, data -> String.format("Hello! %s", data))
                                .dispatch(new Event(uniqueEventKey, "How are you doing today?",
                                    Utils.isAsynchronous(operationType)));
                        break;
                    case "2":
                        uniqueEventKey = Utils.generateUniqueEventKey("read-file", eventId++);
                        eventLoop
                                .on(uniqueEventKey, Utils::readFile)
                                .dispatch(
                                        new Event(uniqueEventKey,
                                                "hello.txt",
                                                Utils.isAsynchronous(operationType)));
                        break;
                    case "3":
                        uniqueEventKey = Utils.generateUniqueEventKey("fetch-latest-news", eventId++);
                        eventLoop
                                .on(uniqueEventKey, Utils::fetchLatestNewsItemFromNewYorkTimes)
                                .dispatch(new Event(uniqueEventKey,
                                        System.getenv("API_KEY"),
                                        Utils.isAsynchronous(operationType)));
                        break;
                    case "4": break;
                    case "5":
                    default:
                        break outer;
                }

                eventLoop.run();

            } while (true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

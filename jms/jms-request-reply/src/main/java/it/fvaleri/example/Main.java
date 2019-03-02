package it.fvaleri.example;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

public class Main {
    private static final String CONNECTION_URL = "tcp://localhost:61616?jms.watchTopicAdvisories=false";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String QUEUE_NAME = "my-queue";

    private static final Queue<Throwable> ERRORS = new ConcurrentLinkedQueue<>();
    private static final CountDownLatch READY = new CountDownLatch(1);

    public static void main(String[] args) {
        try {
            final ExecutorService executor = Executors.newFixedThreadPool(2);
            executor.execute(new ServiceA());
            executor.execute(new ServiceB());
            stopExecutor(executor, 60_000);
            System.out.printf("Errors: %d%n", ERRORS);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // executor services create non-daemon threads by default, which prevent JVM shutdown
    private static void stopExecutor(ExecutorService executor, long timeoutMs) {
        if (executor == null || timeoutMs < 0) {
            return;
        }
        try {
            executor.shutdown();
            executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
        }
    }

    static class ServiceA implements Runnable {
        @Override
        public void run() {
            Connection connection = null;
            try {
                String className = this.getClass().getSimpleName();
                ConnectionFactory factory = new ActiveMQConnectionFactory(CONNECTION_URL);
                connection = factory.createConnection(USERNAME, PASSWORD);
                connection.setExceptionListener(e -> System.err.printf("[%s] %s%n", className, e));
                System.out.printf("[%s] Connected%n", className);
                connection.start();

                // do not use transacted session to avoid hang
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination requestQueue = session.createQueue(QUEUE_NAME);
                Destination tempQueue = session.createTemporaryQueue();

                MessageProducer producer = session.createProducer(requestQueue);
                MessageConsumer consumer = session.createConsumer(tempQueue);

                TextMessage request = session.createTextMessage("ping");
                request.setJMSReplyTo(tempQueue);
                request.setJMSCorrelationID("id" + System.nanoTime());

                READY.await(20, TimeUnit.SECONDS);
                System.out.printf("[%s] Sending request: %s%n", className, request.getText());
                producer.send(request);

                System.out.printf("[%s] Waiting for reply%n", className);
                TextMessage reply = (TextMessage) consumer.receive();
                System.out.printf("[%s] Reply received: %s%n", className, reply.getText());
            } catch (Throwable e) {
                ERRORS.add(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.close();
                    }
                } catch (JMSException e) {
                }
            }
        }
    }

    static class ServiceB implements Runnable {
        @Override
        public void run() {
            Connection connection = null;
            try {
                String className = this.getClass().getSimpleName();
                ConnectionFactory cf = new org.apache.activemq.ActiveMQConnectionFactory(CONNECTION_URL);
                connection = cf.createConnection(USERNAME, PASSWORD);
                connection.setExceptionListener(e -> System.err.printf("[%s] %s%n", className, e));
                System.out.printf("[%s] Connected", className);
                connection.start();

                // do not use transacted session to avoid hang
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination requestQueue = session.createQueue(QUEUE_NAME);

                MessageConsumer consumer = session.createConsumer(requestQueue);
                MessageProducer producer = session.createProducer(null);

                System.out.printf("[%s] Waiting for request%n", className);
                READY.countDown();
                TextMessage request = (TextMessage) consumer.receive();
                System.out.printf("[%s] Request received: %s%n", className, request.getText());

                TextMessage reply = session.createTextMessage("pong");
                reply.setJMSCorrelationID(request.getJMSMessageID());
                System.out.printf("[%s] Sending reply: %s%n", className, reply.getText());
                producer.send(request.getJMSReplyTo(), reply);
            } catch (Throwable e) {
                ERRORS.add(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.close();
                    }
                } catch (JMSException e) {
                }
            }
        }
    }
}

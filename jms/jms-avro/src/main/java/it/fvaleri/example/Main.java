package it.fvaleri.example;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.qpid.jms.JmsConnectionFactory;

public class Main {
    private static final String CONNECTION_URL = "amqp://localhost:5672";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String QUEUE_NAME = "my-queue";

    private final static String SCHEMA_FILE = "src/main/resources/person.avsc";
    private static BinaryEncoder encoder = null;
    private static BinaryDecoder decoder = null;
    private static CountDownLatch latch;

    public static void main(String[] args) {
        try {
            final ExecutorService executor = Executors.newFixedThreadPool(2);
            executor.execute(new Producer());
            executor.execute(new Consumer());
            stopExecutor(executor, 60_000);
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

    static class Producer implements Runnable {
        @Override
        public void run() {
            Connection connection = null;
            try {
                String className = this.getClass().getSimpleName();
                ConnectionFactory factory = new JmsConnectionFactory(CONNECTION_URL);
                connection = factory.createConnection(USERNAME, PASSWORD);
                connection.setExceptionListener(e -> System.err.printf("[%s] %s%n", className, e));
                System.out.printf("[%s] Connected%n", className);

                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination queue = session.createQueue(QUEUE_NAME);
                MessageProducer producer = session.createProducer(queue);

                Person person = new Person("Federico", "Via del Corso 13");
                byte[] bytes = encodeMessage(person);
                BytesMessage message = session.createBytesMessage();
                message.writeBytes(bytes);

                producer.send(message);
                System.out.printf("[%s] Produced message: %s%n", className, person);
            } catch (Throwable e) {
                System.out.printf("%s%n", e);
            } finally {
                try {
                    if (connection != null) {
                        connection.close();
                    }
                } catch (JMSException e) {
                }
            }
        }

        private byte[] encodeMessage(Person person) throws IOException {
            Schema schema = new Schema.Parser().parse(new File(SCHEMA_FILE));
            // the writer works most efficiently when it's fed a stream of objects to convert
            // so we could place multiple objects in the same JMS message
            SpecificDatumWriter<Person> personDatumWriter = new SpecificDatumWriter<>(schema);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // passing the existing encoder to avoid creating a new one for every message
            encoder = EncoderFactory.get().binaryEncoder(out, encoder);
            personDatumWriter.write(person, encoder);
            encoder.flush();
            byte[] bytes = out.toByteArray();
            out.close();
            return bytes;
        }
    }

    static class Consumer implements Runnable, MessageListener {
        @Override
        public void run() {
            Connection connection = null;
            try {
                String className = this.getClass().getSimpleName();
                ConnectionFactory cf = new JmsConnectionFactory(CONNECTION_URL);
                connection = cf.createConnection(USERNAME, PASSWORD);
                connection.setExceptionListener(e -> System.err.printf("[%s] %s%n", className, e));
                System.out.printf("[%s] Connected%n", className);
                connection.start();

                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination queue = session.createQueue(QUEUE_NAME);
                MessageConsumer consumer = session.createConsumer(queue);
                consumer.setMessageListener(this);

                // wait indefinitely for new messages
                connection.start();
                latch = new CountDownLatch(1);
                latch.await();
            } catch (Throwable e) {
                System.out.printf("%s%n", e);
            } finally {
                try {
                    if (connection != null) {
                        connection.close();
                    }
                } catch (JMSException e) {
                }
            }
        }

        @Override
        public void onMessage(Message message) {
            try {
                String className = this.getClass().getSimpleName();
                BytesMessage byteMessage = (BytesMessage) message;
                byte[] bytes = new byte[(int) byteMessage.getBodyLength()];
                byteMessage.readBytes(bytes);
                Person person = decodeMessage(bytes);
                System.out.printf("[%s] Consumed message: %s%n", className, person);
                latch.countDown();
            } catch (Throwable e) {
                System.out.printf("%s%n", e);
            }
        }

        private Person decodeMessage(byte[] message) throws IOException {
            Schema schema = new Schema.Parser().parse(new File(SCHEMA_FILE));
            DatumReader<Person> personDatumReader = new SpecificDatumReader<>(schema);
            // passing the existing decoder to avoid creating a new one
            decoder = DecoderFactory.get().binaryDecoder(message, decoder);
            Person person = personDatumReader.read(null, decoder);
            return person;
        }
    }
}

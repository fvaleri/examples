package it.fvaleri.example;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

public class Main {
    private static final long FILE_SIZE = 1 * 1024 * 1024 * 1024; // 1GB
    private static final String CONNECTION_URL = "tcp://localhost:61616?minLargeMessageSize=10240&compressLargeMessages=true";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String QUEUE_NAME = "my-queue";

    public static void main(String[] args) {
        try {
            long maxMemory = Runtime.getRuntime().maxMemory();
            System.out.printf("Xmx value: %d bytes%n", maxMemory);

            File inputFile = new File("target/huge-message.dat");
            createFile(inputFile, FILE_SIZE);

            // large message streaming is only supported by Core (this example) and AMQP protocols
            ConnectionFactory factory = new ActiveMQConnectionFactory(CONNECTION_URL);
            Connection connection = factory.createConnection(USERNAME, PASSWORD);
            connection.setExceptionListener(e -> System.err.printf("%s%n", e));
            System.out.println("CONNECTED");

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue(QUEUE_NAME);
            MessageProducer producer = session.createProducer(destination);

            // when sending the message will read the InputStream until it gets EOF
            BytesMessage message = session.createBytesMessage();
            FileInputStream fileInputStream = new FileInputStream(inputFile);
            BufferedInputStream bufferedInput = new BufferedInputStream(fileInputStream);
            message.setObjectProperty("JMS_AMQ_InputStream", bufferedInput);
            System.out.println("Sending message...");
            producer.send(message);
            System.out.printf("Message sent: %s%n", message.getJMSMessageID());

            // when we receive the large message we initially just receive the message with an empty body
            MessageConsumer messageConsumer = session.createConsumer(destination);
            connection.start();
            System.out.println("Receiving message...");
            BytesMessage messageReceived = (BytesMessage) messageConsumer.receive(120_000);
            System.out.printf("Message of size %d received%n", messageReceived.getLongProperty("_AMQ_LARGE_SIZE"));

            File outputFile = new File("target/huge-message-received.dat");
            System.out.println("Streaming file to disk...");
            try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
                BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutputStream);
                // this will save the stream and wait until the entire message is written before continuing
                messageReceived.setObjectProperty("JMS_AMQ_SaveStream", bufferedOutput);
            }
            System.out.printf("File of size %d bytes streamed%n", outputFile.length());

            connection.close();
        } catch (Throwable e) {
            System.err.printf("%s%n", e);
        }
    }

    private static void createFile(File file, long fileSize) throws IOException {
        FileOutputStream fileOut = new FileOutputStream(file);
        try (BufferedOutputStream buffOut = new BufferedOutputStream(fileOut)) {
           byte[] outBuffer = new byte[1024 * 1024];
           for (long i = 0; i < fileSize; i += outBuffer.length) {
              buffOut.write(outBuffer);
           }
        }
     }
}

package it.fvaleri.example;

import io.quarkus.arc.Unremovable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import javax.enterprise.context.ApplicationScoped;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Unremovable
@ApplicationScoped
public class MessageProducer {
    private static final Logger LOG = LoggerFactory.getLogger(MessageProducer.class);

    @ConfigProperty(name = "jms.url")
    public String url;

    @ConfigProperty(name = "jms.username")
    public String username;

    @ConfigProperty(name = "jms.password")
    public String password;

    @ConfigProperty(name = "jms.totalMessages")
    public int totalMessages;

    @ConfigProperty(name = "jms.maxConcurrentProducers")
    public int maxConcurrentProducers;

    @ConfigProperty(name = "jms.keystore.path")
    public String keyStorePath;

    @ConfigProperty(name = "jms.keystore.password")
    public String keyStorePassword;

    @ConfigProperty(name = "jms.truststore.path")
    public String trustStorePath;

    @ConfigProperty(name = "jms.truststore.password")
    public String trustStorePassword;

    public void start() {
        try {
            // We use the JMS library because Camel AMQP producer creates a new TCP
            // connection, session and producer for every single message to be sent.
            // This greatly degrades performance, especially when using TLS.
            // Alternatively, you need to enable connection pooling.
            AtomicInteger counter = new AtomicInteger(-1);
            ExecutorService executor = Executors.newFixedThreadPool(maxConcurrentProducers);
            String connectionUrl = String.format(
                    "%s?transport.keyStoreLocation=%s&transport.keyStorePassword=%s&transport.trustStoreLocation=%s" +
                            "&transport.trustStorePassword=%s&transport.verifyHost=false",
                    url, keyStorePath, keyStorePassword, trustStorePath, trustStorePassword);
            ConnectionFactory factory = new JmsConnectionFactory(connectionUrl);
            Connection connection = factory.createConnection(username, password);
            LongStream.range(0, maxConcurrentProducers).forEach(n -> {
                executor.execute(new Producer(connection, counter));
            });
            stopExecutor(executor, 60_000);
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e1) {
                }
            }
        } catch (JMSException e) {
            LOG.error("{}", e);
        }
    }

    // executor services create non-daemon threads by default, which prevent JVM shutdown
    private void stopExecutor(ExecutorService executor, long timeoutMs) {
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

    public class Producer implements Runnable {
        private Connection connection;
        private AtomicInteger counter;
        public Producer(Connection connection, AtomicInteger counter) {
            this.connection = connection;
            this.counter = counter;
        }
        @Override
        public void run() {
            try {
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createQueue("my-queue");
                javax.jms.MessageProducer producer = session.createProducer(destination);
                while (true) {
                    int i = counter.incrementAndGet();
                    if (i >= totalMessages) {
                        break;
                    }
                    String payload = "test" + i;
                    producer.send(session.createTextMessage(payload));
                    LOG.debug("{}", payload);
                }
            } catch (Throwable e) {
                LOG.error("{}", e);
            }
        }
    }
}

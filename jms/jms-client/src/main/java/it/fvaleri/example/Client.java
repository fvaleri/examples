package it.fvaleri.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Client extends Thread {
    protected static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private static final Random RND = new Random(0);

    protected AtomicLong messageCount = new AtomicLong(0);
    protected AtomicBoolean closed = new AtomicBoolean(false);
    protected List<Message> batchBuffer = new ArrayList<>();

    public Client(String threadName) {
        super(threadName);
    }

    @Override
    public void run() {
        try {
            LOG.info("Starting up");
            execute();
            shutdown(null);
        } catch (Throwable e) {
            LOG.error("Unhandled exception");
            shutdown(e);
        }

    }

    public void shutdown(Throwable e) {
        if (!closed.get()) {
            LOG.info("Shutting down");
            closed.set(true);
            onShutdown();
            if (e != null) {
                e.printStackTrace();
                System.exit(1);
            } else {
                System.exit(0);
            }
        }
    }

    /**
     * Implement the execution loop.
     */
    abstract void execute() throws Exception;

    /**
     * Override if custom shutdown logic is needed.
     */
    void onShutdown() {
    }

    // this method does not include all fatal errors
    // additionally, you may want to add your business logic errors
    boolean retriable(Exception e) {
        if (e == null) {
            return false;
        } else if (e instanceof IllegalArgumentException
            || e instanceof UnsupportedOperationException) {
            // non retriable exception
            return false;
        } else {
            // retriable
            return true;
        }
    }

    byte[] randomBytes(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Record size must be greater than zero");
        }
        byte[] payload = new byte[size];
        for (int i = 0; i < payload.length; ++i) {
            payload[i] = (byte) (RND.nextInt(26) + 65);
        }
        return payload;
    }

    Connection connect() throws JMSException {
        if (Configuration.SSL_TRUSTSTORE_LOCATION != null) {
            System.setProperty("javax.net.ssl.trustStore", Configuration.SSL_TRUSTSTORE_LOCATION);
            System.setProperty("javax.net.ssl.trustStorePassword", Configuration.SSL_TRUSTSTORE_PASSWORD);
            if (Configuration.SSL_KEYSTORE_LOCATION != null) {
                System.setProperty("javax.net.ssl.keyStore", Configuration.SSL_KEYSTORE_LOCATION);
                System.setProperty("javax.net.ssl.keyStorePassword", Configuration.SSL_KEYSTORE_PASSWORD);
            }
        }
        ConnectionFactory factory = null;
        switch (Configuration.PROTOCOL_NAME) {
            case "openwire":
                factory = new org.apache.activemq.ActiveMQConnectionFactory(Configuration.CONNECTION_URL);
                break;
            case "core":
                factory = new org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory(Configuration.CONNECTION_URL);
                break;
            case "amqp":
                factory = new org.apache.qpid.jms.JmsConnectionFactory(Configuration.CONNECTION_URL);
                break;
            default:
                throw new IllegalArgumentException("Unknown ConnectionFactory type");
        }
        Connection connection = null; // thread-safe
        if (Configuration.CONNECTION_USERNAME != null && Configuration.CONNECTION_PASSWORD != null) {
            connection = factory.createConnection(Configuration.CONNECTION_USERNAME, Configuration.CONNECTION_PASSWORD);
        } else {
            connection = factory.createConnection();
        }
        if (Configuration.CLIENT_ID != null) {
            connection.setClientID(Configuration.CLIENT_ID);
        }
        return connection;
    }

    Destination createDestination(Session session) throws JMSException {
        if (Configuration.QUEUE_NAME != null) {
            return session.createQueue(Configuration.QUEUE_NAME);
        } else if (Configuration.TOPIC_NAME != null) {
            return session.createTopic(Configuration.TOPIC_NAME);
        } else {
            throw new RuntimeException("Empty destination");
        }
    }

    // creating a consumer involves a network round trip to the broker
    MessageConsumer createConsumer(Session session, Destination destination) throws JMSException {
        if (Configuration.SUBSCRIPTION_NAME != null) {
            return session.createDurableSubscriber((Topic) destination,
                    Configuration.SUBSCRIPTION_NAME, Configuration.MESSAGE_SELECTOR, false);
        } else {
            return session.createConsumer(destination, Configuration.MESSAGE_SELECTOR);
        }
    }

    void maybeCommitBatch(Session session, long messageCount) {
        if (closed.get() || batchBuffer.size() == Configuration.TXN_BATCH_MSGS
                || messageCount >= Configuration.NUM_MESSAGES) {
            try {
                LOG.debug("Batch commit");
                session.commit();
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
            batchBuffer.clear();
        }
    }

    void rollbackBatch(Session session) {
        try {
            LOG.debug("Batch rollback");
            session.rollback();
        } catch (JMSException e) {
            LOG.error("Rollback failed");
        }
    }
}

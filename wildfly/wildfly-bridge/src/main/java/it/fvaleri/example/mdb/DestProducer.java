package it.fvaleri.example.mdb;

import javax.jms.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DestProducer implements AutoCloseable {
    private final static Logger LOG = LoggerFactory.getLogger(DestProducer.class);

    private Connection conn;

    public DestProducer(ConnectionFactory cf) {
        try {
            if (conn == null) {
                conn = cf.createConnection();
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void forwardMessage(Message message, String queueName) throws Exception {
        // session is enrolled in the main tx and settings are ignored
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(queueName);
        MessageProducer producer = session.createProducer(queue);
        producer.send(message);
        LOG.info("Message routed to destination");
    }

    @Override
    public void close() throws Exception {
        // connection is just released to the pool
        LOG.debug("Connection release");
        if (conn != null) {
            conn.close();
        }
    }
}


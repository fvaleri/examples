package it.fvaleri.example;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import static it.fvaleri.example.Configuration.ENABLE_TXN;
import static it.fvaleri.example.Configuration.MESSAGE_DELIVERY;
import static it.fvaleri.example.Configuration.MESSAGE_PRIORITY;
import static it.fvaleri.example.Configuration.MESSAGE_SIZE_BYTES;
import static it.fvaleri.example.Configuration.MESSAGE_TTL_MS;
import static it.fvaleri.example.Configuration.NUM_MESSAGES;
import static it.fvaleri.example.Configuration.PROCESSING_DELAY_MS;

public class Producer extends Client implements ExceptionListener {
    private Connection connection;
    private Session session;
    private MessageProducer producer;

    public Producer(String threadName) {
        super(threadName);
    }

    @Override
    public void execute() throws Exception {
        connection = connect();
        connection.setExceptionListener(this);
        session = connection.createSession(ENABLE_TXN, Session.AUTO_ACKNOWLEDGE);
        Destination destination = createDestination(session);
        producer = session.createProducer(destination);
        producer.setDeliveryMode(MESSAGE_DELIVERY);
        producer.setPriority(MESSAGE_PRIORITY);
        producer.setTimeToLive(MESSAGE_TTL_MS);
        BytesMessage message = session.createBytesMessage();
        message.writeBytes(randomBytes(MESSAGE_SIZE_BYTES));
        while (!closed.get() && messageCount.get() < NUM_MESSAGES) {
            try {
                sleep(PROCESSING_DELAY_MS);
                // sync send with persistent delivery
                // async send with transacted session and non-persistent delivery
                producer.send(message);
                messageCount.incrementAndGet();
                System.out.println("Message sent");
                if (ENABLE_TXN) {
                    batchBuffer.add(message);
                    maybeCommitBatch(session, messageCount.get());
                }
            } catch (Exception e) {
                System.err.println(e.getMessage());
                if (ENABLE_TXN) {
                    rollbackBatch(session);
                }
                if (!retriable(e)) {
                    shutdown(e);
                }
            }
        }
    }

    @Override
    public void onShutdown() {
        if (ENABLE_TXN) {
            maybeCommitBatch(session, messageCount.get());
        }
        try {
            producer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
        }
    }

    @Override
    public void onException(JMSException e) {
        System.err.println(e.getMessage());
    }
}

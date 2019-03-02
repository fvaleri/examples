package it.fvaleri.example;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import static it.fvaleri.example.Configuration.ENABLE_TXN;
import static it.fvaleri.example.Configuration.NUM_MESSAGES;
import static it.fvaleri.example.Configuration.PROCESSING_DELAY_MS;
import static it.fvaleri.example.Configuration.RECEIVE_TIMEOUT_MS;

public class Consumer extends Client implements ExceptionListener {
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public Consumer(String threadName) {
        super(threadName);
    }

    @Override
    public void execute() throws Exception {
        connection = connect();
        connection.setExceptionListener(this);
        // non-transacted and AUTO_ACKNOWLEDGE (the message is acked only when onMessage completes successfully)
        session = connection.createSession(ENABLE_TXN, Session.AUTO_ACKNOWLEDGE);
        Destination destination = createDestination(session);
        consumer = createConsumer(session, destination);
        connection.start();
        LOG.info("Consuming from {}", destination);
        while (!closed.get() && messageCount.get() < NUM_MESSAGES) {
            try {
                Message message = consumer.receive(RECEIVE_TIMEOUT_MS);
                LOG.info("Message received{}", message.getJMSRedelivered() ? " (redelivered)" : "");
                sleep(PROCESSING_DELAY_MS);
                messageCount.incrementAndGet();
            } catch (Exception e) {
                LOG.error(e.getMessage());
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
            consumer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
        }
    }

    @Override
    public void onException(JMSException e) {
        LOG.error(e.getMessage());
        if (ENABLE_TXN) {
            rollbackBatch(session);
        }
    }
}

package it.fvaleri.example.mdb;

import javax.annotation.Resource;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;

import org.jboss.ejb3.annotation.ResourceAdapter;

@ResourceAdapter(value = "sourcePooledCF")
@MessageDriven(name = "SourceMDB", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "queue/SourceQueue"),
    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
    @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "15"),
})
public class SourceMDB implements MessageListener {
    private final static Logger LOG = LoggerFactory.getLogger(SourceMDB.class);

    @Resource(mappedName = "java:/jms/destPooledCF")
    private ConnectionFactory remoteCF;

    public void onMessage(Message message) {
        LOG.info("Message received from source");
        try (DestProducer destProducer = new DestProducer(remoteCF)) {
            final String destQueueName = "DestQueue";
            destProducer.forwardMessage(message, destQueueName);
        } catch (Throwable e) {
            LOG.error("Bridge error", e);
        }
    }
}


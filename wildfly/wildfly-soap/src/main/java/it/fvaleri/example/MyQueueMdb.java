package it.fvaleri.example;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jboss.ejb3.annotation.ResourceAdapter;

/*
Every MDB class uses one InboundConnection and can create up to maxSession (1 instance == 1 session == 1 consumer).
To enable parallelism you need to set MDB.maxSession == EJB3.mdb-strict-max-pool.max-pool-size > 1, but the number 
of active threads per-MDB is determined by the shared thread-pool size and message volume.
/subsystem="ejb3"/strict-max-bean-instance-pool="mdb-strict-max-pool":read-resource(recursive="true",include-runtime="true",include-defaults="true")
/subsystem="ejb3"/strict-max-bean-instance-pool="mdb-strict-max-pool":undefine-attribute(name="derive-size")
/subsystem="ejb3"/strict-max-bean-instance-pool="mdb-strict-max-pool":write-attribute(name="max-pool-size",value="100")
/subsystem="jca"/workmanager="default"/short-running-threads="default":write-attribute(name="core-threads",value="1500")
/subsystem="jca"/workmanager="default"/short-running-threads="default":write-attribute(name="max-threads",value="1500")
*/

@ResourceAdapter(value = "activemq-ra")
@MessageDriven(name = "MyQueueMdb", activationConfig = {
        // useJNDI=false does not work due to ENTMQBR-1179
        @ActivationConfigProperty(propertyName = "useJNDI", propertyValue = "true"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "java:/jms/queue/my-queue"),
        //@ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "queue/my-queue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
        // number of parallel consumers (default 15, must be <= mdb-strict-max-pool.max-pool-size/derive-size)
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "15"),
        // hA and rebalanceConnections to enable reconnection in case of clustering
        @ActivationConfigProperty(propertyName = "hA", propertyValue = "true"),
        @ActivationConfigProperty(propertyName = "rebalanceConnections", propertyValue = "true"),
        // if user authentication is enabled
        @ActivationConfigProperty(propertyName = "user", propertyValue = "admin"),
        @ActivationConfigProperty(propertyName = "password", propertyValue = "admin")
})
public class MyQueueMdb implements MessageListener {
    private static final Logger LOG = LoggerFactory.getLogger(MyQueueMdb.class);
    
    @Override
    public void onMessage(Message msg) {
        try {
            if (msg instanceof TextMessage) {
                TextMessage message = (TextMessage) msg;
                LOG.info("Received message {}", message.getJMSMessageID());
            } else {
                LOG.warn("Wrong type: {}", msg.getClass().getName());
            }
        } catch (JMSException e) {
            LOG.error("MDB error", e);
            throw new RuntimeException(e);
        }
    }
}


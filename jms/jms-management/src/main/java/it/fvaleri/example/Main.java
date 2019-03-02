package it.fvaleri.example;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import java.util.HashMap;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

public class Main {
    private static final String JMX_URL = "service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi";
    private static final String CONNECTION_URL = "tcp://localhost:61616";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String QUEUE_NAME = "my-queue";

    public static void main(String[] args) {
        try {
            ConnectionFactory factory = new ActiveMQConnectionFactory(CONNECTION_URL);
            Connection connection = factory.createConnection(USERNAME, PASSWORD);
            connection.setExceptionListener(e -> System.err.printf("%s%n", e));
            System.out.println("CONNECTED");

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue(QUEUE_NAME);
            MessageProducer producer = session.createProducer(destination);

            TextMessage message = session.createTextMessage("abc");
            message.setIntProperty("myMessageID", 123);
            producer.send(message);
            System.out.printf("Sent message: %s%n", message.getText());

            // retrieve the ObjectName of the queue
            ObjectName on = ObjectNameBuilder.DEFAULT.getQueueObjectName(SimpleString.toSimpleString(QUEUE_NAME),
                    SimpleString.toSimpleString(QUEUE_NAME), RoutingType.ANYCAST);

            // create JMXConnector to connect to the MBeanServer
            HashMap<String, String[]> env = new HashMap<>();
            String[] creds = { USERNAME, PASSWORD };
            env.put(JMXConnector.CREDENTIALS, creds);
            JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(JMX_URL), env);

            // retrieve QueueControl to manage the queue
            MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            QueueControl queueCtrl = MBeanServerInvocationHandler.newProxyInstance(mbsc, on, QueueControl.class, false);
            System.out.printf("The queue contains %d messages%n", queueCtrl.getMessageCount());

            // filter on message properties
            CompositeData[] browse = queueCtrl.browse("myMessageID=123");
            if (browse != null && browse.length > 0) {
                String text = (String) browse[0].get("text");
                System.out.printf("Found message: %s%n", text);
            }

            System.out.printf("Removed messages: %d%n", queueCtrl.removeMessages(null));
            System.out.printf("The queue contains %d messages%n", queueCtrl.getMessageCount());

            connector.close();
            connection.close();
        } catch (Throwable e) {
            System.err.printf("%s%n", e);
        }
    }
}

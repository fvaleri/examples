package it.fvaleri.example;

import javax.jws.WebService;
import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.jboss.ws.api.annotation.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebService(
    serviceName = "HelloService",
    portName = "Hello",
    name = "Hello",
    endpointInterface = "it.fvaleri.example.HelloService",
    targetNamespace = "http://it.fvaleri.example/hello"
)
@WebContext(
    contextRoot = "/services",
    urlPattern = "/hello",
    transportGuarantee = "NONE",
    secureWSDLAccess = false
)
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class HelloServiceImpl implements HelloService {
    private static final Logger LOG = LoggerFactory.getLogger(HelloServiceImpl.class);

    @Resource(mappedName = "java:/JmsXA")
    private ConnectionFactory cf;

    @Resource(mappedName = "java:/jms/queue/my-queue")
    private Destination dest;

    @Override
    public String writeText(String text) {
        sendMessage(text);
        return "OK";
    }

    private void sendMessage(String text) {
        try {
            Connection conn = cf.createConnection();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(dest);
            TextMessage message = session.createTextMessage(text);
            producer.send(message);
            LOG.info("Sent message {}", message.getJMSMessageID());
            conn.close();
        } catch (Exception e) {
            LOG.error("Send error", e);
        }
    }
}


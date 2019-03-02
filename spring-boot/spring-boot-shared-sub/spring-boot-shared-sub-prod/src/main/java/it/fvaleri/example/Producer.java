package it.fvaleri.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class Producer implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(Producer.class);

    @Autowired
    public JmsTemplate jmsTemplate;

    @Override
    public void run(String... strings) throws Exception {
        jmsTemplate.setPubSubDomain(true);
        for (int i = 0; i < 10; i++) {
            sendMessage("example.foo", "Hello World " + i);
            Thread.sleep(100);
        }

    }

    public void sendMessage(String queue, String message) {
        jmsTemplate.convertAndSend(queue, message);
        LOG.info("Sent {}", message);
    }
}


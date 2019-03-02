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
        sendMessage("example.foo", "Hello World");
    }

    public void sendMessage(String queue, String message) {
        jmsTemplate.convertAndSend(queue, message);
        LOG.info("Produced: {}", message);
    }
}

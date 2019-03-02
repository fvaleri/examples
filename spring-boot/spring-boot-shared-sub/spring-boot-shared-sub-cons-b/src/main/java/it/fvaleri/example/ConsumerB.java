package it.fvaleri.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class ConsumerB {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumerB.class);

    @JmsListener(destination = "example.foo", id = "ConsumerB", subscription = "example.foo", containerFactory = "jmsListenerContainerFactory")
    public void processMsg(String message) {
        LOG.info("Got {}", message);
    }
}


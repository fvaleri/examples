package it.fvaleri.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class ConsumerA2 {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumerA2.class);

    @JmsListener(destination = "example.foo", id = "ConsumerA2", subscription = "example.foo", containerFactory = "jmsListenerContainerFactory")
    public void processMsg(String message) {
        LOG.info("Got {}", message);
    }
}


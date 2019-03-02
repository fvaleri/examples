package it.fvaleri.example;

import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.component.sjms2.jms.Jms2ObjectFactory;

public class DelayedDeliveryOF extends Jms2ObjectFactory {
    @Override
    public MessageProducer createMessageProducer(Session session, Destination destination, boolean persistent, long ttl)
            throws Exception {
        MessageProducer producer = super.createMessageProducer(session, destination, persistent, ttl);
        producer.setDeliveryDelay(5_000);
        return producer;
    }
}


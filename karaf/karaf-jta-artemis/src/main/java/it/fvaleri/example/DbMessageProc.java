package it.fvaleri.example;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class DbMessageProc implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        final DbMessage dbMessage = new DbMessage(exchange.getIn().getBody(String.class));
        exchange.getOut().setBody(dbMessage);
    }
}


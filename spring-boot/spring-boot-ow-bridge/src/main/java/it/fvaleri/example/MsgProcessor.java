package it.fvaleri.example;

import java.util.Random;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MsgProcessor implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(MsgProcessor.class);
    private boolean raiseError = false;

    public void process(Exchange exchange) throws Exception {
        int randomSleep = new Random().nextInt(600-300) + 300;
        LOG.trace("Message processing for {} ms", randomSleep);
        Thread.sleep(randomSleep);

        if (raiseError) {
            throw new RuntimeException("Forced exception");
        }

        LOG.trace("Message done");
    }

    public boolean isRaiseError() {
        return raiseError;
    }

    public void setRaiseError(boolean raiseError) {
        this.raiseError = raiseError;
    }
}


package it.fvaleri.example;

import java.util.concurrent.TimeUnit;
import org.apache.camel.Exchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageWorker {
    private static final Logger LOG = LoggerFactory.getLogger(MessageWorker.class);

    public void process() throws Exception {
        LOG.info("Processing message");
        TimeUnit.MILLISECONDS.sleep(600);
    }
}

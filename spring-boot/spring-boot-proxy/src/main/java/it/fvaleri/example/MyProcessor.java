package it.fvaleri.example;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class MyProcessor implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(MyProcessor.class);

    public void process(Exchange exchange) throws Exception {
        LOG.info("Processing external service response");
        InputStream is = exchange.getIn().getBody(InputStream.class);
        if (is == null) {
            exchange.getIn().setBody("InputStream is null");
            exchange.getIn().setBody(new ApiMessage(1, "InputStream is null"));
            return;
        } else {
            String content = new BufferedReader(new InputStreamReader(is))
                .lines().collect(Collectors.joining("\n"));
            if (content.equals("{}")) {
                String message = "No item found with id " + exchange.getIn().getHeader("id");
                exchange.getIn().setBody(new ApiMessage(2, message));
                return;
            }
            exchange.getIn().setBody(new ApiMessage(0, content));
        }
    }
}

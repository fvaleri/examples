package it.fvaleri.example;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RowProcessor implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(RowProcessor.class);

    public void process(Exchange exchange) throws Exception {
        //Map<String, Object> row = exchange.getIn().getBody(Map.class);
        Map<String, Object> headers = exchange.getIn().getHeaders();

        Map<String, Object> generatedKeys = (Map<String, Object>)
            ((List<Object>) headers.get("CamelGeneratedKeysRows")).get(0);
        LOG.info("CamelGeneratedKeysRows: " + generatedKeys);

        User user = new User();
        user.setId((long) generatedKeys.get("insert_id"));
        user.setName((String) headers.get("userName"));

        exchange.getOut().setBody(user);
    }
}


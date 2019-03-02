package it.fvaleri.example;

import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GreetingValidator extends Validator {
    private static final Logger LOG = LoggerFactory.getLogger(GreetingValidator.class);
    private String greeting = "Hello there";

    @Override
    public void validate(Message message, DataType type) throws ValidationException {
        Object body = message.getBody();
        LOG.info("Validating {}", body);
        if (body instanceof String && body.equals(greeting)) {
            LOG.info("Valid");
        } else {
            throw new ValidationException(message.getExchange(), "Wrong content");
        }
    }
}

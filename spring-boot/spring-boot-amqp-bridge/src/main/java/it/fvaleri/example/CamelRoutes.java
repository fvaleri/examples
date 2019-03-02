package it.fvaleri.example;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.LoggingLevel;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CamelRoutes extends RouteBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(CamelRoutes.class);

    private AtomicInteger receiveCounter = new AtomicInteger();
    private int receiveCounterLast = 0;
    private AtomicInteger sendCounter = new AtomicInteger();
    private int sendCounterLast = 0;

    @Value("${receive.enabled}")
    Boolean receiveEnabled;

    @Value("${send.enabled}")
    Boolean sendEnabled;

    @Value("${send.threads}")
    int sendThreads;

    @Value("${send.message}")
    String sendMessage;

    @Override
    public void configure() throws Exception {
        from("amqp:{{receive.endpoint}}").routeId("receiver").autoStartup("{{receive.enabled}}")
            .transacted()
            .log(LoggingLevel.DEBUG, LOG, "Message received: ${exchangeId} - ${body}")
            .choice().when(simple("${body} contains 'error'"))
                .throwException(new Exception("Forced error"))
            .end()
            .choice().when(constant("{{receive.forward.enabled}}"))
                .delay(constant("{{receive.forward.delay}}"))
                .log(LoggingLevel.DEBUG, LOG, "Message forward: ${exchangeId}")
                .to("amqp:{{receive.forward.endpoint}}")
            .end()
            .delay(constant("{{receive.delay}}"))
            .log(LoggingLevel.DEBUG, LOG, "Message processed: ${exchangeId}")
            .process(e -> receiveCounter.incrementAndGet());

        from("timer:sender?period=1&repeatCount={{send.threads}}").routeId("sender").autoStartup("{{send.enabled}}")
            .threads().poolSize(sendThreads).maxPoolSize(sendThreads).maxQueueSize(sendThreads).rejectedPolicy(ThreadPoolRejectedPolicy.Discard)
            .log(LoggingLevel.INFO, LOG, "Sending {{send.count}}")
            .loop(constant("{{send.count}}"))
                .log(LoggingLevel.DEBUG, LOG, "Send message: ${exchangeId}-${header.CamelLoopIndex}")
                .setBody(simple(sendMessage))
                .setHeader("{{send.headeruuid}}").exchange(e -> java.util.UUID.randomUUID().toString())
                .to("amqp:{{send.endpoint}}?transacted=true")
                .process(e -> sendCounter.incrementAndGet())
                .delay(constant("{{send.delay}}"))
                .log(LoggingLevel.DEBUG, LOG, "Sent message: ${exchangeId}-${header.CamelLoopIndex} - ${body}")
            .end()
        .end()
        .log(LoggingLevel.INFO, LOG, "Done {{send.count}}");

        from("timer:counter?period=1000").routeId("counter")
            .setBody(b -> {
                if (receiveEnabled) {
                    int current = receiveCounter.get();
                    int diff = current - receiveCounterLast;
                    receiveCounterLast = current;
                    return "Receive: " + current + " - " + diff + "/s";
                }
                if (sendEnabled) {
                    int current = sendCounter.get();
                    int diff = current - sendCounterLast;
                    sendCounterLast = current;
                    return "Send: " + current + " - " + diff + "/s";
                }
                return null;
            }).log(LoggingLevel.INFO, LOG, "${body}");
    }
}


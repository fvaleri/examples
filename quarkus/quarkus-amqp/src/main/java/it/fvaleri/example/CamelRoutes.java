package it.fvaleri.example;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class CamelRoutes extends RouteBuilder {
    @Override
    public void configure() {
        from("timer:foo?repeatCount=1&period=1")
                .log("Test started")
                .bean(MessageProducer.class);

        from("jms:queue:my-queue")
                .log("${body}")
                .aggregate(constant("true"), (oldE, newE) -> newE)
                    .completionSize(simple("{{jms.totalMessages}}"))
                .log("All messages consumed");
    }
}

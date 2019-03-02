package it.fvaleri.example;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class CamelRoutes extends RouteBuilder {
    @Inject
    Greeter greeter;

    @Override
    public void configure() {
        fromF("timer://foo?period={{timer.period}}")
            .setBody(e -> greeter.greet())
            .log("${body}");
    }
}

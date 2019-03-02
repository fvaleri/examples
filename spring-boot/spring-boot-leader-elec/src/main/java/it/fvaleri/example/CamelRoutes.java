package it.fvaleri.example;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class CamelRoutes extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        // we use "leaders" configMap to store pod name of the current owner
        // of each lock declared (lock1 for master and lock2 for the custom service)
        from("master:lock1:timer:clock")
                .log("Hello World");
    }
}

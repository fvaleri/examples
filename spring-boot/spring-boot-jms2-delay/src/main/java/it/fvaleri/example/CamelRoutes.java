package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms2.Sjms2Endpoint;
import org.springframework.stereotype.Component;

@Component
public class CamelRoutes extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        CamelContext context = getContext();

        DelayedDeliveryOF delayedDeliveryOF = (DelayedDeliveryOF) context.getRegistry()
                .lookupByName("delayedDeliveryOF");

        Sjms2Endpoint endpoint = context.getEndpoint("sjms2:my-queue", Sjms2Endpoint.class);
        endpoint.setJmsObjectFactory(delayedDeliveryOF);

        from("timer://foo?fixedRate=true&period=60000&repeatCount=1")
            .routeId("producer")
            .setBody(constant("Hello World"))
            .log("Message sent")
            .to(endpoint);

        from(endpoint)
            .routeId("consumer")
            .log("Message received");
    }
}


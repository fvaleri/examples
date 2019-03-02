package it.fvaleri.example;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CamelRoutes extends RouteBuilder {
    @Value("${camel.component.servlet.mapping.context-path}")
    public String contextPath;

    @Override
    public void configure() throws Exception {
        restConfiguration()
            .contextPath(contextPath)
            .bindingMode(RestBindingMode.json)
            .component("servlet")
            .enableCORS(true)
            .apiContextPath("/doc")
            .apiProperty("api.title", "Greeting API")
            .apiProperty("api.version", "1.0");

        rest("/greet").description("Greet {name}")
            .get("/{name}").outType(Greeting.class)
                .route()
                    .routeId("greet-api")
                    .description("Greeting service")
                    .log("Processing new request")
                    .to("direct:greet");

        from("direct:greet")
            .routeId("greet-impl")
            .description("Greeting service impl")
            .streamCaching()
            .bean("greetingService", "getGreeting");
    }
}


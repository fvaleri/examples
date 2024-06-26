package it.fvaleri.example;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestParamType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CamelRoutes extends RouteBuilder {
    @Value("${camel.component.servlet.mapping.context-path}")
    public String contextPath;

    @Override
    public void configure() {
        restConfiguration()
            .contextPath(contextPath);

        rest()
            .get("/messages")
            .produces("text/plain")
            .route()
                .to("sql:select message from audit_log order by id")
                .convertBodyTo(String.class);

        rest()
            .post("/messages/{message}")
            .param().name("message").type(RestParamType.path).dataType("string").endParam()
            .produces("text/plain")
            .route()
                .to("direct:trans");

        from("direct:trans")
            .transacted()
            .setBody(simple("${headers.message}"))
            .to("sql:insert into audit_log (message) values (:#message)")
            .to("jms:outbound?disableReplyTo=true")
            .choice()
                .when(body().startsWith("fail"))
                    .log("Forced exception")
                        .process(x -> {throw new RuntimeException("fail");})
                .otherwise()
                    .log("Message added: ${body}")
            .endChoice();

        from("jms:outbound")
            .log("Message out: ${body}")
            .setHeader("message", simple("${body}-ok"))
            .to("sql:insert into audit_log (message) values (:#message)");
    }
}

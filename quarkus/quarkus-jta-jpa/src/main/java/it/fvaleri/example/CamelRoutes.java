package it.fvaleri.example;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestParamType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CamelRoutes extends RouteBuilder {
    @ConfigProperty(name = "camel.servlet.mapping.context-path")
    public String contextPath;

    @Override
    public void configure() {
        restConfiguration()
                .contextPath(contextPath);

        rest("/messages")
                .produces("text/plain")
                .get()
                    .to("direct:messages")
                .post("/{message}")
                    .param().name("message").type(RestParamType.path).dataType("string").endParam()
                    .to("direct:trans");

        from("direct:messages")
                .to("jpa:it.fvaleri.example.AuditLog?namedQuery=getAuditLog")
                .convertBodyTo(String.class);

        from("direct:trans")
                .transacted()
                .setBody(simple("${headers.message}"))
                .to("bean:auditLog?method=createAuditLog(${body})")
                .to("jpa:it.fvaleri.example.AuditLog")
                .setBody(simple("${headers.message}"))
                .to("jms:outbound?disableReplyTo=true")
                .choice()
                    .when(body().startsWith("fail"))
                        .log("Forced exception")
                        .process(x -> {
                            throw new RuntimeException("fail");
                        })
                .otherwise()
                    .log("Message added: ${body}")
                .endChoice();

        from("jms:outbound")
                .log("Message out: ${body}")
                .to("bean:auditLog?method=createAuditLog(${body}-ok)")
                .to("jpa:it.fvaleri.example.AuditLog");
    }
}

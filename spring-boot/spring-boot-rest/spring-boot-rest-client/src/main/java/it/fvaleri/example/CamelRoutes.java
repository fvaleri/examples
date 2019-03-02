package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.jaxrs.CxfRsEndpoint;
import org.apache.cxf.jaxrs.impl.ResponseImpl;
import org.springframework.stereotype.Component;

@Component
public class CamelRoutes extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("timer:foo?period={{client.period}}")
            .process(new MyProcessor())
            .log("Service response: ${body}");
    }

    class MyProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            CamelContext context = exchange.getContext();

            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            exchange.getIn().setBody(null);
            CxfRsEndpoint endpoint = (CxfRsEndpoint) context.getEndpoint("cxfrs://{{client.endpoint}}");
            endpoint.setCxfRsEndpointConfigurer(new CxfConfigurer());

            ProducerTemplate producer = context.createProducerTemplate();
            ResponseImpl response = (ResponseImpl) producer.sendBodyAndHeaders(endpoint,
                ExchangePattern.InOut, exchange.getIn().getBody(), exchange.getIn().getHeaders());

            exchange.getIn().setBody(response.readEntity(String.class));
        }

    }
}


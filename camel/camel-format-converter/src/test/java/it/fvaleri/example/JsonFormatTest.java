package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JsonFormatTest extends CamelTestSupport {
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.disableJMX();
        return ctx;
    }

    @Override
    protected int getShutdownTimeout() {
        return 60;
    }

    @Test
    public void test() throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("{\"name\":\"Camel\",\"price\":160.55,\"amount\":1.0}");

        Order order = new Order();
        order.setPrice(160.55);
        order.setAmount(1);
        order.setName("Camel");

        template.sendBody("direct:order", order);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                JacksonDataFormat jacksonDataFormat = new JacksonDataFormat();
                jacksonDataFormat.setPrettyPrint(false);

                from("direct:order")
                    .marshal(jacksonDataFormat)
                    .log("*** ${body}")
                    .to("mock:result");
            }
        };
    }
}


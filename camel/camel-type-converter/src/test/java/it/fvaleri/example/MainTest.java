package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class MainTest extends CamelTestSupport {
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
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Order.class);

        byte[] data = "#START#Camel    160.55     1#END#".getBytes();
        Order order = template.requestBody("direct:start", data, Order.class);

        assertMockEndpointsSatisfied();
        assertEquals("Camel", order.getName());
        assertEquals("160.55", order.getPrice().toString());
        assertEquals(1, order.getAmount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .convertBodyTo(Order.class)
                    .to("mock:result");
            }
        };
    }
}


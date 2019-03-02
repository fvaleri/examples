package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JaxbFormatTest extends CamelTestSupport {
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

        Order order = new Order();
        order.setPrice(160.55);
        order.setAmount(1);
        order.setName("Camel");

        template.sendBody("direct:order", order);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
                jaxbDataFormat.setContextPath(JaxbFormatTest.class.getPackage().getName());
                jaxbDataFormat.setPrettyPrint(false);

                from("direct:order")
                    .marshal(jaxbDataFormat)
                    .to("seda:queue:order");

                from("seda:queue:order")
                    .unmarshal(jaxbDataFormat)
                    .to("mock:result");
            }
        };
    }
}


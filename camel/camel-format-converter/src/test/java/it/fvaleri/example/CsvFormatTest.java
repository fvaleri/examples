package it.fvaleri.example;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CsvFormatTest extends CamelTestSupport {
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
        mock.expectedBodiesReceived("Camel,160.55,1\n");

        Order order = new Order();
        order.setPrice(160.55);
        order.setAmount(1);
        order.setName("Camel");

        template.sendBody("direct:toCsv", order);

        mock.assertIsSatisfied();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void csvUnmarshal() throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        ProducerTemplate template = context.createProducerTemplate();
        template.sendBody("direct:toObject", "Camel,160.55,1\nDonkey,100.00,1");

        mock.assertIsSatisfied();

        List<Object> rows = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        Order order = (Order) rows.get(0);
        assertNotNull(order);
        Order order2 = (Order) rows.get(1);
        assertNotNull(order2);

        assertEquals("Camel", order.getName());
        assertEquals(160.55, order.getPrice(), 0);
        assertEquals(1, order.getAmount(), 0);
        assertEquals("Donkey", order2.getName());
        assertEquals(100.00, order2.getPrice(), 0);
        assertEquals(1, order2.getAmount(), 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:toCsv")
                    .marshal().bindy(BindyType.Csv, Order.class)
                    .to("mock:result");

                from("direct:toObject")
                    .unmarshal().bindy(BindyType.Csv, Order.class)
                    .to("mock:result");
            }
        };
    }
}


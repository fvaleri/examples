package it.fvaleri.example;

import java.util.concurrent.Future;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class AsyncTest extends CamelTestSupport {
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

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("mySlowBean", new MySlowBean());
        return jndi;
    }

    @Test
    public void test() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("syncResponse", "asyncResponse");

        Future<Object> future = template.asyncRequestBody("direct:async", "asyncRequest");

        // we got the future so in the meantime we can do other stuff
        String syncResponse = template.requestBody("direct:sync", "syncRequest", String.class);
        assertEquals("syncResponse", syncResponse);

        // wait for the async process to complete and get the result
        String asyncResponse = template.extractFutureBody(future, String.class);
        assertEquals("asyncResponse", asyncResponse);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:async")
                    .log("${body}")
                    .bean("mySlowBean", "doSomething")
                    .to("mock:result");

                from("direct:sync")
                    .log("${body}")
                    .setBody(constant("syncResponse"))
                    .to("mock:result");
            }
        };
    }

    protected class MySlowBean {
        public void doSomething(Exchange exchange) {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
            }
            exchange.getIn().setBody("asyncResponse");
            log.info("Async task done");
        }
    }
}


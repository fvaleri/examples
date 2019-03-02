package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
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

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("counter", new CounterService());
        jndi.bind("be", new CounterService());
        return jndi;
    }

    @Test
    public void test() throws Exception {
        assertEquals("test1", template.requestBody("direct:start", "test"));
        assertEquals("test2", template.requestBody("direct:start", "test"));
        assertEquals("nope", template.requestBody("direct:start", "test"));
        assertEquals("test4", template.requestBody("direct:start", "test"));
        assertEquals("nope", template.requestBody("direct:start", "test"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .hystrix()
                        .hystrixConfiguration().executionTimeoutInMilliseconds(100).end()
                        .log("Hystrix request")
                        .to("bean:counter")
                        //.to("bean:anotherBean")
                    .onFallback()
                        // Bulkhead pattern:
                        .log("Hystrix fallback")
                        .transform(constant("nope"))
                    .end()
                    .log("Hystrix after: ${body}");
            }
        };
    }
}


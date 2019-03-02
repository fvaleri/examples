package it.fvaleri.example;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class AsyncCallbackTest extends CamelTestSupport {
    private static final CountDownLatch LATCH = new CountDownLatch(2);

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
        mock.expectedBodiesReceivedInAnyOrder("Hello Fede", "Hello Matti");

        MyCallback callback = new MyCallback();
        template.asyncCallbackRequestBody("direct:test", "Fede", callback);
        template.asyncCallbackRequestBody("direct:test", "Matti", callback);

        assertMockEndpointsSatisfied();
        assertTrue("Should get 2 callbacks", LATCH.await(20, TimeUnit.SECONDS));
        assertTrue("Fede is missing", callback.getData().contains("Hello Fede"));
        assertTrue("Matti is missing", callback.getData().contains("Hello Matti"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test")
                    .delay(300)
                    .transform(body().prepend("Hello "))
                    .to("mock:result");
            }
        };
    }

    private static class MyCallback extends SynchronizationAdapter {
        // using Vector because it is thread-safe
        private final List<String> data = new Vector<>();

        @Override
        public void onComplete(Exchange exchange) {
            String body = exchange.getOut().getBody(String.class);
            data.add(body);
            LATCH.countDown();
        }

        public List<String> getData() {
            return data;
        }
    }
}


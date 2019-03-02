package it.fvaleri.example;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class AsyncProcTest extends CamelTestSupport {
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
        jndi.bind("mySlowProc", new MySlowProc());
        return jndi;
    }

    @Test
    public void test() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        boolean done = notify.matches(20, TimeUnit.SECONDS);
        assertTrue("Should be done", done);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo?period=1s&repeatCount=5")
                    .log("New request")
                    .process("mySlowProc");
            }
        };
    }

    protected class MySlowProc implements AsyncProcessor {
        private ExecutorService customPool = Executors.newFixedThreadPool(2);

        @Override
        public void process(Exchange exchange) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            final Message in = exchange.getIn();
            customPool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                    }
                    in.setBody("OK");
                    log.info("Async task completed");
                    callback.done(false);
                }
            });
            return false;
        }
    }
}


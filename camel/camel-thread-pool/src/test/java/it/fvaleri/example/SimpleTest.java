package it.fvaleri.example;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SimpleTest extends CamelTestSupport {
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
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();
        for (int i = 0; i < 10; i++) {
            template.asyncRequestBody("direct:test", "request" + i);
        }
        boolean done = notify.matches(120, TimeUnit.SECONDS);
        assertTrue("Should be done", done);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                /*ThreadPoolProfile customPool = new ThreadPoolProfileBuilder("customPool")
                    .poolSize(2).maxPoolSize(2).maxQueueSize(10).build();
                ExecutorServiceManager manager = context.getExecutorServiceManager();
                manager.registerThreadPoolProfile(customPool);
                manager.setThreadNamePattern("#name#-#counter#");*/

                ExecutorService customPool =
                    new ThreadPoolBuilder(getContext())
                        .poolSize(2)
                        .maxPoolSize(2)
                        .maxQueueSize(10)
                        .build("customPool");

                from("direct:test")
                    .log("${body}")
                    .threads().executorService(customPool)
                    .bean("mySlowBean", "doSomething")
                    .to("mock:result");
            }
        };
    }

    class MySlowBean {
        public void doSomething(Exchange exchange) {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
            }
            Message message = exchange.getIn();
            log.info("Async task for {} done", message.getBody());
        }
    }
}


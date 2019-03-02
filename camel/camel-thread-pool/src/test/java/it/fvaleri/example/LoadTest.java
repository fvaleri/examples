package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class LoadTest extends CamelSpringTestSupport {
    private static final int REQUESTS = 3300;

    @Override
    protected int getShutdownTimeout() {
        return 60;
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("camel-context.xml");
    }

    @Test
    public void test() throws Exception {
        int totReq = 3 * REQUESTS;
        MockEndpoint out = getMockEndpoint("mock:result");
        out.expectedMessageCount(totReq);
        out.setResultWaitTime(120 * 1_000);

        // load test that you can observe from jconsole
        log.info("Starting load test...");
        long t = System.nanoTime();
        for (int i = 1; i <= REQUESTS; i++) {
            Thread thread = new MyThread(template);
            thread.setName("MyThread" + i);
            thread.start();
            if (i % 100 == 0) {
                long d = System.nanoTime() - t;
                log.info("Done {} requests in {} ms", i, d / 1e6);
            }
            Thread.sleep(100);
        }

        assertMockEndpointsSatisfied();
    }

    class MyThread extends Thread {
        private ProducerTemplate template;

        public MyThread(ProducerTemplate template) {
            this.template = template;
        }

        @Override
        public void run() {
            template.sendBody("direct://start", "foo");
        }
    }
}


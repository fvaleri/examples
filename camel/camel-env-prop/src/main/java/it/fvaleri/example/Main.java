package it.fvaleri.example;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static CountDownLatch latch = new CountDownLatch(1);
    private static org.apache.camel.main.Main camel = new org.apache.camel.main.Main();

    public static void main(String[] args) {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (camel != null && camel.isStarted()) {
                    LOG.info("Stopping Camel");
                    try {
                        latch.countDown();
                        camel.stop();
                    } catch (Throwable e) {
                    }
                }
            }));
            LOG.info("APP_HOME={}", System.getenv("APP_HOME"));

            camel.addRouteBuilder(new RouteBuilder() {
                @Override
                public void configure() {
                    getContext().disableJMX();
                    PropertiesComponent pc = new PropertiesComponent();
                    pc.setLocation("classpath:application.properties");
                    pc.setCache(false);
                    getContext().addComponent("properties", pc);

                    onException(Exception.class)
                            .handled(true)
                            .stop();

                    from("timer:foo?repeatCount=1&period=1")
                            .setBody(simple("${properties:keystore.location}"))
                            .log("${body}");
                }
            });

            camel.start();
            latch.await();
        } catch (Throwable e) {
            LOG.error("{}", e);
        }
    }
}


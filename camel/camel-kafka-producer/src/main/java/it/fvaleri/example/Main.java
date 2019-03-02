package it.fvaleri.example;

import org.apache.camel.builder.RouteBuilder;
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

            String accessToken = "invalid-or-expired-access-token";
            String jassConfig = "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required oauth.access.token=\"" + accessToken + "\";";

            camel.addRouteBuilder(new RouteBuilder() {
                @Override
                public void configure() {
                    onException(Exception.class)
                            .handled(true)
                            .stop();
                    
                    // OAuth token flow (we pass/reuse the access_token obtained by another component)
                    from("timer:foo?repeatCount=1&period=1")
                            .setBody().constant("test")
                            .to("kafka:my-topic?brokers=my-cluster-kafka-bootstrap-kafka-oauth.apps.cluster-7e3c.7e3c.example.opentlc.com:443" +
                                    "&securityProtocol=SASL_SSL&saslMechanism=OAUTHBEARER&saslJaasConfig=" + jassConfig +"&synchronous=true" +
                                    "&sslTruststoreLocation=/home/fvaleri/Documents/code/setup/tmp/03090000/test/truststore.jks&sslTruststorePassword=changeit" +
                                    "&additionalProperties.sasl.login.callback.handler.class=io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler")
                            .log("Record sent");

                }
            });

            camel.start();
            latch.await();
        } catch (Throwable e) {
            LOG.error("{}", e);
        }
    }
}


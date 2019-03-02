package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;

import java.util.Arrays;
import java.util.List;

import javax.jms.ConnectionFactory;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.component.jms.JmsComponent;
import org.junit.BeforeClass;
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
        return 10;
    }

    @BeforeClass
    public static void startEmbeddedArtemis() throws Exception {
        ActiveMQServer server = ActiveMQServers.newActiveMQServer(
            new ConfigurationImpl()
                .setPersistenceEnabled(true)
                .setJournalDirectory("target/data/journal")
                .setBindingsDirectory("target/data/bindigs")
                .setLargeMessagesDirectory("target/data/largemessages")
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("invm", "vm://0"));
        server.start();
    }


    @Test
    public void testRateLimiter() throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        List<String> messages = Arrays.asList("A", "B", "C", "D", "E", "F");
        messages.forEach(m -> template.sendBody("jms:queue:my-queue", m));
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                jmsComponentSetup(context);

                // using the Aggregate EIP we achieve at most X messages/s semantic
                // with Artemis broker, we can also use the consumerMaxRate URL parameter
                from("jms:queue:my-queue")
                    // simulate processing
                    .delay(100)
                    // aggregate every 5 messages or 1 second interval, whichever comes first
                    .aggregate(new GroupedExchangeAggregationStrategy())
                        .constant(true).completionSize(5).completionInterval(1000L)
                    .log("Messages: ${body}")
                    .to("mock:result");
            }
        };
    }

    private void jmsComponentSetup(CamelContext ctx) {
        // set client buffer to 0 for pulling messages instead of being pushed
        final String url = "vm://0?consumerWindowSize=0";
        final ConnectionFactory cf = new ActiveMQConnectionFactory(url);
        final JmsComponent comp = JmsComponent.jmsComponentAutoAcknowledge(cf);
        comp.setUsername("admin");
        comp.setPassword("admin");
        // use local transactions to not lose messages
        comp.setLazyCreateTransactionManager(false);
        comp.setTransacted(true);
        ctx.addComponent("jms", comp);
    }
}


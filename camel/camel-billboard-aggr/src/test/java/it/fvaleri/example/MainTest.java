package it.fvaleri.example;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

public class MainTest extends CamelTestSupport {
    private final static String BASE_PATH = System.getProperty("user.dir") + "/target/test-classes";

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
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();

        Map<String, Integer> top20 = ((MyAggregationStrategy)
            mock.getReceivedExchanges().get(0).getIn().getHeader("myAggregation")).getTop20Artists();
        top20.forEach((k,v) -> log.info("{}, {}", k, v));
        assertEquals(20, top20.size());
        assertEquals(35, (int) top20.get("madonna"));
        assertEquals(26, (int) top20.get("elton john"));
        assertEquals(17, (int) top20.get("the beatles"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:" + BASE_PATH + "?noop=true&idempotent=true")
                    .split(body().tokenize("\n")).streaming().parallelProcessing()
                        .choice().when(simple("${exchangeProperty.CamelSplitIndex} > 0"))
                            .doTry()
                                .unmarshal().bindy(BindyType.Csv, SongRecord.class)
                                .to("seda:aggregate")
                            .doCatch(Exception.class)
                                .setBody(simple("${exchangeProperty.CamelSplitIndex}:${body}"))
                                .transform(body().append("\n"))
                                .to("file:" + BASE_PATH + "?fileName=waste.log&fileExist=append")
                            .end();

                from("seda:aggregate?concurrentConsumers=10")
                    .bean(MyAggregationStrategy.class, "setArtistHeader")
                    .aggregate(new MyAggregationStrategy()).header("artist")
                        .completionPredicate(header("CamelSplitComplete").isEqualTo(true))
                    .to("mock:result");
            }
        };
    }
}


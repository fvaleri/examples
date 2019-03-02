package it.fvaleri.example;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;

import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;

public class XmlTokenizerTest extends CamelTestSupport {
    @BeforeClass
    public static void beforeClass() throws Exception {
        XmlBuilder.build();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.disableJMX();
        return ctx;
    }

    @Override
    protected int getShutdownTimeout() {
        return 300;
    }

    @Test
    public void test() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(XmlBuilder.getNumOfRecords()).create();
        boolean matches = notify.matches(5_000, TimeUnit.MILLISECONDS);
        log.info("Processed XML file with {} records", XmlBuilder.getNumOfRecords());
        assertTrue("Test completed", matches);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:" + XmlBuilder.getBasePath() + "?readLock=changed&noop=true")
                    .split(body().tokenizeXML("record", "records")).streaming().stopOnException()
                        .log(LoggingLevel.TRACE, "it.fvaleri", "${body}")
                        .to("log:it.fvaleri?level=DEBUG&groupInterval=100&groupDelay=100&groupActiveOnly=false")
                    .end();
            }
        };
    }
}


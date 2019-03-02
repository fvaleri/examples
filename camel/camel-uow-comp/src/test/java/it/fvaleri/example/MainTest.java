package it.fvaleri.example;

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * A file is not a transactional resources so we must compensate in case of error.
 * A UnitOfWOrk is a batch of tasks handled as a single coherent unit. An Exchange
 * has one UnitOfWork, which in turn has from zero to many Synchronizations.
 */
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
    public void setUp() throws Exception {
        deleteDirectory("target/mail/backup");
        super.setUp();
    }

    @Test
    public void testCommit() throws Exception {
        template.sendBodyAndHeader("direct:confirm", "engine", "to", "foo@example.org");
        File file = new File("target/mail/backup/");
        String[] files = file.list();
        assertEquals("There should be one file", 1, files.length);
    }

    @Test
    public void testRollback() throws Exception {
        try {
            template.sendBodyAndHeader("direct:confirm", "bumper", "to", "FATAL");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Simulated fatal error", e);
        }
        File file = new File("target/mail/backup/");
        String[] files = file.list();
        assertEquals("There should be no files", 0, files.length);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:confirm")
                    // adding the FileRollback Synchronization to UOW
                    .onCompletion().onFailureOnly().onWhen(header(Exchange.FILE_NAME_PRODUCED))
                        .bean(FileRollback.class, "onFailure")
                    .end()
                    .bean(OrderService.class, "createMail")
                    .log("Saving mail backup file")
                    .to("file:target/mail/backup")
                    .log("Trying to send mail to ${header.to}")
                    .bean(OrderService.class, "sendMail")
                    .log("Mail sent to ${header.to}");
            }
        };
    }
}


package it.fvaleri.example;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class MainTest extends CamelSpringTestSupport {
    private JdbcTemplate jdbcTemplate;

    @Before
    public void setupDatabase() {
        DataSource ds = context.getRegistry().lookupByNameAndType("myDataSource", DataSource.class);
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("create table partner_metric (partner_id varchar(10), " +
                "time_occurred varchar(20), status_code varchar(3), perf_time varchar(10))");
    }

    @After
    public void dropDatabase() {
        if (jdbcTemplate != null) {
            jdbcTemplate.execute("drop table partner_metric");
        }
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("spring-camel.xml");
    }

    @Test
    public void allSystemsWorkingFine() {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();

        int rows = jdbcTemplate.queryForObject("select count(1) from partner_metric", Integer.class);
        assertEquals(0, rows);

        String xml = "<partner id=\"123\"><date>201702250815</date><code>200</code><time>4387</time></partner>";
        template.sendBody("jms:queue:partners", xml);

        assertTrue(notify.matches(10, TimeUnit.SECONDS));

        rows = jdbcTemplate.queryForObject("select count(*) from partner_metric", Integer.class);
        assertEquals(1, rows);
    }

    @Test
    public void databaseConnectionFailure() throws Exception {
        // database connection fails and ActiveMQ redelivers the message up to 6 times before moving it to DLQ
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1 + 6).create();

        // simulating a database connection error by using a route interceptor
        context.getRouteDefinition("partnerToDB").adviceWith(context, new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("sql:*")
                        .skipSendToOriginalEndpoint()
                        .throwException(new ConnectException("Database connection failed"));
            }
        });

        int rows = jdbcTemplate.queryForObject("select count(*) from partner_metric", Integer.class);
        assertEquals(0, rows);

        String xml = "<partner id=\"123\"><date>201702250815</date><code>200</code><time>4387</time></partner>";
        template.sendBody("jms:queue:partners", xml);

        assertTrue(notify.matches(15, TimeUnit.SECONDS));

        rows = jdbcTemplate.queryForObject("select count(*) from partner_metric", Integer.class);
        assertEquals(0, rows);

        Object body = consumer.receiveBody("jms:queue:ActiveMQ.DLQ", 5_000);
        assertNotNull("The message was not in the DLQ", body);
    }

    @Test
    public void databaseConnectionRecovery() throws Exception {
        // database connection fails and then recovers on first redelivery attempt
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1 + 1).create();

        // simulating a database connection error by using a route interceptor
        context.getRouteDefinition("partnerToDB").adviceWith(context, new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("sql:*")
                        .choice()
                        .when(header("JMSRedelivered").isEqualTo("false"))
                        .throwException(new ConnectException("Database connection failed"))
                        .end();
            }
        });

        // there should be 0 row in the database when we start
        int rows = jdbcTemplate.queryForObject("select count(*) from partner_metric", Integer.class);
        assertEquals(0, rows);

        String xml = "<?xml version=\"1.0\"?><partner id=\"123\"><date>201702250815</date><code>200</code><time>4387</time></partner>";
        template.sendBody("jms:queue:partners", xml);

        assertTrue(notify.matches(10, TimeUnit.SECONDS));

        rows = jdbcTemplate.queryForObject("select count(*) from partner_metric", Integer.class);
        assertEquals(1, rows);

        String dlq = consumer.receiveBody("jms:queue:ActiveMQ.DLQ", 1000L, String.class);
        assertNull("The message was in the DLQ", dlq);
    }
}


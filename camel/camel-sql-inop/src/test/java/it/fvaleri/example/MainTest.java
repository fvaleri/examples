package it.fvaleri.example;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

public class MainTest extends CamelSpringTestSupport {
    private JdbcTemplate jdbc;

    @Override
    protected int getShutdownTimeout() {
        return 60;
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("camel-context.xml");
    }

    @Before
    public void before() throws Exception {
        DataSource ds = context.getRegistry().lookupByNameAndType("myDataSource", DataSource.class);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table partner_metric (partner_id varchar(10), time_occurred varchar(20), status_code varchar(3), perf_time varchar(10))");
        jdbc.execute("insert into partner_metric (partner_id, time_occurred, status_code, perf_time) values ('1', '20170315183457', '200', '1503')");
        jdbc.execute("insert into partner_metric (partner_id, time_occurred, status_code, perf_time) values ('1', '20170315183558', '500', '1707')");
        jdbc.execute("insert into partner_metric (partner_id, time_occurred, status_code, perf_time) values ('2', '20170315183782', '200', '1255')");
        jdbc.execute("insert into partner_metric (partner_id, time_occurred, status_code, perf_time) values ('1', '20170315183782', '404', '1255')");
    }

    @After
    public void after() throws Exception {
        if (jdbc != null) {
            jdbc.execute("drop table partner_metric");
        }
    }

    @Test
    public void test() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        template.sendBody("direct://start", "foo");
        assertMockEndpointsSatisfied();
        context.stop();
    }
}


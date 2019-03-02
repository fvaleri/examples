package it.fvaleri.example;

import static org.apache.camel.LoggingLevel.DEBUG;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpComponent;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.KeyManagersParameters;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.camel.util.jsse.SSLContextParameters;
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
        return 60;
    }

    @Test
    public void test() throws Exception {
        getMockEndpoint("mock:output").expectedMessageCount(1);
        Map<String, Object> headers = new HashMap<>();
        headers.put("targetUrl", "https4://raw.githubusercontent.com/apache/camel/master/apache-camel");
        headers.put("fileName", "pom.xml");
        template.sendBodyAndHeaders("direct:input", "test", headers);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                KeyStoreParameters ksp = new KeyStoreParameters();
                ksp.setResource("/tmp/truststore.jks");
                ksp.setPassword("changeit");

                KeyManagersParameters kmp = new KeyManagersParameters();
                kmp.setKeyStore(ksp);
                kmp.setKeyPassword("changeit");

                SSLContextParameters scp = new SSLContextParameters();
                scp.setKeyManagers(kmp);

                HttpComponent httpComponent = getContext().getComponent("https4", HttpComponent.class);
                httpComponent.setSslContextParameters(scp);

                from("direct:input")
                    .log(DEBUG, getClass().getName(), "Input params: ${headers}")
                    .toD("${header.targetUrl}/${header.fileName}?sslContextParametersRef=sslContextParameters")
                    .log(DEBUG, getClass().getName(), "File content:\n${body}")
                    .to("mock:output");
            }
        };
    }
}


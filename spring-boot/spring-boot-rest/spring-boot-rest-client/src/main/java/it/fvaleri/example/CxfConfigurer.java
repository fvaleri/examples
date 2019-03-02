package it.fvaleri.example;

import org.apache.camel.component.cxf.jaxrs.CxfRsEndpointConfigurer;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.AbstractJAXRSFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.springframework.stereotype.Component;
import org.apache.cxf.jaxrs.client.Client;

@Component
public class CxfConfigurer implements CxfRsEndpointConfigurer {
    @Override
    public void configure(AbstractJAXRSFactoryBean abstractJAXRSFactoryBean) {
    }

    @Override
    public void configureClient(Client client) {
        HTTPConduit conduit = (HTTPConduit) WebClient.getConfig(client).getConduit();
        HTTPClientPolicy policy = new HTTPClientPolicy();
        // client can't wait more than X seconds
        policy.setReceiveTimeout(2_000);
        conduit.setClient(policy);
    }

    public void configureServer(Server server) {
        // do nothing here
    }
}


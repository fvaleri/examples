package it.fvaleri.example;

import javax.xml.namespace.QName;

import org.apache.camel.component.cxf.CxfEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.SimplePortType;

@Configuration
public class MyBeans {
    @Bean
    public CxfEndpoint simpleEndpoint() {
        CxfEndpoint endpoint = new CxfEndpoint();
        endpoint.setServiceName(new QName("http://example.com", "simple"));
        endpoint.setServiceClass(SimplePortType.class);
        return endpoint;
    }
}


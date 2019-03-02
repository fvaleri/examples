package it.fvaleri.example;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.core.JsonParseException;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;

@ApplicationScoped
@ContextName("eap-rest-service")
public class CamelRoutes extends RouteBuilder {
    @Override
    public void configure() {
        onException(JsonParseException.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.TEXT_PLAIN))
            .setBody().constant("Invalid json data");

        restConfiguration()
            .bindingMode(RestBindingMode.json)
            .component("undertow")
            .contextPath("/api")
            .host("localhost")
            .port(8080)
            .enableCORS(true)
            .apiProperty("api.title", "WildFly Camel REST API")
            .apiProperty("api.version", "1.0")
            .apiContextPath("swagger");

        rest("/customers").description("Customers REST service")
            .get()
                .description("Retrieves all customers")
                .produces(MediaType.APPLICATION_JSON)
                .route()
                    .bean(CustomerService.class, "findAll")
                .endRest()

            .get("/{id}")
                .description("Retrieves a customer for the specified id")
                .param()
                    .name("id")
                    .description("Customer ID")
                    .type(RestParamType.path)
                    .dataType("int")
                .endParam()
                .produces(MediaType.APPLICATION_JSON)
                .route()
                    .bean(CustomerService.class, "findById")
                .endRest()

            .post()
                .description("Creates a new customer")
                .consumes(MediaType.APPLICATION_JSON)
                .produces(MediaType.APPLICATION_JSON)
                .type(Customer.class)
                .route()
                    .bean(CustomerService.class, "create")
                .endRest()

            .put("/{id}")
                .description("Updates the customer relating to the specified id")
                .param()
                    .name("id")
                    .description("Customer ID")
                    .type(RestParamType.path)
                    .dataType("int")
                .endParam()
                .consumes(MediaType.APPLICATION_JSON)
                .type(Customer.class)
                .route()
                    .bean(CustomerService.class, "update")
                .endRest()

            .delete("/{id}")
                .description("Deletes the customer relating to the specified id")
                .param()
                    .name("id")
                    .description("Customer ID")
                    .type(RestParamType.path)
                    .dataType("int")
                .endParam()
                .route()
                    .bean(CustomerService.class, "delete")
                .endRest();
    }
}


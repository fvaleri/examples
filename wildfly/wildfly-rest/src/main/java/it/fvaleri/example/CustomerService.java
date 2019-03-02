package it.fvaleri.example;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.Exchange;

@ApplicationScoped
@Named
public class CustomerService {
    @Inject
    CustomerRepository customerRepository;

    public void findAll(Exchange exchange) {
        List<Customer> customers = customerRepository.findAll();
        exchange.getOut().setBody(customers);
    }

    public void findById(Exchange exchange) {
        Long id = exchange.getIn().getHeader("id", Long.class);
        Customer customer = customerRepository.findById(id);
        if (customer != null) {
            exchange.getOut().setBody(customer);
        } else {
            exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        }
    }

    public void create(Exchange exchange) {
        Customer customer = customerRepository.save(exchange.getIn().getBody(Customer.class));
        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 201);
        String URL = exchange.getIn().getHeader(Exchange.HTTP_URL, String.class);
        exchange.getOut().setHeader("Location", URL + "/" + customer.getId());
        exchange.getOut().setBody(customer);
    }

    public void update(Exchange exchange) {
        Long id = exchange.getIn().getHeader("id", Long.class);
        Customer customer = customerRepository.findById(id);
        if (customer != null) {
            Customer updatedCustomer = customerRepository.save(exchange.getIn().getBody(Customer.class));
            String URL = exchange.getIn().getHeader(Exchange.HTTP_URL, String.class);
            exchange.getOut().setHeader("Location", URL + "/" + updatedCustomer.getId());
            exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
            exchange.getOut().setBody(updatedCustomer);
        } else {
            exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        }
    }

    public void delete(Exchange exchange) {
        Long id = exchange.getIn().getHeader("id", Long.class);
        Customer customer = customerRepository.findById(id);
        if (customer != null) {
            customerRepository.delete(customer);
            exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
        } else {
            exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
        }
    }
}


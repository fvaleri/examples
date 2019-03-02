package it.fvaleri.example;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.jaxrs.PathParam;

@Path("/api/greet")
public class GreetingResource {
    private static final Logger LOG = Logger.getLogger(GreetingResource.class);

    @Inject
    GreetingService service;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Greeting hello() {
        LOG.info("Got new request from anonymous");
        return new Greeting("hello");
    }

    @GET
    @Path("/{name}")
    public Greeting greeting(@PathParam("name") String name) {
        LOG.info("Got new request from " + name);
        return service.getGreeting(name);
    }
}

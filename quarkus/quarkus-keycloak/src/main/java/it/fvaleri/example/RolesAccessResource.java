package it.fvaleri.example;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/api")
public class RolesAccessResource {
    @GET
    @Path("/admin")
    @RolesAllowed("admin")
    @Produces(MediaType.TEXT_PLAIN)
    public String roleAdmin() {
        return "Admin role granted";
    }

    @GET
    @Path("/user")
    @RolesAllowed("user")
    @Produces(MediaType.TEXT_PLAIN)
    public String roleUser() {
        return "User role granted";
    }
}

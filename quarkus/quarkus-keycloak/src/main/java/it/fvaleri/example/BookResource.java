package it.fvaleri.example;

import java.util.List;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/api/books")
public class BookResource {
    @Inject
    RolesAccessResource accessResource;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<BookEntity> books() {
        List<BookEntity> books = null;
        if (accessResource.roleUser() != null) {
            books = BookEntity.listAll();
        }
        return books;
    }

    @Transactional
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response newBook(BookEntity book) {
        Response response = null;
        if (accessResource.roleAdmin() != null) {
            book.id = null;
            book.persist();
            response = Response
                    .status(Response.Status.CREATED)
                    .entity(book)
                    .build();
        }
        return response;
    }
}

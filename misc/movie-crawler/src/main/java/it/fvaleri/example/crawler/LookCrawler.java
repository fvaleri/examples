package it.fvaleri.example.crawler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import it.fvaleri.example.FileCache;
import it.fvaleri.example.model.Movie;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LookCrawler {
    private final static String LOOK_SEARCH_URL = "https://lookmovie2.to/api/v1/movies/do-search?q=";
    private final static String LOOK_VIEW_URL = "https://lookmovie2.to/movies/view/";
    private final static Path LOOK_CACHE_PATH = Path.of(System.getProperty("user.home") + "/.movie-crawler/look-movies.json");

    private WebClient client;
    private FileCache cache;
    private Set<Movie> movies;

    public LookCrawler(WebClient client, FileCache cache, Set<Movie> movies) {
        this.client = client;
        this.cache = cache;
        this.movies = movies;
    }

    public String findViewUrl() throws IOException {
        Set<Movie> viewed = cache.read(LOOK_CACHE_PATH);
        Set<Movie> diff = movies.stream()
                .filter(m -> !viewed.contains(m))
                .collect(Collectors.toSet());
        int attemptsLeft = 30;
        String slug = null;
        while (slug == null && attemptsLeft-- > 0) {
            Movie selectedMovie = getRandomMovie(diff).get();
            //System.out.println(selectedMovie);
            Page page = client.getPage(LOOK_SEARCH_URL
                    + URLEncoder.encode(selectedMovie.title(), StandardCharsets.UTF_8));
            WebResponse response = page.getWebResponse();
            if (response == null || !response.getContentType().equals("application/json")) {
                throw new RuntimeException("Invalid content type");
            }
            String searchResultJson = response.getContentAsString();
            slug = extractSlug(viewed, selectedMovie, searchResultJson);
            viewed.add(selectedMovie);
        }
        if (slug == null || slug.isEmpty()) {
            throw new RemoteException("No movie found");
        }
        cache.write(viewed, LOOK_CACHE_PATH);
        return LOOK_VIEW_URL + slug;
    }

    private Optional<Movie> getRandomMovie(Set<Movie> movies) {
        return movies.stream().findAny();
    }

    private String extractSlug(Set<Movie> watchedMovies,
                               Movie selectedMovie,
                               String searchResultJson) throws JsonProcessingException {
        JsonNode node = new ObjectMapper().readTree(searchResultJson);
        // results are ordered by year desc
        JsonNode titleNode = node.at("/result/0/title");
        if (titleNode != null
                && titleNode.asText().equalsIgnoreCase(selectedMovie.title())
                && !watchedMovies.contains(selectedMovie)) {
            return node.at("/result/0/slug").asText().trim();
        }
        return null;
    }
}

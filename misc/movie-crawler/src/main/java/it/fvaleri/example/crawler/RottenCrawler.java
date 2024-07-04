package it.fvaleri.example.crawler;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import it.fvaleri.example.FileCache;
import it.fvaleri.example.model.Movie;

import java.io.IOException;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

public class RottenCrawler {
    private final static String ROTTEN_SEARCH_URL = "https://www.rottentomatoes.com/browse/movies_at_home/critics:certified_fresh~sort:newest?page=100";
    private final static Path ROTTEN_CACHE_PATH = Path.of(System.getProperty("user.home") + "/.movie-crawler/rotten-movies.json");

    private WebClient client;
    private FileCache cache;

    public RottenCrawler(WebClient client, FileCache cache) {
        this.client = client;
        this.cache = cache;
    }

    public Set<Movie> findCertifiedFreshMovies() throws IOException {
        Set<Movie> movies = cache.read(ROTTEN_CACHE_PATH);
        if (movies.isEmpty()) {
            HtmlPage page = client.getPage(ROTTEN_SEARCH_URL);
            List<HtmlElement> htmlElementList = page.getByXPath("//div[@data-track='scores']");
            if (htmlElementList.isEmpty()) {
                new RuntimeException("No movie found");
            } else {
                for (HtmlElement htmlElement : htmlElementList) {
                    String title = ((HtmlElement) htmlElement.getFirstByXPath("./span[@class='p--small']")).asNormalizedText();
                    HtmlElement scorePairsElement = htmlElement.getFirstByXPath("./score-pairs-deprecated");
                    if (title != null && scorePairsElement != null) {
                        String criticsScoreAttribute = scorePairsElement.getAttribute("criticsscore");
                        String audienceScoreAttribute = scorePairsElement.getAttribute("audiencescore");
                        if (isInteger(criticsScoreAttribute) && isInteger(audienceScoreAttribute)) {
                            movies.add(new Movie(title.trim(),
                                    Integer.parseInt(criticsScoreAttribute),
                                    Integer.parseInt(audienceScoreAttribute)));
                        }
                    }
                }
            }
            cache.write(movies, ROTTEN_CACHE_PATH);
        }
        if (movies.isEmpty()) {
            throw new RemoteException("No movie found, the page template may have changed");
        }
        return movies;
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

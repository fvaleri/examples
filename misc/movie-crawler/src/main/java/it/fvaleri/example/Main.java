package it.fvaleri.example;

import com.gargoylesoftware.htmlunit.WebClient;
import it.fvaleri.example.crawler.LookCrawler;
import it.fvaleri.example.crawler.RottenCrawler;
import it.fvaleri.example.model.Movie;

import java.util.Set;

public class Main {
    static {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("java.security.egd", "file:/dev/./urandom");
    }

    public static void main(String[] args) {
        new Main().start();
    }

    private void start() {
        try (WebClient client = crateWebClient()) {
            FileCache cache = new FileCache();
            RottenCrawler rottenCrawler = new RottenCrawler(client, cache);
            Set<Movie> movies = rottenCrawler.findCertifiedFreshMovies();
            LookCrawler lookCrawler = new LookCrawler(client, cache, movies);
            String viewURL = lookCrawler.findViewUrl();
            System.out.println(viewURL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static WebClient crateWebClient() {
        WebClient client = new WebClient();
        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(false);
        client.getOptions().setTimeout(120_000);
        return client;
    }
}

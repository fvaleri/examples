package it.fvaleri.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

public class Utils {
    private static final Random RANDOM = new Random();

    private Utils() {
    }

    public static String readFile(String fileName) {
        var lines = new StringBuilder();
        var fileURL = Utils.class.getClassLoader().getResource(fileName);
        if (fileURL != null) {
            try (Scanner file = new Scanner(new File(fileURL.getFile()))) {
                while (file.hasNextLine()) {
                    lines.append(file.nextLine()).append(" ");
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return lines.toString();
    }

    public static String fetchLatestNewsItemFromNewYorkTimes(String apiKey) {
        var latestNews = new StringBuilder();
        var httpClient = new OkHttpClient();
        var newsItemIndex = RANDOM.nextInt(11);
        var request = new Request.Builder()
                .url(String.format("https://api.nytimes.com/svc/topstories/v2/technology.json?api-key=%s", apiKey))
                .build();
        try {
            var response = httpClient.newCall(request).execute();
            NewsItems newsItems = new ObjectMapper().readValue(response.body().byteStream(), NewsItems.class);
            if (newsItemsAreAvailable(newsItems)) {
                latestNews.append(String.format("%s - %s",
                            newsItems.results.get(newsItemIndex).title,
                            newsItems.results.get(newsItemIndex).byLine));
            }
        } catch (IOException e) {
            latestNews.append("Failed to get latest news");
            e.printStackTrace();
        }
        return latestNews.toString();
    }

    public static String generateUniqueEventKey(String humanReadableEventKey, int eventCount) {
        return String.format("%s-%s", humanReadableEventKey, eventCount);
    }

    public static boolean newsItemsAreAvailable(NewsItems newsItems) {
        return (newsItems != null && newsItems.results != null && !newsItems.results.isEmpty());
    }

    public static boolean isAsynchronous(String operationType) {
        return operationType.equals("2");
    }

    public static boolean userHasNotChosenToExit(String userChoice) {
        return !userChoice.equals("5");
    }
}

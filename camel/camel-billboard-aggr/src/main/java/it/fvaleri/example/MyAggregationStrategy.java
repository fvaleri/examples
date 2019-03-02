package it.fvaleri.example;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

public class MyAggregationStrategy implements AggregationStrategy {
    private static Map<String, Integer> map = new ConcurrentHashMap<String, Integer>();

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Message newIn = newExchange.getIn();
        String artist = (String) newIn.getHeader("artist");
        if (map.containsKey(artist)) {
            map.put(artist, map.get(artist) + 1);
        } else {
            map.put(artist, 1);
        }
        newIn.setHeader("myAggregation", this);
        return newExchange;
    }

    public void setArtistHeader(Exchange exchange, SongRecord song) {
        exchange.getMessage().setHeader("artist", song.getArtist());
    }

    public Map<String, Integer> getTop20Artists() {
        return map.entrySet()
            .stream()
            .sorted((Map.Entry.<String, Integer>comparingByValue().reversed()))
            .limit(20)
            .collect(Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }
}


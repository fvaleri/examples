package it.fvaleri.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import it.fvaleri.example.model.Movie;

public class FileCacheTest {
    private FileCache cache = new FileCache();

    @TempDir
    static Path tempDir;

    @Test
    void readCache() throws IOException {
        Path path = Path.of(tempDir.toString() + "/read.json");
        String content = "[{\"title\":\"Neptune Frost\",\"criticsScore\":96,\"audienceScore\":68}," +
                "{\"title\":\"Zola\",\"criticsScore\":88,\"audienceScore\":68}," +
                "{\"title\":\"All My Friends Hate Me\",\"criticsScore\":89,\"audienceScore\":66}]";
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        Set<Movie> movies = cache.read(path);
        assertEquals(3, movies.size());
    }

    @Test
    void writeCache() throws IOException {
        Path path = Path.of(tempDir.toString() + "/write.json");
        Set<Movie> movies = Set.of(new Movie("Neptune Frost", 96, 68));
        assertFalse(Files.exists(path));
        cache.write(movies, path);
        assertTrue(Files.exists(path));
    }
}

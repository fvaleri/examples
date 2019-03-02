package it.fvaleri.example;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcQueryableStorageIT {
    static Connection conn;
    static AtomicLong keys;

    @BeforeAll
    static void beforeAll() throws SQLException {
        String url = "jdbc:h2:mem:test;" +
            "INIT=DROP TABLE IF EXISTS NOTES\\;" +
            "CREATE TABLE NOTES(NO_ID BIGINT PRIMARY KEY, NO_TEXT VARCHAR)";
        conn = DriverManager.getConnection(url);
        keys = new AtomicLong(0);
    }

    @AfterAll
    static void afterAll() throws SQLException {
        conn.close();
    }

    @Test
    void shouldExecuteCrudOperations() {
        long key1 = keys.incrementAndGet();
        String value1 = randomUUID().toString();
        long key2 = keys.incrementAndGet();
        String value2 = randomUUID().toString();
        long key3 = keys.incrementAndGet();
        String value3 = randomUUID().toString();

        Properties queries = new Properties();
        queries.put("notes.insert", "INSERT INTO NOTES (NO_ID, NO_TEXT) VALUES (?, ?)");
        queries.put("notes.select.all", "SELECT NO_TEXT FROM NOTES WHERE NO_ID IN (?, ?, ?)");
        queries.put("notes.select", "SELECT NO_TEXT FROM NOTES WHERE NO_ID = ?");
        queries.put("notes.update", "UPDATE NOTES SET NO_TEXT = ? WHERE NO_ID = ?");
        queries.put("notes.delete", "DELETE FROM NOTES WHERE NO_ID = ?");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        // insert some data
        assertEquals(1, storage.write("notes.insert", List.of(key1, value1)));
        assertEquals(1, storage.write("notes.insert", List.of(key2, value2)));
        assertEquals(1, storage.write("notes.insert", List.of(key3, value3)));

        // check they are there
        List<String> result1 = new ArrayList<>();
        List<QueryableStorage.Row> rows1 = storage.read("notes.select.all", List.of(String.class), List.of(key1, key2, key3));
        rows1.forEach(row -> result1.add((String) row.columns().get(0)));
        assertTrue(result1.containsAll(List.of(value1, value2, value3)));

        // update one row
        assertEquals(1, storage.write("notes.update", List.of(value1, key3)));

        // check it was updated
        assertEquals(value1, storage.read("notes.select", List.of(String.class), List.of(key3)).get(0).columns().get(0));

        // delete one row
        assertEquals(1, storage.write("notes.delete", List.of(key2)));

        // check it was deleted
        assertTrue(storage.read("notes.select", List.of(String.class), List.of(key2)).isEmpty());

        storage.close();
    }

    @Test
    void shouldFailWithInvalidQueryParams() {
        long key = keys.incrementAndGet();

        Properties queries = new Properties();
        queries.put("notes.insert", "INSERT INTO NOTES (NO_ID, NO_TEXT) VALUES (?, ?)");
        queries.put("notes.select", "SELECT NO_TEXT FROM NOTES WHERE NO_ID = ? AND NO_TEXT = ?");
        queries.put("notes.update", "UPDATE NOTES SET NO_TEXT = ? WHERE NO_ID = ? AND NO_TEXT = ?");
        queries.put("notes.delete", "DELETE FROM NOTES WHERE NO_ID = ? AND NO_TEXT = ?");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        Exception e1 = assertThrows(RuntimeException.class, () -> storage.write("notes.insert", null));
        assertTrue(e1.getMessage().contains("Query notes.insert failed"));

        Exception e2 = assertThrows(RuntimeException.class, () -> storage.write("notes.insert", List.of()));
        assertTrue(e2.getMessage().contains("Query notes.insert failed"));

        Exception e3 = assertThrows(RuntimeException.class, () -> storage.write("notes.insert", List.of(key)));
        assertTrue(e3.getMessage().contains("Query notes.insert failed"));

        Exception e4 = assertThrows(RuntimeException.class, () -> storage.read("notes.select", List.of(String.class), null));
        assertTrue(e4.getMessage().contains("Query notes.select failed"));

        Exception e5 = assertThrows(RuntimeException.class, () -> storage.read("notes.select", List.of(String.class)));
        assertTrue(e5.getMessage().contains("Query notes.select failed"));

        Exception e6 = assertThrows(RuntimeException.class, () -> storage.write("notes.update", null));
        assertTrue(e6.getMessage().contains("Query notes.update failed"));

        Exception e7 = assertThrows(RuntimeException.class, () -> storage.write("notes.update", List.of()));
        assertTrue(e7.getMessage().contains("Query notes.update failed"));

        Exception e8 = assertThrows(RuntimeException.class, () -> storage.write("notes.update", List.of(key)));
        assertTrue(e8.getMessage().contains("Query notes.update failed"));

        Exception e9 = assertThrows(RuntimeException.class, () -> storage.write("notes.delete", null));
        assertTrue(e9.getMessage().contains("Query notes.delete failed"));

        Exception e10 = assertThrows(RuntimeException.class, () -> storage.write("notes.delete", List.of()));
        assertTrue(e10.getMessage().contains("Query notes.delete failed"));

        Exception e11 = assertThrows(RuntimeException.class, () -> storage.write("notes.delete", List.of(key)));
        assertTrue(e11.getMessage().contains("Query notes.delete failed"));

        storage.close();
    }

    @Test
    void shouldWriteAndReadNullParameter() {
        long key = keys.incrementAndGet();
        String value = randomUUID().toString();

        Properties queries = new Properties();
        queries.put("notes.insert", "INSERT INTO NOTES (NO_ID, NO_TEXT) VALUES (?, ?)");
        queries.put("notes.select", "SELECT NO_TEXT FROM NOTES WHERE NO_ID = ?");
        queries.put("notes.update", "UPDATE NOTES SET NO_TEXT = ? WHERE NO_ID = ?");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        assertEquals(1, storage.write("notes.insert", List.of(key, value)));
        assertEquals(value, storage.read("notes.select", List.of(String.class), List.of(key)).get(0).columns().get(0));

        assertEquals(1, storage.write("notes.update", Arrays.asList(null, key)));
        assertNull(storage.read("notes.select", List.of(String.class), List.of(key)).get(0).columns().get(0));

        storage.close();
    }

    @Test
    void shouldFailWithInvalidQuery() {
        Properties queries1 = new Properties();
        queries1.put("notes.insert.invalid", "INSERT INTO FOO (ID, NAME) VALUES (?, ?)");
        Exception e1 = assertThrows(RuntimeException.class, () -> new JdbcQueryableStorage(conn, queries1));
        assertTrue(e1.getMessage().contains("Init error"));

        Properties queries2 = new Properties();
        queries2.put("notes.select.invalid", "SELECT VALUE FROM NOTES WHERE NO_ID = ?");
        Exception e2 = assertThrows(RuntimeException.class, () -> new JdbcQueryableStorage(conn, queries2));
        assertTrue(e2.getMessage().contains("Init error"));
    }

    @Test
    void shouldExecuteBatchWrites() {
        int numOfBatches = 2;
        int batchSize = 100;

        for (int i = 0; i < numOfBatches; i++) {
            List<Long> batchKeys = IntStream.range(0, batchSize)
                .mapToObj(n -> keys.incrementAndGet()).collect(Collectors.toList());
            List<String> batchValues = IntStream.range(0, batchSize)
                .mapToObj(n -> randomUUID().toString()).collect(Collectors.toList());

            Properties queries = new Properties();
            queries.put("notes.insert", "INSERT INTO NOTES (NO_ID, NO_TEXT) VALUES (?, ?)");
            QueryableStorage dataStore = new JdbcQueryableStorage(conn, queries);

            for (int j = 0; j < batchSize - 1; j++) {
                assertEquals(0, dataStore.write("notes.insert", List.of(batchKeys.get(j), batchValues.get(j)), batchSize));
            }
            assertEquals(batchSize, dataStore.write("notes.insert", List.of(batchKeys.get(batchSize - 1), batchValues.get(batchSize -1 )), batchSize));

            dataStore.close();
        }
    }
}

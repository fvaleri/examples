package it.fvaleri.example;

import it.fvaleri.example.QueryableStorage.Row;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JdbcQueryableStorageTest {
    @Test
    void shouldReadUsingExistingQueryWithParams() throws SQLException {
        List<String> data = List.of("v1", "v2", "v3");

        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);
        when(prepStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getObject(anyInt(), any(Class.class))).thenReturn(data.get(1));

        Properties queries = new Properties();
        queries.put("read", "valid SQL query");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        assertEquals("v2", storage.read("read", List.of(String.class), List.of("k2")).get(0).columns().get(0));

        storage.close();
    }

    @Test
    void shouldReadUsingExistingQueryWithoutParams() throws SQLException {
        List<String> data = List.of("v1", "v2", "v3");

        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);
        when(prepStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
        when(resultSet.getObject(anyInt(), any(Class.class))).thenReturn(data.get(0)).thenReturn(data.get(1)).thenReturn(data.get(2));

        Properties queries = new Properties();
        queries.put("read", "valid SQL query");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        List<Row> rows = storage.read("read", List.of(String.class));
        List<String> result = rows.stream().map(row -> (String) row.columns().get(0)).collect(Collectors.toList());
        assertEquals(data,  result);

        storage.close();
    }

    @Test
    void shouldWriteUsingExistingQuery() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);
        when(prepStmt.executeUpdate()).thenReturn(1);

        Properties queries = new Properties();
        queries.put("write", "valid SQL query");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        assertEquals(1, storage.write("write", List.of("foo", "bar")));

        storage.close();
    }

    @Test
    void shouldReturnEmptyListWhenNoResult() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);
        when(prepStmt.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Properties queries = new Properties();
        queries.put("read", "valid SQL query");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        List<Row> rows = storage.read("read", List.of(String.class));
        assertTrue(rows.isEmpty());

        storage.close();
    }

    @Test
    void shouldFailWhenConnectionIsNullOrClosed() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);

        Properties queries = new Properties();
        queries.put("read", "valid SQL query");

        Exception e1 = assertThrows(IllegalArgumentException.class, () -> new JdbcQueryableStorage(null, queries));
        assertEquals("Invalid connection", e1.getMessage());

        when(conn.isClosed()).thenReturn(true);
        Exception e2 = assertThrows(IllegalArgumentException.class, () -> new JdbcQueryableStorage(conn, queries));
        assertEquals("Invalid connection", e2.getMessage());
    }

    @Test
    void shouldFailWhenPropertiesAreNullOrEmpty() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);

        Exception e1 = assertThrows(IllegalArgumentException.class, () -> new JdbcQueryableStorage(conn, null));
        assertEquals("Invalid queries", e1.getMessage());

        Exception e2 = assertThrows(IllegalArgumentException.class, () -> new JdbcQueryableStorage(conn, new Properties()));
        assertEquals("Invalid queries", e2.getMessage());
    }

    @Test
    void shouldFailWhenQueryNameIsNullOrEmpty() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);

        Properties queries = new Properties();
        queries.put("read", "valid SQL query");
        queries.put("write", "valid SQL query");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        Exception e1 = assertThrows(IllegalArgumentException.class, () -> storage.read(null, List.of(String.class)));
        assertEquals("Invalid query name", e1.getMessage());

        Exception e2 = assertThrows(IllegalArgumentException.class, () -> storage.read("", List.of(String.class)));
        assertEquals("Invalid query name", e2.getMessage());

        Exception e3 = assertThrows(IllegalArgumentException.class, () -> storage.write(null, List.of("foo", "bar")));
        assertEquals("Invalid query name", e3.getMessage());

        Exception e4 = assertThrows(IllegalArgumentException.class, () -> storage.write("", List.of("foo", "bar")));
        assertEquals("Invalid query name", e4.getMessage());

        storage.close();
    }

    @Test
    void shouldFailWhenReadColumnTypesAreNullOrEmpty() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);

        Properties queries = new Properties();
        queries.put("read", "valid SQL query");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        Exception e1 = assertThrows(IllegalArgumentException.class, () -> storage.read("read", null));
        assertEquals("Invalid column types", e1.getMessage());

        Exception e2 = assertThrows(IllegalArgumentException.class, () -> storage.read("read", List.of()));
        assertEquals("Invalid column types", e2.getMessage());

        storage.close();
    }

    @Test
    void shouldFailWhenQueryNotFound() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);

        Properties queries = new Properties();
        queries.put("read", "valid SQL query");
        queries.put("write", "valid SQL query");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        Exception e1 = assertThrows(IllegalArgumentException.class, () -> storage.read("foo", List.of(String.class)));
        assertEquals("Query foo not found", e1.getMessage());

        Exception e2 = assertThrows(IllegalArgumentException.class, () -> storage.write("foo", List.of("foo", "bar")));
        assertEquals("Query foo not found", e2.getMessage());

        storage.close();
    }

    @Test
    void shouldBatchWhenBatchSizeIsGreaterThanOne() throws SQLException {
        Connection conn = mock(Connection.class);
        PreparedStatement prepStmt = mock(PreparedStatement.class);
        when(conn.prepareStatement(any())).thenReturn(prepStmt);
        when(prepStmt.executeBatch()).thenReturn(new int[]{1, 1});

        Properties queries = new Properties();
        queries.put("read", "valid SQL query");
        queries.put("write", "valid SQL query");
        QueryableStorage storage = new JdbcQueryableStorage(conn, queries);

        assertEquals(0, storage.write("write", List.of("foo", "bar"), 2));
        assertEquals(2, storage.write("write", List.of("foo", "bar"), 2));

        storage.close();
    }
}

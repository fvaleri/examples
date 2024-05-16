package it.fvaleri.example.storage;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.String.format;

/**
 * JDBC storage that supports read and write queries.
 * All queries are pre-loaded and cached as {@link PreparedStatement}.
 * Each result set is automatically closed after a read query execution.
 */
public class JdbcQueryableStorage implements QueryableStorage {
    private final Map<String, PreparedStatement> prepStmts;
    private final Map<String, AtomicLong> batchCounters;
    private final List<ResultSet> resultSets;

    JdbcQueryableStorage(Connection conn, Properties queries) {
        try {
            if (conn == null || conn.isClosed()) {
                throw new IllegalArgumentException("Invalid connection");
            }
            if (queries == null || queries.isEmpty()) {
                throw new IllegalArgumentException("Invalid queries");
            }
            this.prepStmts = new HashMap<>();
            this.batchCounters = new HashMap<>();
            this.resultSets = new ArrayList<>();
            for (String key : queries.stringPropertyNames()) {
                prepStmts.put(key, conn.prepareStatement(queries.getProperty(key)));
            }
            queries.clear();
        } catch (SQLException e) {
            throw new RuntimeException(format("Init error: %s", e.getMessage()));
        }
    }

    public List<Row> read(String queryName, List<Class<?>> columnTypes) {
        return read(queryName, columnTypes, null);
    }

    public List<Row> read(String queryName, List<Class<?>> columnTypes, List<Object> queryParams) {
        if (queryName == null || queryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid query name");
        }
        if (columnTypes == null || columnTypes.isEmpty()) {
            throw new IllegalArgumentException("Invalid column types");
        }
        PreparedStatement prepStmt = prepStmts.get(queryName);
        if (prepStmt == null) {
            throw new IllegalArgumentException(format("Query %s not found", queryName));
        }
        if (queryParams != null && !queryParams.isEmpty()) {
            for (int i = 0; i < queryParams.size(); i++) {
                setQueryParam(prepStmt, i + 1, queryParams.get(i));
            }
        }
        try {
            List<Row> rows = new ArrayList<>();
            ResultSet resultSet = prepStmt.executeQuery();
            if (resultSet != null) {
                resultSets.add(resultSet);
                int index = 0;
                while (resultSet.next()) {
                    List<Object> columns = new ArrayList<>();
                    for (int j = 0; j < columnTypes.size(); j++) {
                        columns.add(resultSet.getObject(j + 1, columnTypes.get(j)));
                    }
                    rows.add(new Row(index++, columns));
                }
                resultSet.close();
                resultSets.remove(resultSet);
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(format("Query %s failed: %s", queryName, e.getMessage()));
        }
    }

    public int write(String queryName) {
        return write(queryName, null, 1);
    }

    public int write(String queryName, List<Object> queryParams) {
        return write(queryName, queryParams, 1);
    }

    public int write(String queryName, List<Object> queryParams, int batchSize) {
        if (queryName == null || queryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid query name");
        }
        PreparedStatement prepStmt = prepStmts.get(queryName);
        if (prepStmt == null) {
            throw new IllegalArgumentException(format("Query %s not found", queryName));
        }
        if (queryParams != null && !queryParams.isEmpty()) {
            for (int i = 0; i < queryParams.size(); i++) {
                setQueryParam(prepStmt, i + 1, queryParams.get(i));
            }
        }
        try {
            addBatch(queryName, batchSize);
            return batchSize > 1 ? executeBatch(queryName, batchSize) : prepStmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(format("Query %s failed: %s", queryName, e.getMessage()));
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (resultSets != null && !resultSets.isEmpty()) {
                for (ResultSet rs : resultSets) {
                    rs.close();
                }
                resultSets.clear();
            }
            if (batchCounters != null && !batchCounters.isEmpty()) {
                batchCounters.clear();
            }
            if (prepStmts != null && !prepStmts.isEmpty()) {
                for (PreparedStatement ps : prepStmts.values()) {
                    ps.close();
                }
                prepStmts.clear();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void setQueryParam(PreparedStatement prepStmt, int index, Object param) {
        try {
            if (param == null) {
                prepStmt.setNull(index, Types.NULL);
            } else if (param instanceof String) {
                prepStmt.setString(index, (String) param);
            } else if (param instanceof Integer) {
                prepStmt.setInt(index, (Integer) param);
            } else if (param instanceof Long) {
                prepStmt.setLong(index, (Long) param);
            } else if (param instanceof Date) {
                prepStmt.setDate(index, (Date) param);
            } else if (param instanceof LocalDate) {
                prepStmt.setDate(index, Date.valueOf((LocalDate) param));
            } else if (param instanceof BigDecimal) {
                prepStmt.setBigDecimal(index, (BigDecimal) param);
            } else if (param instanceof Timestamp) {
                prepStmt.setTimestamp(index, (Timestamp) param);
            } else if (param instanceof byte[]) {
                prepStmt.setBinaryStream(index,
                    new ByteArrayInputStream(((byte[]) param)),
                    ((byte[]) param).length);
            } else {
                throw new IllegalArgumentException("Unsupported data type for query parameter");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addBatch(String queryName, int batchSize) throws SQLException {
        if (batchSize > 1) {
            PreparedStatement prepStmt = prepStmts.get(queryName);
            prepStmt.addBatch();
            batchCounters.putIfAbsent(queryName, new AtomicLong(0));
            batchCounters.get(queryName).incrementAndGet();
        }
    }

    private int executeBatch(String queryName, int batchSize) throws SQLException {
        AtomicLong counter = batchCounters.get(queryName);
        int result = 0;
        if (counter.get() == batchSize) {
            PreparedStatement prepStmt = prepStmts.get(queryName);
            int[] updateCounts = prepStmt.executeBatch();
            prepStmt.clearBatch();
            batchCounters.remove(queryName);
            result = Arrays.stream(updateCounts).sum();
        }
        return result;
    }
}

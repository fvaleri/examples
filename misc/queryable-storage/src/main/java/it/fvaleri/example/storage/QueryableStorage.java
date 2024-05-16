package it.fvaleri.example.storage;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

/**
 * Storage that supports read and write queries.
 */
public interface QueryableStorage extends AutoCloseable {
    /**
     * Create default queryable storage instance.
     * @param conn SQL connection.
     * @param queries Query templates
     */
    static QueryableStorage create(Connection conn, Properties queries) {
        return new JdbcQueryableStorage(conn, queries);
    }
    
    /**
     * Executes a read query without parameters.
     *
     * @param queryName Query name.
     * @param columnTypes Result column types.
     * @return List of rows.
     */
    List<Row> read(String queryName, List<Class<?>> columnTypes);

    /**
     * Executes a read query with parameters.
     * The parameter order must match the query order.
     *
     * @param queryName Query name.
     * @param columnTypes Result column types.
     * @param queryParams Query parameters.
     * @return List of rows.
     */
    List<Row> read(String queryName, List<Class<?>> columnTypes, List<Object> queryParams);

    /**
     * Executes a write query without parameters.
     *
     * @param queryName Query name.
     * @return Number of written rows.
     */
    int write(String queryName);

    /**
     * Executes a write query with parameters.
     * The parameter order must match the query order.
     *
     * @param queryName Query name.
     * @param queryParams Query parameters.
     * @return Number of written rows.
     */
    int write(String queryName, List<Object> queryParams);

    /**
     * Executes a write query with parameters with batch support.
     * The parameter order must match the query order.
     * If batchSize size is greater than one, the write will be executed
     * only when batchSize number of writes is reached for that write query.
     *
     * @param queryName Query name.
     * @param queryParams Query parameters.
     * @param batchSize batch size.
     * @return Number of written rows.
     */
    int write(String queryName, List<Object> queryParams, int batchSize);

    /**
     * A row in a query result (list of rows).
     *
     * @param index Result index.
     * @param columns List of row values.
     */
    record Row(long index, List<Object> columns) { }
}

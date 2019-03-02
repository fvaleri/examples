package it.fvaleri.example;

import java.util.List;

/**
 * Storage that supports read and write queries.
 */
public interface QueryableStorage {
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
     * @param queryParams Query parameters
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
     * Closes all open resources.
     */
    void close();

    /**
     * A row in a query result (list of rows).
     *
     * @param index Result index.
     * @param columns List of row values.
     */
    record Row(long index, List<Object> columns) { }
}

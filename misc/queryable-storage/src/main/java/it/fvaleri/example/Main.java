package it.fvaleri.example;

import it.fvaleri.example.PagamentoDao.Pagamento;
import it.fvaleri.example.UsersDao.User;
import it.fvaleri.example.storage.QueryableStorage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Properties;

/**
 * We are using one shared connection because this app is single threaded,
 * but this is not recommended in case of multiple threads querying the database.
 *
 * We have one properties file per DAO, but you can have just one containing
 * all queries and share a single QueryableDataStore instance among all DAOs.
 */
public class Main {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:test;INIT=runscript from 'classpath:/init.sql'")) {
            runUserDaoExample(conn);
            runPagamentoDaoExample(conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runUserDaoExample(Connection conn) throws Exception {
        Properties queries = new Properties();
        queries.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("queries/users.properties"));
        
        try (QueryableStorage storage = QueryableStorage.create(conn, queries)) {
            UsersDao usersDao = new UsersDao(storage);

            User dylan = new User("dylan", "changeit", "dylan@example.com");
            User groucho = new User("groucho", "changeit", "groucho@example.com");
            User block = new User("block", "changeit", "block@example.com");

            usersDao.insert(dylan);
            System.out.println(usersDao.findByPk("dylan"));

            usersDao.insert(groucho);
            usersDao.insert(block);
            System.out.println(usersDao.findAll());

            dylan.password("secR3t!");
            usersDao.update(dylan);
            usersDao.delete(block.userid());

            System.out.println(usersDao.findAll());
        }
    }

    private static void runPagamentoDaoExample(Connection conn) throws Exception {
        long totalRows = 10_000;
        int batchSize = 100;

        Properties queries = new Properties();
        queries.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("queries/pagamento.properties"));
        
        try (QueryableStorage storage = QueryableStorage.create(conn, queries)) {
            PagamentoDao pagamentoDao = new PagamentoDao(storage, batchSize);

            for (int i = 0; i < totalRows; i++) {
                pagamentoDao.insert(new Pagamento(i + 1, 1, new BigDecimal(100), LocalDate.now(), 1, 1, "000123456", "AAA"));
            }

            System.out.println(pagamentoDao.findByPk(1));
            System.out.println(pagamentoDao.findByPk(100));
            System.out.println(pagamentoDao.findByPk(1_000));
            System.out.println(pagamentoDao.findByPk(10_000));
        }
    }
}

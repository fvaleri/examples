package it.fvaleri.example;

import it.fvaleri.example.PagamentoDao.Pagamento;
import it.fvaleri.example.UsersDao.User;

import java.io.IOException;
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

    private static void runUserDaoExample(Connection conn) throws IOException {
        Properties queries = new Properties();
        queries.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("queries/users.properties"));
        UsersDao usersDao = new UsersDao(new JdbcQueryableStorage(conn, queries));

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

    private static void runPagamentoDaoExample(Connection conn) throws IOException {
        long totalRows = 10_000;
        int batchSize = 100;

        Properties queries = new Properties();
        queries.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("queries/pagamento.properties"));
        PagamentoDao pagamentoDao = new PagamentoDao(new JdbcQueryableStorage(conn, queries), batchSize);

        for (int i = 0; i < totalRows; i++) {
            pagamentoDao.insert(new Pagamento(i + 1, 1, new BigDecimal(100), LocalDate.now(), 1, 1, "000123456", "AAA"));
        }

        System.out.println(pagamentoDao.findByPk(1));
        System.out.println(pagamentoDao.findByPk(100));
        System.out.println(pagamentoDao.findByPk(1_000));
        System.out.println(pagamentoDao.findByPk(10_000));
    }
}

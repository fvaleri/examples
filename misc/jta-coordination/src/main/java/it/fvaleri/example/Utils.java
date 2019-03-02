package it.fvaleri.example;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import javax.sql.XAConnection;
import javax.sql.XADataSource;

import org.postgresql.xa.PGXADataSource;

public class Utils {
    public static XADataSource getXADataSource(String dbHostname, int dbPort, String dbName) {
        PGXADataSource dataSource = new PGXADataSource();
        dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", dbHostname, dbPort, dbName));
        return dataSource;
    }

    public static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    public static void close(Statement s, Connection c, XAConnection x) {
        try {
            s.close();
            c.close();
            x.close();
        } catch (Exception e) {
        }
    }
}

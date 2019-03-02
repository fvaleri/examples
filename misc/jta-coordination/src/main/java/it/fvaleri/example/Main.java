package it.fvaleri.example;

import static it.fvaleri.example.Constants.DB_NAME;
import static it.fvaleri.example.Constants.DB_HOSTNAME;
import static it.fvaleri.example.Constants.DB_PORT;
import static it.fvaleri.example.Constants.DB_PASSWORD;
import static it.fvaleri.example.Constants.DB_USERNAME;
import static it.fvaleri.example.Utils.close;
import static it.fvaleri.example.Utils.getXADataSource;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * This JTA application is acting as application server and transaction 
 * manager with a single external resource manager (PostgreSQL database).
 */
public class Main {
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(new TransactionReaper()));
        txWithSingleBranch();
        //txsCanBeSuspended(); // not supported in PG
        //txsCanShareTheSameResource(); // not supported in PG
        txsCanUseSameBranchIfSameRM();
    }

    private static void txWithSingleBranch() {
        XAConnection xaConnection = null;
        Connection connection = null;
        Statement statement = null;

        try {
            // the TM obtains an XAResource for each connection participating in a global transaction
            XADataSource xaDataSource = getXADataSource(DB_HOSTNAME, DB_PORT, DB_NAME);
            xaConnection = xaDataSource.getXAConnection(DB_USERNAME, DB_PASSWORD);
            XAResource xaResource = xaConnection.getXAResource();
            connection = xaConnection.getConnection();
            statement = connection.createStatement();

            // a new global transaction identifier is created
            Xid xid = new TransactionXid(100, new byte[] { 0x01 }, new byte[] { 0x02 });

            // the TM uses the start/end method to associate/disassociate the global transaction with the XAResource (branch)
            xaResource.start(xid, XAResource.TMNOFLAGS);
            statement.executeUpdate("INSERT INTO test_table1 VALUES (100)");
            xaResource.end(xid, XAResource.TMSUCCESS);

            // the two-phase commit process runs when the global transaction is completed
            if (xaResource.prepare(xid) == XAResource.XA_OK) {
                xaResource.commit(xid, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                statement.close();
                connection.close();
                xaConnection.close();
            } catch (Exception e) {
            }
        }
    }

    private static void txsCanBeSuspended() {
        XAConnection xaConnection = null;
        Connection connection = null;
        Statement statement = null;

        try {
            XADataSource xaDataSource = getXADataSource(DB_HOSTNAME, DB_PORT, DB_NAME);
            xaConnection = xaDataSource.getXAConnection(DB_USERNAME, DB_PASSWORD);
            XAResource xaResource = xaConnection.getXAResource();
            connection = xaConnection.getConnection();
            statement = connection.createStatement();

            Xid xid = new TransactionXid(100, new byte[] { 0x01 }, new byte[] { 0x02 });

            xaResource.start(xid, XAResource.TMNOFLAGS);
            statement.executeUpdate("INSERT INTO test_table1 VALUES (100)");
            xaResource.end(xid, XAResource.TMSUSPEND);

            // local TX done outside the scope of the global TX, so it should not be affected by the final rollback
            statement.executeUpdate("INSERT INTO test_table2 VALUES (111)");

            xaResource.start(xid, XAResource.TMRESUME);
            statement.executeUpdate("INSERT INTO test_table1 VALUES (200)");
            xaResource.end(xid, XAResource.TMSUCCESS);
            if (xaResource.prepare(xid) == XAResource.XA_OK) {
                xaResource.rollback(xid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(statement, connection, xaConnection);
        }
    }

    private static void txsCanShareTheSameResource() {
        XAConnection xaConnection = null;
        Connection connection = null;
        Statement statement = null;

        try {
            XADataSource xaDataSource = getXADataSource(DB_HOSTNAME, DB_PORT, DB_NAME);
            xaConnection = xaDataSource.getXAConnection(DB_USERNAME, DB_PASSWORD);
            XAResource xaResource = xaConnection.getXAResource();
            connection = xaConnection.getConnection();
            statement = connection.createStatement();

            TransactionXid xid1 = new TransactionXid(100, new byte[] { 0x01 }, new byte[] { 0x02 });
            TransactionXid xid2 = new TransactionXid(100, new byte[] { 0x11 }, new byte[] { 0x22 });

            xaResource.start(xid1, XAResource.TMNOFLAGS);
            statement.executeUpdate("INSERT INTO test_table1 VALUES (100)");
            xaResource.end(xid1, XAResource.TMSUCCESS);
            xaResource.start(xid2, XAResource.TMNOFLAGS);

            // should allow XA resource to do 2PC on TX1 while associated to TX2
            if (xaResource.prepare(xid1) == XAResource.XA_OK) {
                xaResource.commit(xid1, false);
            }
            
            statement.executeUpdate("INSERT INTO test_table2 VALUES (200)");
            xaResource.end(xid2, XAResource.TMSUCCESS);
            if (xaResource.prepare(xid2) == XAResource.XA_OK) {
                xaResource.rollback(xid2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(statement, connection, xaConnection);
        }
    }

    private static void txsCanUseSameBranchIfSameRM() {
        XAConnection xaConnection1 = null;
        Connection connection1 = null;
        Statement statement1 = null;
        XAConnection xaConnection2 = null;
        Connection connection2 = null;
        Statement statement2 = null;

        try {
            XADataSource xaDataSource1 = getXADataSource(DB_HOSTNAME, DB_PORT, DB_NAME);
            xaConnection1 = xaDataSource1.getXAConnection(DB_USERNAME, DB_PASSWORD);
            XAResource xaResource1 = xaConnection1.getXAResource();
            connection1 = xaConnection1.getConnection();
            statement1 = connection1.createStatement();
            TransactionXid xid1 = new TransactionXid(100, new byte[]{0x01}, new byte[]{0x02});
            xaResource1.start(xid1, XAResource.TMNOFLAGS);
            statement1.executeUpdate("INSERT INTO test_table1 VALUES (100)");
            xaResource1.end(xid1, XAResource.TMSUCCESS);

            XADataSource xaDataSource2 = getXADataSource(DB_HOSTNAME, DB_PORT, DB_NAME);
            xaConnection2 = xaDataSource2.getXAConnection(DB_USERNAME, DB_PASSWORD);
            XAResource xaResource2 = xaConnection2.getXAResource();
            connection2 = xaConnection2.getConnection();
            statement2 = connection2.createStatement();
            if (xaResource2.isSameRM(xaResource1)) {
                // join the first branch created on the first XA connection
                // with this optimization we save on two-phase commit process
                xaResource2.start(xid1, XAResource.TMJOIN);
                statement2.executeUpdate("INSERT INTO test_table2 VALUES (100)");
                xaResource2.end(xid1, XAResource.TMSUCCESS);
            } else {
                TransactionXid xid2 = new TransactionXid(100, new byte[]{0x01}, new byte[]{0x03});
                // create a new branch (this is another RM)
                xaResource2.start(xid2, XAResource.TMNOFLAGS);
                statement2.executeUpdate("INSERT INTO test_table2 VALUES (100)");
                xaResource2.end(xid2, XAResource.TMSUCCESS);
                if (xaResource2.prepare(xid2) == XAResource.XA_OK) {
                    xaResource2.commit(xid2, false);
                }
            }
            if (xaResource1.prepare(xid1) == XAResource.XA_OK) {
                xaResource1.commit(xid1, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close(statement1, connection1, xaConnection1);
            close(statement2, connection2, xaConnection2);
        }
    }
}

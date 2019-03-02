package it.fvaleri.example;

import static it.fvaleri.example.Utils.getXADataSource;
import static it.fvaleri.example.Constants.DB_NAME;
import static it.fvaleri.example.Constants.DB_HOSTNAME;
import static it.fvaleri.example.Constants.DB_PORT;
import static it.fvaleri.example.Constants.DB_PASSWORD;
import static it.fvaleri.example.Constants.DB_USERNAME;

import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

public class TransactionReaper implements Runnable {
    private volatile boolean stop = false;

    @Override
    public void run() {
        while (!stop) {
            try {
                Utils.sleep(60_000);
                System.out.println("Running perdioc TX recovery");
                XADataSource xaDataSource = getXADataSource(DB_HOSTNAME, DB_PORT, DB_NAME);
                XAConnection xaConnection = xaDataSource.getXAConnection(DB_USERNAME, DB_PASSWORD);
                XAResource xaResource = xaConnection.getXAResource();
                Xid[] xids = xaResource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN);
                for (int i = 0; xids != null && i < xids.length; i++) {
                    try {
                        xaResource.rollback(xids[i]);
                    } catch (XAException e) {
                        try {
                            xaResource.forget(xids[i]);
                        } catch (XAException e1) {
                            System.err.printf("Rollback/forget failed: %d%n", e1.errorCode);
                        }
                    }
                }
            } catch(Throwable e) {
                System.err.println("TX recovery failed");
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        this.stop = true;
    }
}

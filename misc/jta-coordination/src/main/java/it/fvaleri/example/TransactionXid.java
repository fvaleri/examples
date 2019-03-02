package it.fvaleri.example;

import javax.transaction.xa.Xid;

/**
 * Globally unique transaction identifier.
 * The gtrid and bqual taken together must be globally unique.
 */
public class TransactionXid implements Xid {
    protected int formatId; // 0 OSI CCR, >0 other format
    protected byte gtrid[]; // global transaction id
    protected byte bqual[]; // branch qualifier id

    public TransactionXid() {
    }

    public TransactionXid(int formatId, byte gtrid[], byte bqual[]) {
        this.formatId = formatId;
        this.gtrid = gtrid;
        this.bqual = bqual;
    }

    public int getFormatId() {
        return formatId;
    }

    public byte[] getBranchQualifier() {
        return bqual;
    }

    public byte[] getGlobalTransactionId() {
        return gtrid;
    }
}

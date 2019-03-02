package it.fvaleri.example;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import java.io.Serializable;

@Entity(name = "DbMessage")
@Table(name = DbMessage.TABLENAME)
public class DbMessage implements Serializable {
    public static final String TABLENAME = "messages";
    private static final long serialVersionUID = 7017085711761726297L;

    @Id
    @Column(name = "messageText", length = 255)
    private String messageText;

    public DbMessage() {
    }

    public DbMessage(String messageText) {
        this.messageText = messageText;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    @Override
    public String toString() {
        return messageText;
    }
}


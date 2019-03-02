package it.fvaleri.example;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;

@XmlRootElement(name = "order")
@XmlAccessorType(XmlAccessType.FIELD)
@CsvRecord(separator = ",", crlf = "UNIX")
public class Order {
    @XmlAttribute
    @DataField(pos = 1)
    private String name;

    @XmlAttribute
    @DataField(pos = 2, precision = 2)
    private double price;

    @XmlAttribute
    @DataField(pos = 3)
    private double amount;

    public Order() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}


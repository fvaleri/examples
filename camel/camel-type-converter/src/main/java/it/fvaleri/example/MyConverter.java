package it.fvaleri.example;

import java.math.BigDecimal;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;

@Converter
public class MyConverter {
    @Converter
    public static Order toOrder(byte[] data, Exchange exchange) {
        TypeConverter converter = exchange.getContext().getTypeConverter();

        String s = converter.convertTo(String.class, data);
        if (s == null || s.length() < 30) {
            throw new IllegalArgumentException("Invalid data");
        }

        // parsing custom protocol
        s = s.replaceAll("#START#", "");
        s = s.replaceAll("#END#", "");

        String name = s.substring(0, 9).trim();
        String price = s.substring(9, 20).trim();
        String amount = s.substring(20).trim();

        BigDecimal priceBd = new BigDecimal(price);
        priceBd.setScale(2);

        Integer amountIn = converter.convertTo(Integer.class, amount);

        return new Order(name, priceBd, amountIn);
    }
}


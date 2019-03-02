package it.fvaleri.example;

import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;
import java.util.StringTokenizer;

import org.apache.camel.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts a string msg contaning comma separated values into
 * Maps or Arrays so that it can be used in camel-sql component.
 */
@Converter
public class MsgConverter {
    private static final Logger LOG = LoggerFactory.getLogger(MsgConverter.class);
    public static String delimiter = ",";

    private MsgConverter() {
    }

    /**
     * Converts String message into java.util.Map by tokenizing
     * the input string and trimming any leading and trailing
     * whitespace characters.
     *
     * @param orig The original input msg containing comma separated values.
     * @return Map of these values.
     */
    @Converter
    public static Map<String, String> toMap(String orig) {
        LOG.debug("Converting from String to java.util.Map");
        Map<String, String> result = new HashMap<>();
        if (orig != null && (!orig.equals(""))) {
            StringTokenizer tokenizer = new StringTokenizer(orig, delimiter);
            result.put("firstname", tokenizer.nextToken().trim());
            result.put("lastname", tokenizer.nextToken().trim());
            result.put("login", tokenizer.nextToken().trim());
            result.put("password", tokenizer.nextToken().trim());
            LOG.debug("Converted to Map: {}", result);
        }
        return result;
    }

    /**
     * Converts String message into java.lang.Object[] by tokenizing
     * the input string and trimming any leading and trailing
     * whitespace characters.
     *
     * @param orig The original input msg containing comma separated values.
     * @return Object array of these values.
     */
    @Converter
    public static Object[] toArray(String orig) {
        LOG.debug("Converting from String to java.lang.Object[]");
        Object[] result = null;
        if (orig != null && (!orig.equals(""))) {
            StringTokenizer tokenizer = new StringTokenizer(orig, delimiter);
            result = new Object[tokenizer.countTokens()];
            result[0] = new String(tokenizer.nextToken().trim());
            result[1] = new String(tokenizer.nextToken().trim());
            result[2] = new String(tokenizer.nextToken().trim());
            result[3] = new String(tokenizer.nextToken().trim());
            LOG.debug("Converted to Object array: {}", Arrays.toString(result));
        }
        return result;
    }
}


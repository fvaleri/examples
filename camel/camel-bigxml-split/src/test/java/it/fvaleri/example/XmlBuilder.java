package it.fvaleri.example;

import java.io.File;
import java.io.FileOutputStream;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(XmlBuilder.class);
    private final static String BASE_PATH = System.getProperty("user.dir") + "/target/data";
    private final static int NUM_RECORDS = 40_000;

    public static String getBasePath() {
        return BASE_PATH;
    }

    public static int getNumOfRecords() {
        return NUM_RECORDS;
    }

    public static void build() throws Exception {
        new File(BASE_PATH).mkdir();
        File f = new File(BASE_PATH + "/test.xml");
        if (!f.exists()) {
            LOG.info("Building test XML file...");
            XMLOutputFactory xof = XMLOutputFactory.newInstance();
            XMLStreamWriter xsw = xof.createXMLStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                xsw.writeStartDocument("UTF-8", "1.0");
                xsw.writeStartElement("records");
                xsw.writeAttribute("xmlns", "http://fvaleri.it/records");
                for (int i = 0; i < NUM_RECORDS; i++) {
                    xsw.writeStartElement("record");
                    xsw.writeStartElement("key");
                    xsw.writeCharacters("" + i);
                    xsw.writeEndElement();
                    xsw.writeStartElement("value");
                    xsw.writeCharacters("The quick brown fox jumps over the lazy dog");
                    xsw.writeEndElement();
                    xsw.writeEndElement();
                }
                xsw.writeEndElement();
                xsw.writeEndDocument();
            } finally {
                LOG.info("Test XML file ready (size: {} kB)", f.length() / 1024);
                xsw.flush();
                xsw.close();
            }
        }
    }
}


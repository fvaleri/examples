package it.fvaleri.example;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// a Synchronization which is to be executed when the Exchange is done
// this allows us to execute custom logic such as rollback by deleting the file which was saved
public class FileRollback implements Synchronization {
    private static final Logger LOG = LoggerFactory.getLogger(FileRollback.class);

    public void onComplete(Exchange exchange) {
    }

    public void onFailure(Exchange exchange) {
        // the Exception is stored on the Exchange which you can obtain using getException
        String name = exchange.getIn().getHeader(Exchange.FILE_NAME_PRODUCED, String.class);
        LOG.warn("Failure occurred so deleting backup file: {}", name);
        FileUtil.deleteFile(new File(name));
    }
}


package it.fvaleri.example;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

import org.jboss.logging.Logger;

@QuarkusMain
public class Application implements QuarkusApplication {
    private static final Logger LOG = Logger.getLogger(Application.class);

    public static void main(String[] args) {
        Quarkus.run(Application.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        LOG.info("Application started");
        Quarkus.waitForExit();
        LOG.info("Application stopped");
        return 0;
    }
}

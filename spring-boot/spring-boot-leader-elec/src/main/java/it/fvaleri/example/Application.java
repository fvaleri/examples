package it.fvaleri.example;

import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Configuration
    static class BeanConfiguration {
        @Bean
        public CustomService customService(CamelClusterService clusterService) throws Exception {
            CustomService service = new CustomService();
            clusterService.getView("lock2").addEventListener((CamelClusterEventListener.Leadership) (view, leader) -> {
                boolean weAreLeaders = leader.isPresent() && leader.get().isLocal();
                if (weAreLeaders && !service.isStarted()) {
                    service.start();
                } else if (!weAreLeaders && service.isStarted()) {
                    service.stop();
                }
            });
            return service;
        }
    }

    public static class CustomService {
        private static final Logger LOG = LoggerFactory.getLogger(CustomService.class);
        private boolean started;

        public void start() {
            LOG.info("CustomService has been started on the master pod");
            started = true;
        }

        public boolean isStarted() {
            return started;
        }

        public void stop() {
            LOG.info("CustomService has been stopped on the master pod");
            started = false;
        }
    }
}

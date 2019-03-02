package it.fvaleri.example.tick;

import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedService;

public class Activator implements BundleActivator {
    private static final String SERVICE_PID = "it.fvaleri.example.tick";

    private boolean stop = false;
    private MyTickService tick = new MyTickService();

    public void start(BundleContext bundleContext) throws Exception {
        stop = false;

        Hashtable <String, Object> properties = new Hashtable<String, Object>();
        properties.put(Constants.SERVICE_PID, SERVICE_PID);
        bundleContext.registerService(ManagedService.class.getName(), tick, properties);
        bundleContext.registerService(TickService.class.getName(), tick, null);
        System.out.println("Tick bundle started");

        new Thread(new Runnable() {
            public void run() {
                while (!stop) {
                    try {
                        System.out.println("Tick!");
                        for (TickListener listener : tick.getListeners()) {
                            listener.tick();
                        }
                        Thread.sleep(tick.getDelay());
                    } catch (Throwable e) {
                    }
                }
            }
        }).start();
    }

    public void stop(BundleContext bundleContext) throws Exception {
        stop = true;
        System.out.println("Tick bundle stopped");
    }
}

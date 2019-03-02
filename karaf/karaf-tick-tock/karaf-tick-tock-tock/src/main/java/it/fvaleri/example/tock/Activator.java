package it.fvaleri.example.tock;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import it.fvaleri.example.tick.TickListener;
import it.fvaleri.example.tick.TickService;

public class Activator implements BundleActivator, TickListener {
    @Override
    public void tick() {
        System.out.println("Tock!");
    }

    public void start(BundleContext bundleContext) throws Exception {
        ServiceTracker tracker = new ServiceTracker(bundleContext, TickService.class.getName(), null);
        tracker.open();
        TickService tick = (TickService) tracker.getService();
        tracker.close();

        if (tick != null) {
            tick.addListener(this);
        } else {
            throw new Exception("Can't start tock bundle, as tick service is not running");
        }

        System.out.println("Tock bundle started");
    }

    public void stop(BundleContext bundleContext) throws Exception {
        ServiceTracker tracker = new ServiceTracker(bundleContext, TickService.class.getName(), null);

        tracker.open();
        TickService tick = (TickService) tracker.getService();
        tracker.close();

        if (tick != null) {
            tick.removeListener(this);
        }

        System.out.println("Tock bundle stopped");
    }
}

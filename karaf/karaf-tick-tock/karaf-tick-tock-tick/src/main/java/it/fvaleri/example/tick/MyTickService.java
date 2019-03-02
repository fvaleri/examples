package it.fvaleri.example.tick;

import java.util.Dictionary;
import java.util.List;
import java.util.Vector;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

public class MyTickService implements TickService, ManagedService {
    private static final int DEFAULT_DELAY = 5000;

    private List<TickListener> listeners;
    private int delay;

    public MyTickService() {
        this.listeners = new Vector<TickListener>();
        this.delay = DEFAULT_DELAY;
    }

    @Override
    public int getDelay() {
        return delay;
    }

    @Override
    public List<TickListener> getListeners() {
        return listeners;
    }

    @Override
    public void addListener(TickListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(TickListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void updated(Dictionary properties) throws ConfigurationException {
        System.out.println("TickService config update");
        if (properties != null) {
            String newDelay = (String) properties.get("delay");
            if (newDelay != null) {
                System.out.printf("Passed a new delay of %s ms%n", newDelay);
                delay = Integer.parseInt(newDelay);
            } else {
                System.out.println("Passed a null delay");
                delay = DEFAULT_DELAY;
            }
        } else {
            System.out.println("Passed a null configuration");
            delay = DEFAULT_DELAY;
        }
	}
}

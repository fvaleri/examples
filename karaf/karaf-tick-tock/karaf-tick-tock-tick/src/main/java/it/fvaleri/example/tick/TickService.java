package it.fvaleri.example.tick;

import java.util.List;

public interface TickService {
    public int getDelay();

    public List<TickListener> getListeners();

    public void addListener(TickListener listener);

    public void removeListener(TickListener listener);
}

package it.fvaleri.example;

/**
 * Blocking queue implementation.
 *
 * @param <E> Data type.
 */
public class BlockingQueue<E> {
    public final int capacity;
    private final E[] store;
    private int head, tail, size;

    private int waitSetSize;
    private long msgCount;
    private long lastChange;
    
    @SuppressWarnings("unchecked")
    public BlockingQueue(int capacity) {
        this.capacity = capacity;
        this.store = (E[]) new Object[capacity];
    }

    private int next(int x) {
        return (x + 1) % store.length;
    }

    public synchronized void put(E e) throws InterruptedException {
        String name = Thread.currentThread().getName();
        while (isFull()) {
            System.out.printf("buffer full, %s waits%n", name);
            waitSetSize++;
            wait(); // suspends a thread unconditionally, putting it in the wait set of the object
            System.out.printf("%s notified%n", name);
        }
        System.out.printf("%s successfully puts%n", name);
        notify(); // selects a thread in the wait set, while notifyAll allows all threads in the set to resume
        if (waitSetSize > 0) {
            waitSetSize--;
        }
        System.out.printf("wait set size is %d%n", waitSetSize);
        store[tail] = e;
        tail = next(tail);
        size++;
        msgCount++;
        lastChange = System.nanoTime();
    }

    public synchronized E get() throws InterruptedException {
        String name = Thread.currentThread().getName();
        while (isEmpty()) {
            System.out.printf("buffer full; %s waits%n", name);
            waitSetSize++;
            wait();
            System.out.printf("%s notified%n", name);
        }
        System.out.printf("%s successfully gets%n", name);
        notify(); // replace notify with notifyAll to fix it
        if (waitSetSize > 0) {
            waitSetSize--;
        }
        System.out.printf("wait set size is %d%n", waitSetSize);
        E e = store[head];
        store[head] = null; // for GC
        head = next(head);
        size--;

        lastChange = System.nanoTime();
        return e;
    }

    public synchronized boolean isFull() {
        return size == capacity;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public int waitSetSize() {
        return waitSetSize;
    }

    public long msgCount() {
        return msgCount;
    }

    public long lastChange() {
        return lastChange;
    }
}

package info.kgeorgiy.ja.gordienko.iterative;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;

public class ParallelMapperImpl implements ParallelMapper {
    private final Thread[] threadPool;
    private final SyncQueue<Runnable> tasks;

    /**
     * Constructor that takes number of threads
     * @param threads number of threads
     */
    public ParallelMapperImpl(int threads) {
        threadPool = new Thread[threads];
        tasks = new SyncQueue<>();

        for (int i = 0; i < threads; i++) {
            threadPool[i] = Thread.ofPlatform().start(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Runnable task = tasks.getItem();
                        task.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> items) throws InterruptedException {
        TasksContext<R> results = new TasksContext<>(items.size());

        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            tasks.addItem(() -> {
                try {
                    R result = f.apply(items.get(index));
                    results.setResult(index, result);
                } catch (RuntimeException e) {
                    results.addException(e);
                }
            });
        }

        results.await();
        results.checkExceptions();

        return results.getResults();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        for (Thread thread : threadPool) {
            thread.interrupt();
        }
        for (Thread thread : threadPool) {
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}

/**
 * Custom synchronized queue class
 */
class SyncQueue<T> {
    private final Queue<T> queue = new ArrayDeque<>();

    public synchronized void addItem(T item) {
        queue.add(item);
        notify();
    }

    public synchronized T getItem() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        return queue.poll();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
}

/**
 * Custom synchronized tasks context class
 * @param <R> result type
 */
class TasksContext<R> {
    private int remaining;
    private final List<R> results;
    private final List<RuntimeException> exceptions = new ArrayList<>();

    public TasksContext(int size) {
        this.remaining = size;
        this.results = new ArrayList<>(Collections.nCopies(size, null));
    }

    public synchronized void setResult(int index, R value) {
        results.set(index, value);
        decrement();
    }

    public synchronized void addException(RuntimeException e) {
        exceptions.add(e);
        decrement();
    }

    private void decrement() {
        if (--remaining == 0) {
            notify();
        }
    }

    public synchronized void await() throws InterruptedException {
        while (remaining > 0) {
            wait();
        }
    }

    public synchronized void checkExceptions() {
        if (!exceptions.isEmpty()) {
            RuntimeException e = new RuntimeException("Exception in one or more threads occurred");
            exceptions.forEach(e::addSuppressed);
            throw e;
        }
    }

    public List<R> getResults() {
        return results;
    }
}

package info.kgeorgiy.ja.gordienko.iterative;

import info.kgeorgiy.java.advanced.iterative.ScalarIP;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Class that allows to perform multiple tasks using iterative parallelism.
 * @author Konstantin Gordienko
 */
public class IterativeParallelism implements ScalarIP {

    /**
     * Default constructor
     */
    public IterativeParallelism() {
    }

    private static <T> int findMaxByComparator(List<T> values, int from, int to, Comparator<? super T> comparator) {
        return values.indexOf(Collections.max(values.subList(from, to), comparator));
    }

    private static <T> int findIndexByPredicate(List<T> values, int from, int to, Predicate<? super T> predicate) {
        return IntStream.range(from, to)
                .filter(i -> predicate.test(values.get(i)))
                .findFirst()
                .orElse(-1);
    }

    private static <T> long findIndicesSumByPredicate(List<T> values, int from, int to, Predicate<? super T> predicate) {
        long sum = 0;
        for (int i = from; i < to; i++) {
            if (predicate.test(values.get(i))) {
                sum += i;
            }
        }
        return sum;
    }

    private static <T, R> R performParallel(int threads,
                                            List<T> values,
                                            BiFunction<Integer, Integer, R> task,
                                            R foldInitialValue,
                                            BinaryOperator<R> foldFunc) throws InterruptedException {
        if (values.isEmpty()) return foldInitialValue;

        int actualThreadCount = Math.min(threads, values.size());
        List<R> results = new ArrayList<>(Collections.nCopies(actualThreadCount, null));
        Thread[] threadPool = new Thread[actualThreadCount];
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        int partitionSize = values.size() / actualThreadCount;
        int remain = values.size() % actualThreadCount;
        int start = 0;
        for (int i = 0; i < actualThreadCount; i++) {
            int from = start;
            int to = start + partitionSize + (i < remain ? 1 : 0);
            final int index = i;
            start = to;
            threadPool[i] = Thread.ofPlatform().start(() -> {
                try {
                    results.set(index, task.apply(from, to));
                } catch (Throwable e) {
                    exceptions.add(e);
                }
            });
        }

        for (Thread thread : threadPool) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                exceptions.add(e);
                Thread.currentThread().interrupt();
            }
        }

        if (!exceptions.isEmpty()) {
            InterruptedException ie = new InterruptedException("Exception in one or more threads occurred");
            for (Throwable t : exceptions) {
                ie.addSuppressed(t);
            }
            throw ie;
        }

        R result = foldInitialValue;
        for (R val : results) {
            result = foldFunc.apply(result, val);
        }
        return result;
    }

    private <T> int argExtremal(int threads, List<T> values, Comparator<? super T> comparator) throws InterruptedException {
        if (values.isEmpty()) return -1;
        return performParallel(threads, values,
                (from, to) -> findMaxByComparator(values, from, to, comparator),
                0,
                (ind1, ind2) -> comparator.compare(values.get(ind1), values.get(ind2)) >= 0 ? ind1 : ind2
        );
    }

    @Override
    public <T> int argMax(int threads, List<T> values, Comparator<? super T> comparator) throws InterruptedException {
        return argExtremal(threads, values, comparator);
    }
    @Override
    public <T> int argMin(int threads, List<T> values, Comparator<? super T> comparator) throws InterruptedException {
        return argExtremal(threads, values, comparator.reversed());
    }

    @Override
    public <T> int indexOf(int threads, List<T> values, Predicate<? super T> predicate) throws InterruptedException {
        if (values.isEmpty()) return -1;

        return performParallel(threads, values,
                (from, to) -> findIndexByPredicate(values, from, to, predicate),
                -1,
                (saved, current) -> {
                    if (saved == -1) return current;
                    if (current == -1) return saved;
                    return Math.min(saved, current);
                }
        );
    }

    @Override
    public <T> int lastIndexOf(int threads, List<T> values, Predicate<? super T> predicate) throws InterruptedException {
        int idx = indexOf(threads, values.reversed(), predicate);
        return idx == -1 ? -1 : values.size() - 1 - idx;
    }

    @Override
    public <T> long sumIndices(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return performParallel(threads, values,
                (from, to) -> findIndicesSumByPredicate(values, from, to, predicate),
                0L,
                Long::sum
        );
    }
}
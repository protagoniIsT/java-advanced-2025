package info.kgeorgiy.ja.gordienko.iterative;

import info.kgeorgiy.java.advanced.iterative.ScalarIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Class that allows to perform multiple tasks using iterative parallelism.
 *
 * @author Konstantin Gordienko
 */
public class IterativeParallelism implements ScalarIP {

    private final ParallelMapper parallelMapper;

    /**
     * Default constructor
     */
    public IterativeParallelism() {
        this.parallelMapper = null;
    }

    /**
     * Constructor out of {@link ParallelMapper} instance
     *
     * @param parallelMapper {@link ParallelMapper} instance
     */
    public IterativeParallelism(ParallelMapper parallelMapper) {
        this.parallelMapper = parallelMapper;
    }

    private static <T> int findMaxByComparator(List<T> values, Comparator<? super T> comparator, int offset) {
        int idx = values.indexOf(Collections.max(values, comparator));
        return idx != -1 ? idx + offset : -1;
    }

    private static <T> int findIndexByPredicate(List<T> values, Predicate<? super T> predicate, int offset) {
        int idx = IntStream.range(0, values.size())
                .filter(i -> predicate.test(values.get(i)))
                .findFirst()
                .orElse(-1);
        return idx != -1 ? idx + offset : -1;
    }

    private static <T> long findIndicesSumByPredicate(List<T> values, Predicate<? super T> predicate, int offset) {
        long sum = 0;
        for (int i = 0; i < values.size(); i++) {
            if (predicate.test(values.get(i))) {
                sum += i + offset;
            }
        }
        return sum;
    }

    private <T, R> R performParallel(int threads,
                                     List<T> values,
                                     BiFunction<List<? extends T>, Integer, R> task,
                                     R foldInitialValue,
                                     BinaryOperator<R> foldFunc) throws InterruptedException {
        if (values.isEmpty()) return foldInitialValue;

        int actualThreadCount = Math.min(threads, values.size());
        List<List<? extends T>> chunks = new ArrayList<>(actualThreadCount);
        List<Integer> offsets = new ArrayList<>(actualThreadCount);
        int chunkSize = values.size() / actualThreadCount;
        int remain = values.size() % actualThreadCount;
        int start = 0;
        for (int i = 0; i < actualThreadCount; i++) {
            int currSize = chunkSize + (i < remain ? 1 : 0);
            chunks.add(values.subList(start, start + currSize));
            offsets.add(start);
            start += currSize;
        }
        List<R> results;

        if (parallelMapper == null) {
            results = new ArrayList<>(Collections.nCopies(actualThreadCount, null));
            Thread[] threadPool = new Thread[actualThreadCount];
            final List<Throwable> exceptions = new ArrayList<>();

            for (int i = 0; i < actualThreadCount; i++) {
                final int idx = i;
                final int off = offsets.get(i);
                List<? extends T> chunk = chunks.get(i);
                threadPool[i] = Thread.ofPlatform().start(() -> {
                    try {
                        results.set(idx, task.apply(chunk, off));
                    } catch (Throwable e) {
                        exceptions.add(e);
                    }
                });
            }

            for (Thread thread : threadPool) {
                while (true) {
                    try {
                        thread.join();
                        break;
                    } catch (InterruptedException e) {
                        exceptions.add(e);
                        Thread.currentThread().interrupt();
                    }
                }
            }

            final List<Throwable> exceptionList = exceptions.stream().filter(Objects::nonNull).toList();

            if (!exceptionList.isEmpty()) {
                InterruptedException ie = new InterruptedException("Exception in one or more threads occurred");
                exceptionList.forEach(ie::addSuppressed);
                throw ie;
            }

        } else {
            List<Map.Entry<List<? extends T>, Integer>> pairs = new ArrayList<>(actualThreadCount);
            for (int i = 0; i < actualThreadCount; i++) {
                pairs.add(new AbstractMap.SimpleEntry<>(chunks.get(i), offsets.get(i)));
            }
            results = parallelMapper.map(
                    entry -> task.apply(entry.getKey(), entry.getValue()),
                    pairs
            );
        }

        R result = foldInitialValue;
        for (R val : results) {
            result = foldFunc.apply(result, val);
        }
        return result;
    }


    private <T> int argExtremal(int threads, List<T> values, Comparator<? super T> comparator) throws InterruptedException {
        return performParallel(threads, values,
                (chunk, offset) -> findMaxByComparator(chunk, comparator, offset),
                0,
                (ind1, ind2) -> comparator.compare(values.get(ind1), values.get(ind2)) >= 0 ? ind1 : ind2
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int argMax(int threads, List<T> values, Comparator<? super T> comparator) throws InterruptedException {
        return argExtremal(threads, values, comparator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int argMin(int threads, List<T> values, Comparator<? super T> comparator) throws InterruptedException {
        return argExtremal(threads, values, comparator.reversed());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int indexOf(int threads, List<T> values, Predicate<? super T> predicate) throws InterruptedException {
        return performParallel(threads, values,
                (chunk, offset) -> findIndexByPredicate(chunk, predicate, offset),
                -1,
                (saved, current) -> {
                    if (saved == -1) return current;
                    if (current == -1) return saved;
                    return Math.min(saved, current);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> int lastIndexOf(int threads, List<T> values, Predicate<? super T> predicate) throws InterruptedException {
        int idx = indexOf(threads, values.reversed(), predicate);
        return idx == -1 ? -1 : values.size() - 1 - idx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> long sumIndices(int threads, List<? extends T> values, Predicate<? super T> predicate) throws InterruptedException {
        return performParallel(threads, values,
                (chunk, offset) -> findIndicesSumByPredicate(chunk, predicate, offset),
                0L,
                Long::sum);
    }
}

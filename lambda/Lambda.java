package info.kgeorgiy.ja.gordienko.lambda;

import info.kgeorgiy.java.advanced.lambda.EasyLambda;
import info.kgeorgiy.java.advanced.lambda.Trees;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.Spliterator.*;

public class Lambda implements EasyLambda {

    private static <T, S> Spliterator<T> treeSpliterator(
            S tree,
            Function<S, Optional<T>> leafExtractor,
            Function<S, List<S>> childrenExtractor,
            ToLongFunction<S> sizeEstimator,
            int characteristics
    ) {
        return new Spliterator<>() {
            private final Deque<S> stack = new ArrayDeque<>(List.of(tree));

            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                while (!stack.isEmpty()) {
                    S curr = stack.pop();
                    Optional<T> node = leafExtractor.apply(curr);
                    if (node.isPresent()) {
                        action.accept(node.get());
                        return true;
                    } else {
                        List<S> children = childrenExtractor.apply(curr);
                        children.reversed().forEach(stack::push);
                    }
                }
                return false;
            }

            @Override
            public Spliterator<T> trySplit() {
                if (stack.isEmpty()) {
                    return null;
                }
                S curr = stack.pop();
                Optional<T> node = leafExtractor.apply(curr);
                if (node.isPresent()) {
                    stack.push(curr);
                    return null;
                }
                List<S> children = childrenExtractor.apply(curr);
                children.subList(1, children.size()).reversed().forEach(stack::push);
                return children.size() <= 1 ? null : treeSpliterator(children.getFirst(),
                                                                    leafExtractor,
                                                                    childrenExtractor,
                                                                    sizeEstimator,
                                                                    characteristics);
            }

            @Override
            public long estimateSize() {
                return sizeEstimator.applyAsLong(stack.peek());
            }

            @Override
            public int characteristics() {
                return characteristics;
            }
        };
    }

    public <T> Spliterator<T> binaryTreeSpliterator(Trees.Binary<T> tree) {
        int characteristics = IMMUTABLE | ORDERED;
        if (tree instanceof Trees.Leaf<T>) {
            characteristics |= (SIZED | SUBSIZED);
        }
        return treeSpliterator(
                tree,
                node -> node instanceof Trees.Leaf<T> leaf ? Optional.of(leaf.value()) : Optional.empty(),
                node -> node instanceof Trees.Binary.Branch<T> branch
                        ? Arrays.asList(branch.left(), branch.right())
                        : Collections.emptyList(),
                node -> tree instanceof Trees.Leaf<T> ? 1 : Long.MAX_VALUE,
                characteristics
        );
    }

    @Override
    public <T> Spliterator<T> sizedBinaryTreeSpliterator(Trees.SizedBinary<T> tree) {
        return treeSpliterator(
                tree,
                node -> node instanceof Trees.Leaf<T> leaf ? Optional.of(leaf.value()) : Optional.empty(),
                node -> node instanceof Trees.SizedBinary.Branch<T> branch
                        ? Arrays.asList(branch.left(), branch.right())
                        : Collections.emptyList(),
                Trees.SizedBinary::size,
                IMMUTABLE | ORDERED | SIZED | SUBSIZED
        );
    }

    @Override
    public <T> Spliterator<T> naryTreeSpliterator(Trees.Nary<T> tree) {
        int characteristics = IMMUTABLE | ORDERED;
        if (tree instanceof Trees.Leaf<T>) {
            characteristics |= (SIZED | SUBSIZED);
        }
        return treeSpliterator(
                tree,
                node -> node instanceof Trees.Leaf<T> leaf ? Optional.of(leaf.value()) : Optional.empty(),
                node -> node instanceof Trees.Nary.Node<T> nNode
                        ? nNode.children()
                        : Collections.emptyList(),
                node -> tree instanceof Trees.Leaf<T> ? 1 : Long.MAX_VALUE,
                characteristics
        );
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> first() {
        return Collectors.reducing((e1, e2) -> e1);
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> last() {
        return Collectors.reducing((e1, e2) -> e2);
    }

    @Override
    public <T> Collector<T, ?, Optional<T>> middle() {
        final class State {
            int index = 0;
            final Deque<T> elements = new ArrayDeque<>();

            public void updateCandidate(T element) {
                elements.addLast(element);
                if (elements.size() > index / 2 + 1) {
                    elements.poll();
                }
                index++;
            }

            public State combine(State other) {
                throw new UnsupportedOperationException("Middle cannot be found between concurrent streams.");
            }

            public Optional<T> getMiddle() {
                if (elements.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(elements.getFirst());
            }
        }

        return Collector.of(
                State::new,
                State::updateCandidate,
                State::combine,
                State::getMiddle
        );
    }


    private enum CommonPart {
        PREFIX, SUFFIX
    }

    private Collector<CharSequence, ?, String> common(CommonPart mode) {
        final class State {
            String currCommon = null;
            private final CommonPart searchMode = mode;

            private int getIndex(int len, int iterationIndex) {
                if (searchMode == CommonPart.SUFFIX) {
                    return len - iterationIndex - 1;
                }
                return iterationIndex;
            }

            public void updateCommon(String other) {
                if (currCommon == null) {
                    currCommon = other;
                    return;
                }
                int len1 = currCommon.length();
                int len2 = other.length();
                int i = 0;
                while (i < len1 && i < len2
                        && currCommon.charAt(getIndex(len1, i)) == other.charAt(getIndex(len2, i))) {
                    i++;
                }
                currCommon = searchMode == CommonPart.PREFIX
                        ? currCommon.substring(0, i)
                        : currCommon.substring(len1 - i);
            }

            public State combine(State other) {
                if (other.currCommon != null) {
                    this.updateCommon(other.currCommon);
                }
                return this;
            }

            public String getCommon() {
                return this.currCommon == null ? "" : this.currCommon;
            }
        }

        return Collector.of(
                State::new,
                (state, elem) -> state.updateCommon(elem.toString()),
                State::combine,
                State::getCommon
        );
    }

    @Override
    public Collector<CharSequence, ?, String> commonPrefix() {
        return common(CommonPart.PREFIX);
    }

    @Override
    public Collector<CharSequence, ?, String> commonSuffix() {
        return common(CommonPart.SUFFIX);
    }
}

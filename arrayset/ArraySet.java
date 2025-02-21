package info.kgeorgiy.ja.gordienko.arrayset;

import java.text.MessageFormat;
import java.util.*;

public class ArraySet<E> extends AbstractSet<E> implements SortedSet<E> {

    private final ArrayList<E> elements;
    private final Comparator<? super E> comparator;

    public ArraySet() {
        this(Collections.emptyList(), null);
    }

    public ArraySet(Collection<? extends E> collection) {
        this(collection, null);
    }

    public ArraySet(Comparator<? super E> comparator) {
        this(Collections.emptyList(), comparator);
    }

    public ArraySet(Collection<? extends E> collection, Comparator<? super E> comparator) {
        this.comparator = comparator;
        if (isSortedSet(collection, comparator)) {
            this.elements = new ArrayList<>(collection);
            return;
        }
        SortedSet<E> treeSet = new TreeSet<>(comparator);
        treeSet.addAll(collection);
        this.elements = new ArrayList<>(treeSet);
    }

    private ArraySet(List<E> sortedList, Comparator<? super E> comparator) {
        this.elements = new ArrayList<>(sortedList);
        this.comparator = comparator;
    }

    private boolean isSortedSet(Collection<? extends E> collection, Comparator<? super E> comparator) {
        if (collection instanceof SortedSet<?>
                && Objects.equals(((SortedSet<? extends E>) collection).comparator(), comparator)) {
            return true;
        }
        Iterator<? extends E> it = collection.iterator();
        if (!it.hasNext()) return true;
        E prev = it.next();
        while (it.hasNext()) {
            E curr = it.next();
            if (compare(prev, curr, comparator) >= 0) {
                return false;
            }
            prev = curr;
        }
        return true;
    }

    @Override
    public Iterator<E> iterator() {
        return Collections.unmodifiableList(elements).iterator();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o) {
        if (o == null) return false;
        try {
            return indexOf((E) o) >= 0;
        } catch (ClassCastException e) {
            return false;
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object element : c) {
            if (!contains(element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    private SortedSet<E> subSetByIndices(int fromIndex, int toIndex) {
        return new ArraySet<>(elements.subList(fromIndex, toIndex), comparator);
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) throws IllegalArgumentException {
        if (compare(fromElement, toElement, comparator) > 0) {
            throw new IllegalArgumentException(
                    MessageFormat.format("fromElement ''{0}'' is bigger than toElement " +
                            "''{1}'' by comparator ''{2}''.", fromElement, toElement, comparator));
        }
        return subSetByIndices(getIndex(fromElement), getIndex(toElement));
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return subSetByIndices(0, getIndex(toElement));
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return subSetByIndices(getIndex(fromElement), size());
    }

    @Override
    public E first() {
        return elements.getFirst();
    }

    @Override
    public E last() {
        return elements.getLast();
    }

    private int indexOf(E element) {
        return Collections.binarySearch(elements, element, comparator);
    }

    private int compare(E o1, E o2, Comparator<? super E> comparator) {
        return comparator == null
                ? Collections.reverseOrder().reversed().compare(o1, o2)
                : comparator.compare(o1, o2);
    }

    private int getIndex(E element) {
        int index = indexOf(element);
        return index < 0 ? -index - 1 : index;
    }
}

package tungsten.types.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TreeSet;

public class CombiningIterator <T extends Comparable<T>> implements Iterator<T> {
    private final Iterator<T> iter1;
    private final Iterator<T> iter2;
    private final TreeSet<T> cache = new TreeSet<>();

    public CombiningIterator(Iterator<T> first, Iterator<T> second) {
        this.iter1 = first;
        this.iter2 = second;
    }

    @Override
    public boolean hasNext() {
        return iter1.hasNext() || iter2.hasNext() || !cache.isEmpty();
    }

    @Override
    public T next() {
        if (iter1.hasNext() && iter2.hasNext()) {
            // choose which one gets obtained next
            T val1 = iter1.next();
            T val2 = iter2.next();
            if (cache.isEmpty()) {
                return findLowestAndCacheOther(val1, val2);
            }
            T top = cache.first();
            // if we got this far, we have something in the cache that is important
            if (top.compareTo(val1) < 0 && top.compareTo(val2) < 0) {
                cache.add(val1);
                cache.add(val2);
                cache.remove(top);
                return top;
            } else {
                return findLowestAndCacheOther(val1, val2);
            }
        } else if (iter1.hasNext()) {
            T val = iter1.next();
            return findLowestVsCache(val);
        } else if (iter2.hasNext()) {
            T val = iter2.next();
            return findLowestVsCache(val);
        } else if (!cache.isEmpty()) {
            return cache.pollFirst();
        }
        throw new NoSuchElementException("Parent iterators have both been exhausted.");
    }

    private T findLowestVsCache(T val) {
        if (!cache.isEmpty()) {
            T top = cache.first();
            if (top.compareTo(val) < 0) {
                cache.add(val);
                cache.remove(top);
                return top;
            } else if (top.compareTo(val) == 0) {
                // remove duplicates
                cache.remove(top);
            }
        }
        return val;
    }

    private T findLowestAndCacheOther(T val1, T val2) {
        int comparison = val1.compareTo(val2);
        if (comparison < 0) {
            cache.add(val2);
            return val1;
        } else if (comparison > 0) {
            cache.add(val1);
            return val2;
        } else {
            // currently we're dropping duplicates
            return val1;
        }
    }
}

/*
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <Tarquin.AZ@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tungsten.types.set.impl;

import tungsten.types.Multiset;
import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link Multiset} for {@link Numeric} types.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class NumericMultiset implements Multiset<Numeric> {
    private class ElementTuple {
        private final Numeric value;
        private long multiplicity;
        
        public ElementTuple(Numeric val) {
            this.value = val;
            this.multiplicity = 1L;
        }
        
        public void increment() {
            multiplicity++;
        }
        
        public void decrement() {
            multiplicity--;
        }
        
        public long multiplicity() {
            return multiplicity;
        }
        
        public Numeric getValue() {
            return value;
        }
        
        public List<Numeric> expand() {
            List<Numeric> result = new LinkedList<>();
            for (long i = 0L; i < multiplicity; i++) {
                result.add(value);
            }
            return result;
        }
        
        public Stream<Numeric> stream() {
            return expand().stream();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 17 * hash + Objects.hashCode(this.value);
            return hash;
        }
        
        @Override
        public boolean equals(Object o) {
            if (o instanceof ElementTuple) {
                ElementTuple that = (ElementTuple) o;
                return this.value.equals(that.value);
            } else if (o instanceof Numeric) {
                Numeric that = (Numeric) o;
                return this.value.equals(that);
            }
            return false;
        }
    }
    private final HashSet<ElementTuple> internal = new HashSet<>();
    
    public NumericMultiset(Collection<Numeric> source) {
        Iterator<Numeric> iter = source.iterator();
        while (iter.hasNext()) {
            this.append(iter.next());
        }
    }
    
    /**
     * Standard no-args constructor.
     */
    public NumericMultiset() { }
    
    /**
     * Copy constructor.
     * @param multiset the multiset from which to copy
     */
    public NumericMultiset(NumericMultiset multiset) {
        this.internal.addAll(multiset.internal);
    }

    @Override
    public long multiplicity(Numeric element) {
        return internal.parallelStream().filter(x -> x.value.equals(element)).findAny()
                .orElseThrow().multiplicity;
    }

    @Override
    public Set<Numeric> asSet() {
        java.util.Set<Numeric> temp = internal.stream().map(x -> x.value).collect(Collectors.toSet());
        return new NumericSet(temp);
    }

    @Override
    public long cardinality() {
        return internal.parallelStream().mapToLong(ElementTuple::multiplicity).sum();
    }

    @Override
    public boolean countable() {
        return true;
    }

    @Override
    public boolean contains(Numeric element) {
        return internal.parallelStream().anyMatch(x -> x.value.equals(element));
    }

    @Override
    public void append(Numeric element) {
        for (ElementTuple t : internal) {
            if (t.getValue().equals(element)) {
                t.increment();
                return;
            }
        }
        // not found, so we need to add a brand new tuple to the set
        if (!internal.add(new ElementTuple(element))) {
            Logger.getLogger(NumericMultiset.class.getName()).log(Level.FINER, "Attempted to append {0}, but multiset failed to add a new ElementTuple unexpectedly.", element);
        }
    }

    @Override
    public void remove(Numeric element) {
        for (ElementTuple t : internal) {
            if (t.getValue().equals(element)) {
                t.decrement();
                if (t.multiplicity == 0L) {
                    // there are no instances of this numeric left in the multiset
                    // therefore, we should remove the ElementTuple holding it
                    if (!internal.remove(t)) {
                        Logger.getLogger(NumericMultiset.class.getName()).log(Level.WARNING, "Failed to remove ElementTuple from internal HashSet.");
                    }
                }
                return;
            }
        }
    }

    @Override
    public Set<Numeric> union(Set<Numeric> other) {
        NumericMultiset union = new NumericMultiset(this);
        Iterator<Numeric> iter = other.iterator();
        while (iter.hasNext()) {
            union.append(iter.next());
        }
        return union;
    }

    @Override
    public Set<Numeric> intersection(Set<Numeric> other) {
        NumericMultiset intersec = new NumericMultiset();
        
        Iterator<Numeric> iter = internal.stream().map(x -> x.value).iterator();
        while (iter.hasNext()) {
            Numeric element = iter.next();
            if (other.contains(element)) {
                intersec.append(element);
            }
        }
        
        return intersec;
    }

    @Override
    public Set<Numeric> difference(Set<Numeric> other) {
        NumericMultiset diff = new NumericMultiset(this);
        
        Iterator<Numeric> iter = other.iterator();
        while (iter.hasNext()) {
            Numeric element = iter.next();
            // note we're checking the internal store, then removing from the copy
            if (this.contains(element)) {
                diff.remove(element);
            }
        }
        
        return diff;
    }
    
    public <T extends Numeric> Multiset<T> coerceTo(Class<T> clazz) throws CoercionException {
        final NumericMultiset parent = this;
        // first, check to see that all the elements of this multiset can be
        // coerced to the target type
        boolean coercible = internal.parallelStream().map(ElementTuple::getValue).allMatch(x -> x.isCoercibleTo(clazz));
        if (!coercible) {
            throw new CoercionException("Cannot coerce elements of NumericMultiset to " + clazz.getTypeName(), Numeric.class, clazz);
        }
        
        return new Multiset<T>() {
            @Override
            public long multiplicity(T element) {
                return parent.multiplicity(element);
            }

            @Override
            public Set<T> asSet() {
                try {
                    return ((NumericSet) parent.asSet()).coerceTo(clazz);
                } catch (CoercionException ex) {
                    Logger.getLogger(NumericMultiset.class.getName()).log(Level.WARNING, "Failed to coerce set element to " + clazz.getTypeName(), ex);
                    throw new IllegalStateException(ex);
                }
            }

            @Override
            public long cardinality() {
                return parent.cardinality();
            }

            @Override
            public boolean countable() {
                return parent.countable();
            }

            @Override
            public boolean contains(T element) {
                return parent.contains(element);
            }

            @Override
            public void append(T element) {
                parent.append(element);
            }

            @Override
            public void remove(T element) {
                parent.remove(element);
            }

            @Override
            public Set<T> union(Set<T> other) {
                Set<Numeric> temp = (Set<Numeric>) other;
                Set<Numeric> intermediate = parent.union(temp);
                try {
                    return ((NumericMultiset) intermediate).coerceTo(clazz);
                } catch (CoercionException ex) {
                    Logger.getLogger(NumericMultiset.class.getName()).log(Level.SEVERE, "Unable to coerce types in Multiset.union()", ex);
                    throw new UnsupportedOperationException(ex);
                }
            }

            @Override
            public Set<T> intersection(Set<T> other) {
                Set<Numeric> temp = (Set<Numeric>) other;
                Set<Numeric> intermediate = parent.intersection(temp);
                try {
                    return ((NumericMultiset) intermediate).coerceTo(clazz);
                } catch (CoercionException ex) {
                    Logger.getLogger(NumericMultiset.class.getName()).log(Level.SEVERE, "Unable to coerce types in Multiset.intersection()", ex);
                    throw new UnsupportedOperationException(ex);
                }
            }

            @Override
            public Set<T> difference(Set<T> other) {
                Set<Numeric> temp = (Set<Numeric>) other;
                Set<Numeric> intermediate = parent.difference(temp);
                try {
                    return ((NumericMultiset) intermediate).coerceTo(clazz);
                } catch (CoercionException ex) {
                    Logger.getLogger(NumericMultiset.class.getName()).log(Level.SEVERE, "Unable to coerce types in Multiset.difference()", ex);
                    throw new UnsupportedOperationException(ex);
                }
            }

            @Override
            public Iterator<T> iterator() {
                return parent.internal.stream().flatMap(ElementTuple::stream).map(this::mapper).iterator();
            }
            
            private T mapper(Numeric value) {
                try {
                    return (T) value.coerceTo(clazz);
                } catch (CoercionException ex) {
                    Logger.getLogger(NumericMultiset.class.getName()).log(Level.WARNING, "Problems coercing NumericMultiset to generic Multiset.", ex);
                    throw new IllegalStateException(ex);
                }
            }
        };
    }

    @Override
    public Iterator<Numeric> iterator() {
        return internal.stream().flatMap(ElementTuple::stream).iterator();
    }
    
}

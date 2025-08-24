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
package tungsten.types.transforms;

import tungsten.types.Numeric;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ComplexPolarImpl;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;

/**
 * An implementation of FFT which uses recursion and the Fork/Join framework
 * first introduced in Java&nbsp;7.  It requires the following of the input:
 * <ul>
 * <li>The input must have an even number of elements.</li>
 * <li>The input elements must already be instances of {@link ComplexType}.</li>
 * </ul>
 * This work is based heavily on an FFT implementation provided by Princeton.
 *
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @see <a href="https://introcs.cs.princeton.edu/java/97data/FFT.java.html">Princeton's FFT implementation</a>
 */
public class FastFourierTransform implements Function<List<ComplexType>, List<ComplexType>> {
    private final MathContext mctx;

    /**
     * Construct a new instance of {@code FastFourierTransform} using
     * the given {@code MathContext} for all internal operations.
     * @param mctx the {@code MathContext} governing all internal mathematical operations
     */
    public FastFourierTransform(MathContext mctx) {
        this.mctx = mctx;
    }

    /**
     * Compute the Fast Fourier Transform (FFT) upon the given
     * {@code List} of complex values and return the result
     * as a list of complex values.
     * @param t the function argument, a list of complex data values
     * @return a list of complex values which is the result of applying the FFT to the argument
     */
    @Override
    public List<ComplexType> apply(List<ComplexType> t) {
        ForkJoinPool commonPool = ForkJoinPool.commonPool();
        FFTRecursiveTask task = new FFTRecursiveTask(t);
        return commonPool.invoke(task);
    }
    
    private class FFTRecursiveTask extends RecursiveTask<List<ComplexType>> {
        private final List<ComplexType> source;
        
        private FFTRecursiveTask(List<ComplexType> source) {
            this.source = source;
        }

        @Override
        protected List<ComplexType> compute() {
            int length = source.size();
            if (length == 1) {
                // singletonList is still slightly more performant than List.of
                // so leaving this with Collections.singletonList() since this is performance critical
                // see https://dzone.com/articles/singleton-list-showdown-collectionssingletonlist-v
                return Collections.singletonList(source.get(0));
            } else if (length % 2 != 0) {
                throw new IllegalStateException("Fourier transform requires an even-length List");
            }
            FFTRecursiveTask[] tasks = createSubtasks();
            
            ForkJoinTask.invokeAll(tasks[0], tasks[1]);
            return combine(tasks[0].join(), tasks[1].join());
        }
        
        private FFTRecursiveTask[] createSubtasks() {
            SplitTuple split = splitList(source);
            List<ComplexType> q = split.getEvenElements();  // even
            List<ComplexType> r = split.getOddElements();  // odd
            return new FFTRecursiveTask[] { new FFTRecursiveTask(q), new FFTRecursiveTask(r) };
        }
    }
    
    private List<ComplexType> combine(List<ComplexType> q, List<ComplexType> r) {
        assert q.size() == r.size();
        final RealImpl one = new RealImpl(BigDecimal.ONE, mctx, true);
        final RealImpl negtwo = new RealImpl("-2", true);
        negtwo.setMathContext(mctx);
        RealType negtwopiovern = (RealType) Pi.getInstance(mctx).multiply(negtwo)
                .divide(new RealImpl(BigDecimal.valueOf(2L * q.size())));
        List<ComplexType> result = new ArrayList<>(q.size() * 2);
        
        for (int i = 0; i < q.size(); i++) {
            RealType kth = (RealType) negtwopiovern.multiply(new RealImpl(BigDecimal.valueOf(i), mctx, true));
            ComplexType wk = new ComplexPolarImpl(one, kth);
            Numeric wkRprod = wk.multiply(r.get(i));
            result.set(i, (ComplexType) q.get(i).add(wkRprod));
            result.set(i + q.size(), (ComplexType) q.get(i).subtract(wkRprod));
        }
        return result;
    }
    
    private SplitTuple splitList(List<ComplexType> source) {
        List<ComplexType> evenElements;
        List<ComplexType> oddElements;
        final int n = source.size() / 2;
        evenElements = new ArrayList<>(n);
        oddElements  = new ArrayList<>(n);
        
        for (int i = 0; i < source.size(); i++) {
            switch (i % 2) {
                case 0:
                    evenElements.add(source.get(i));
                    break;
                case 1:
                    oddElements.add(source.get(i));
                    break;
            }
        }
        assert evenElements.size() == oddElements.size();
        return new SplitTuple(evenElements, oddElements);
    }

    private static class SplitTuple {
        private final List<ComplexType> evenElements;
        private final List<ComplexType> oddElements;

        SplitTuple(List<ComplexType> even, List<ComplexType> odd) {
            this.evenElements = even;
            this.oddElements  = odd;
        }

        public List<ComplexType> getEvenElements() {
            return evenElements;
        }

        public List<ComplexType> getOddElements() {
            return oddElements;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SplitTuple that = (SplitTuple) o;
            return evenElements.equals(that.evenElements) && oddElements.equals(that.oddElements);
        }

        @Override
        public int hashCode() {
            return Objects.hash(evenElements, oddElements);
        }
    }
}

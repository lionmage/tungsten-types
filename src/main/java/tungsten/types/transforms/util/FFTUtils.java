package tungsten.types.transforms.util;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
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

import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ComplexRectImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.transforms.FastFourierTransform;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utility methods pertaining to the application of Fast Fourier Transforms (FFTs).
 * As with {@link FastFourierTransform}, most of the logic here was derived from work
 * published by Princeton. Concurrency (beyond that which is provided by {@link FastFourierTransform})
 * is leveraged where feasible.
 *
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 * @see <a href="https://introcs.cs.princeton.edu/java/97data/FFT.java.html">Princeton's FFT implementation</a>
 */
public class FFTUtils {
    public static List<ComplexType> inverseFFT(List<ComplexType> source, MathContext ctx) {
        FastFourierTransform fft = new FastFourierTransform(ctx);

        List<ComplexType> conj = source.stream().map(ComplexType::conjugate).collect(Collectors.toList());
        List<ComplexType> intermediate = fft.apply(conj);
        conj = intermediate.stream().map(ComplexType::conjugate).collect(Collectors.toList());
        final RealType scale = new RealImpl(BigDecimal.ONE.divide(BigDecimal.valueOf(source.size()), ctx), ctx);
        return conj.stream().map(z -> (ComplexType) z.multiply(scale)).collect(Collectors.toList());
    }

    public static List<ComplexType> circularConvolution(List<ComplexType> x, List<ComplexType> y, MathContext ctx) {
        final int N = x.size();
        if (N != y.size()) {
            throw new IllegalArgumentException("Dimensions of Lists must agree");
        }
        final FastFourierTransform fft = new FastFourierTransform(ctx);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<List<ComplexType>> xfft = () -> fft.apply(x);
        Callable<List<ComplexType>> yfft = () -> fft.apply(y);
        Future<List<ComplexType>> xresult = executor.submit(xfft);
        Future<List<ComplexType>> yresult = executor.submit(yfft);
        // element-wise multiply the result
        List<ComplexType> aggregate = new ArrayList<>(N);  // we know exactly how many elements this will be
        try {
            List<ComplexType> xTrans = xresult.get();
            List<ComplexType> yTrans = yresult.get();
            for (int idx = 0; idx < N; idx++) {
                aggregate.add((ComplexType) xTrans.get(idx).multiply(yTrans.get(idx)));
            }
            return inverseFFT(aggregate, ctx);
        } catch (InterruptedException inte) {
            Logger.getLogger(FFTUtils.class.getName()).log(Level.SEVERE,
                    "Execution of circular convolution was interrupted.", inte);
            throw new IllegalStateException("Calculation interrupted", inte);
        } catch (ExecutionException ex) {
            Logger.getLogger(FFTUtils.class.getName()).log(Level.SEVERE,
                    "One of the FFT operations failed during execution.", ex);
            throw new IllegalStateException(ex);
        } finally {
            executor.shutdownNow();
        }
    }

    public static List<ComplexType> linearConvolution(List<ComplexType> x, List<ComplexType> y, MathContext ctx) {
        final ComplexType zero = new ComplexRectImpl(new RealImpl(BigDecimal.ZERO, ctx));
        List<ComplexType> xExt = new ArrayList<>(x.size() * 2);
        List<ComplexType> yExt = new ArrayList<>(y.size() * 2);
        // copy the contents of x, then pad with zero
        xExt.addAll(x);
        xExt.addAll(Collections.nCopies(x.size(), zero));
        // and do the same for y
        yExt.addAll(y);
        yExt.addAll(Collections.nCopies(y.size(), zero));
        return circularConvolution(xExt, yExt, ctx);
    }
}

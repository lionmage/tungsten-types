package tungsten.types.transforms.util;

import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.transforms.FastFourierTransform;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.stream.Collectors;

public class FFTUtils {
    public static List<ComplexType> inverseFFT(List<ComplexType> source, MathContext ctx) {
        FastFourierTransform fft = new FastFourierTransform(ctx);

        List<ComplexType> conj = source.stream().map(ComplexType::conjugate).collect(Collectors.toList());
        List<ComplexType> intermediate = fft.apply(conj);
        conj = intermediate.stream().map(ComplexType::conjugate).collect(Collectors.toList());
        final RealType scale = new RealImpl(BigDecimal.ONE.divide(BigDecimal.valueOf(source.size()), ctx), ctx);
        return conj.stream().map(z -> (ComplexType) z.multiply(scale)).collect(Collectors.toList());
    }
}

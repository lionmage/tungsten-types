package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.One;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.ParameterizedType;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Product<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> {
    private final Class<R> resultClass = (Class<R>) ((Class) ((ParameterizedType) this.getClass()
            .getGenericSuperclass()).getActualTypeArguments()[1]);
    private final List<UnaryFunction<T, R>> terms = new ArrayList<>();


    public Product(String argName) {
        super(argName);
    }

    public Product(UnaryFunction<T, R>... productOf) {
        super("x");
        Arrays.stream(productOf).filter(f -> !(f instanceof Const)).forEach(terms::add);
        // combine all const terms into one
        try {
            R prodOfConstants = (R) Arrays.stream(productOf).filter(Const.class::isInstance)
                    .map(Const.class::cast).map(Const::inspect)
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply)
                    .coerceTo(resultClass);
            if (!One.isUnity(prodOfConstants)) terms.add(Const.getInstance(prodOfConstants));
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Constant sum cannot be coerced to function return type", e);
        }
    }

    public Product(String argName, UnaryFunction<T, R> first, UnaryFunction<T, R> second) {
        super(argName);
        terms.add(first);
        terms.add(second);
    }

    public void add(UnaryFunction<T, R> term) {
        if (term instanceof Const) {
            try {
                R prodOfConstants = (R) parallelStream().filter(Const.class::isInstance)
                        .map(Const.class::cast).map(Const::inspect)
                        .reduce(((Const) term).inspect(), Numeric::multiply)
                        .coerceTo(resultClass);
                terms.removeIf(Const.class::isInstance);
                if (!One.isUnity(prodOfConstants)) terms.add(Const.getInstance(prodOfConstants));
            } catch (CoercionException e) {
                throw new IllegalArgumentException("Constant sum cannot be coerced to function return type", e);
            }
        } else {
            terms.add(term);
        }
    }

    public long termCount() {
        return stream().count();
    }
    @Override
    public R apply(ArgVector<T> arguments) {
        try {
            R result = (R) terms.parallelStream().map(f -> f.apply(arguments))
                    .map(Numeric.class::cast)
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply).coerceTo(resultClass);
            return result;
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to coerce result to " + resultClass.getTypeName(), e);
        }
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return terms.parallelStream().map(f -> f.inputRange(argName))
                .reduce(RangeUtils.ALL_REALS, Range::chooseNarrowest);
    }

    public Stream<UnaryFunction<T, R>> stream() {
        return terms.stream();
    }

    public Stream<UnaryFunction<T, R>> parallelStream() {
        return terms.parallelStream();
    }

    @Override
    public String toString() {
        return "\u220F\u2009\u0192\u2099(" + getArgumentName() + "), N = " + termCount();
    }
}

package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.ClassTools;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.RangeUtils;

import java.math.MathContext;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A representation of the function &fnof;(x)&nbsp;=&nbsp;&minus;x
 * <br/>Not terribly useful by itself, but it is very handy for composition
 * of differentiable functions.
 *
 * @param <T> the type of the function's sole input parameter
 * @param <R> the type of the function's output
 */
public class Negate<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> {
    private Class<R> rtnClazz;

    private Negate() {
        super("x");
    }

    public static <T extends Numeric, R extends Numeric> Negate<T, R> getInstance(Class<R> clazz) {
        Negate<T, R> instance = new Negate<>();
        instance.rtnClazz = clazz;
        return instance;
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        try {
            return (R) arguments.elementAt(0L).negate().coerceTo(rtnClazz);
        } catch (CoercionException e) {
            throw new ArithmeticException("Could not coerce result of negation.");
        }
    }

    private static final RealType NEGONE_CMP = new RealImpl("-1");

    public static boolean isNegateEquivalent(UnaryFunction<?, ?> fn) {
        if (fn instanceof Negate) return true;
        if (fn instanceof Product) {
            // for a Product to qualify, it has to have at least 1 non-constant term,
            // and the product of the constant terms must be -1
            Product<?, ?> prod = (Product<?, ?>) fn;
            if (prod.termCount() < 2L || prod.stream().allMatch(Const.class::isInstance)) return false;
            Numeric coeffProd =  prod.stream().filter(Const.class::isInstance).map(c -> ((Const<Numeric, Numeric>) c).inspect())
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply);
            try {
                RealType realProd = (RealType) coeffProd.coerceTo(RealType.class);
                return realProd.compareTo(NEGONE_CMP) == 0;
            } catch (CoercionException e) {
                Logger.getLogger(Negate.class.getName()).log(Level.SEVERE,
                        "Product of all constant terms is {}, but could not be coerced to RealType for comparison.",
                        coeffProd);
                throw new IllegalStateException(e);
            }
        }

        return false;
    }


    @Override
    public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
        if (before instanceof Negate) {
            if (this.getComposedFunction().isPresent()) {
                return this.getComposedFunction().get().forReturnType(rtnClazz);
            } else if (this.getOriginalFunction().isPresent()) {
                return this.getOriginalFunction().get();
            }
        }
        return super.composeWith(before);
    }


    @Override
    public <R2 extends R> UnaryFunction<T, R2> andThen(UnaryFunction<R, R2> after) {
        if (after instanceof Negate) {
            List<Class<?>> argClasses = ClassTools.getTypeArguments(NumericFunction.class, after.getClass());
            Class<R2> clazz = argClasses.get(1) == null ? (Class<R2>) rtnClazz : (Class<R2>) argClasses.get(1);
            if (this.getOriginalFunction().isPresent()) {
                return this.getOriginalFunction().get().forReturnType(clazz);
            } else {
                final Logger logger = Logger.getLogger(Negate.class.getName());
                logger.log(Level.WARNING,
                        "andThen() called on Negate with a function {} (classname {}), but this Negate has no original function",
                        new Object[] { after, after.getClass().getTypeName() });
                logger.log(Level.INFO, "This instance has a composed function: {}", this.getComposedFunction().isPresent());
                this.getComposedFunction().ifPresent(f -> {
                    logger.log(Level.INFO, "The composed function is {} (classname {})",
                            new Object[] { f, f.getClass().getTypeName() });
                });
                logger.log(Level.INFO, "This instance has a composing function: {}", this.getComposingFunction().isPresent());
                this.getComposingFunction().ifPresent(f -> {
                    logger.log(Level.INFO, "The composing function is {} (classname {})",
                            new Object[] { f, f.getClass().getTypeName() });
                });
            }
        }
        return super.andThen(after);
    }

    @Differentiable
    public UnaryFunction<T, R> diff() {
        final R response = OptionalOperations.dynamicInstantiate(rtnClazz, "-1");

        return Const.getInstance(response);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }
}

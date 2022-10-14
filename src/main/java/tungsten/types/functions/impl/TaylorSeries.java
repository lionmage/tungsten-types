package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.functions.MetaFunction;
import tungsten.types.functions.NumericFunction;

public class TaylorSeries<T extends Numeric, R extends Numeric> extends MetaFunction<T, R, R> {
    @Override
    public NumericFunction<T, R> apply(NumericFunction<T, R> inputFunction) {
        return null;
    }
}

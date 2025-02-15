package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.functions.impl.ProxyFunction;

/**
 * This interface can be applied to any function that can benefit from having a table of pre-computed
 * values.  The sole method returns a {@link ProxyFunction} that promises to return a pre-computed
 * value for a given input, if one exists.  Such a function can be used to return approximations
 * for a difficult-to-compute function, or to short-circuit calculation of certain common values.
 * @param <T> the input type
 * @param <R> the return type
 */
public interface Proxable<T extends Numeric & Comparable<? super T>, R extends Numeric> {
    /**
     * Obtain a proxy function for this function.
     * @return the proxy function
     */
    ProxyFunction<T, R> obtainProxy();
}

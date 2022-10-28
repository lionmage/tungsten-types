package tungsten.types.functions;

import tungsten.types.Range;
import tungsten.types.numerics.RealType;

/**
 * An interface to be applied to periodic functions.
 * Implementing this interface is sufficient to identify that a function is periodic,
 * eliminating the need to spend computational resources figuring out whether a
 * function is periodic.
 * <br/>
 * The obvious danger is in mis-applying this interface to a non-periodic function.
 * Doing so may lead to incorrect behavior.
 */
public interface Periodic {
    /**
     * Returns the principal range of this periodic function.
     *
     * @return the range over which this function is primarily defined
     */
    Range<RealType> principalRange();

    /**
     * Returns the length of the period of this function.
     *
     * @return the period, typically equivalent to the upper bound minus the lower bound of the principal range
     */
    RealType period();
}

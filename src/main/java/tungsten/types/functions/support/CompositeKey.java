package tungsten.types.functions.support;

import tungsten.types.numerics.RealType;

import java.util.Objects;

/**
 * A simple implementation of a key intended for use with a
 * {@link java.util.Map} tracking Taylor polynomials by
 * their order (number of terms) and a<sub>0</sub>, the point
 * around which a given Taylor polynomial is generated.
 * <br>
 * Note that this class implements {@link Comparable} so that
 * it may be used in non-hash-based {@link java.util.Map}
 * implementations. The default behavior is to first compare
 * by a<sub>0</sub> values, and then by polynomial order.
 */
public class CompositeKey implements Comparable<CompositeKey> {
    private final Long order;
    private final RealType a0;

    /**
     * Construct a key given a polynomial order and a<sub>0</sub> values.
     * @param order a Taylor polynomial's order, its number of terms
     * @param a0    the point around which a Taylor polynomial is generated, a<sub>0</sub>
     */
    public CompositeKey(Long order, RealType a0) {
        this.order = order;
        this.a0 = a0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompositeKey that = (CompositeKey) o;
        return order.equals(that.order) && a0.equals(that.a0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, a0);
    }

    @Override
    public int compareTo(CompositeKey compositeKey) {
        int cmp = this.a0.compareTo(compositeKey.a0);
        if (cmp == 0) {
            cmp = this.order.compareTo(compositeKey.order);
        }
        return cmp;
    }
}

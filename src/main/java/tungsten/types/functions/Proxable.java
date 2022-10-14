package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.functions.impl.ProxyFunction;

public interface Proxable<T extends Numeric & Comparable<? super T>, R extends Numeric> {
    public ProxyFunction<T, R> obtainProxy();
}

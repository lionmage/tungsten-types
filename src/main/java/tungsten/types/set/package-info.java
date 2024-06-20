/**
 * This package is home to all {@link tungsten.types.Set} sub-interfaces and implementations.
 * Currently, there are no sub-interfaces, but {@link tungsten.types.set.impl} contains
 * all concrete implementations.<br>
 * All {@code Set}s attempt to provide an interface that is as close to a mathematical set
 * as possible.  Though superficially similar to {@link java.util.Set}, these sets provide
 * much greater functionality, including a complete or near-complete complement of methods
 * implementing typical set algebra operations.<br>
 * Note that while some implementations of {@link tungsten.types.Set} have internal
 * implementations that may leverage e.g. {@link java.util.TreeSet}, not all do.
 * For example, {@link tungsten.types.set.impl.PrimeNumbers} keeps an internal cache of
 * primes, but does not rely on this exclusively for operations such as testing a value
 * for set membership.
 */
package tungsten.types.set;

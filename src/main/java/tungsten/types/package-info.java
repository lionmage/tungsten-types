/**
 * This base package includes all of the root interfaces for this type system.
 * These basic interfaces are:
 * <ul>
 *     <li>{@link tungsten.types.Numeric}, the root type for all subtypes found in {@link tungsten.types.numerics}</li>
 *     <li>{@link tungsten.types.Set} and {@link tungsten.types.Multiset}, which encapsulate the mathematical
 *       concepts of sets and multisets (the latter is also known as a bag)</li>
 *     <li>{@link tungsten.types.Range} and {@link tungsten.types.NotchedRange} encapsulate ranges of values, and
 *       can be used for any {@link tungsten.types.Numeric} subtype that implements {@link java.lang.Comparable}</li>
 *     <li>{@link tungsten.types.Vector} maps directly onto the mathematical concept of a vector</li>
 *     <li>{@link tungsten.types.Matrix} represents a matrix in 2&nbsp;dimensions and provides default implementations
 *       of several common operations</li>
 * </ul>
 * There is also the enum {@link tungsten.types.Axis} that represents coordinate axes.<br>
 *
 * Note that there is potential confusion between {@link java.util.Set} and {@link tungsten.types.Set}, but it is
 * worth noting that this can be ameliorated by only using references to {@link java.util.Set}-derived objects
 * using either reference types that are subinterfaces of {@link java.util.Set}, or else the specific type of the
 * concrete subclass of {@link java.util.Set}.  In this project, this is mainly a concern when implementing logic
 * or behavior in a concrete subclass of {@link tungsten.types.Set} that leverages a subclass of {@link java.util.Set}
 * internally.<br>
 * A similar problem can crop up involving confusion between {@link java.util.Vector} and {@link tungsten.types.Vector}.
 * In this case, however, {@link java.util.Vector} has been largely deprecated since the Java Collections Framework
 * has evolved and matured. I can't think of anyone who would use {@link java.util.Vector} in new Java code when
 * there are so many better {@link java.util.List} implementations available.
 * @since 0.1
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
package tungsten.types;

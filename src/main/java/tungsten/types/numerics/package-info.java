/**
 * This package contains all sub-interfaces of {@link tungsten.types.Numeric}, one each
 * for the subtypes that are supported, as well as concrete implementations in
 * {@link tungsten.types.numerics.impl}.  Currently, the supported numeric types
 * are integer, rational, real, and complex.<br>
 * Some numeric types have multiple implementations.  For example:
 * <ul>
 *     <li>{@link tungsten.types.numerics.ComplexType} has 2 general implementations and 1 constant:
 *     <ul>
 *         <li>{@link tungsten.types.numerics.impl.ComplexRectImpl} represents complex numbers
 *           internally as a rectangular pair of real and imaginary components</li>
 *         <li>{@link tungsten.types.numerics.impl.ComplexPolarImpl} uses a polar
 *           representation consisting of a modulus (magnitude) and an argument (angle)</li>
 *         <li>{@link tungsten.types.numerics.impl.ImaginaryUnit} is a constant representing&nbsp;&#x2148;</li>
 *     </ul>
 *     </li>
 *     <li>{@link tungsten.types.numerics.RealType} has multiple implementations and is the
 *       base type of multiple constants.  Some examples:
 *       <ul>
 *           <li>{@link tungsten.types.numerics.impl.RealImpl} is the workhorse for real values</li>
 *           <li>{@link tungsten.types.numerics.impl.ContinuedFraction} provides a representation
 *             of real values as a sequence of {@code long}-valued terms, and offers a way to
 *             precisely represent many irrational values (e.g., quadratic irrationals, Euler's number)</li>
 *           <li>{@link tungsten.types.numerics.impl.Pi} provides an arbitrarily precise approximation
 *             of &pi; to as many decimal places as {@code MathContext} supports</li>
 *           <li>{@link tungsten.types.numerics.impl.RealInfinity} provides a real representation
 *             of +&infin; and &minus;&infin;</li>
 *       </ul>
 *     </li>
 * </ul>
 * This package also includes two enums necessary to support these types,
 * {@link tungsten.types.numerics.Sign} (which encapsulates whether a value is
 * negative or positive) and {@link tungsten.types.numerics.NumericHierarchy}
 * (which provides a ladder of coercibility, among other functions).
 * @since 0.1
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
package tungsten.types.numerics;

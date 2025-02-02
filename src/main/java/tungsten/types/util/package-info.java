/**
 * A package containing multiple utility classes and helper classes.<br>
 * Utility classes include:
 * <ul>
 *     <li>{@link tungsten.types.util.ClassTools} covers low-level class operations</li>
 *     <li>{@link tungsten.types.util.OptionalOperations} contains methods to perform some common operations
 *       on different types</li>
 *     <li>{@link tungsten.types.util.UnicodeTextEffects} embodies all things Unicode related to rendering
 *       various data types in text</li>
 *     <li>{@link tungsten.types.util.ANSITextEffects} provides text effects such as bold, italic, and
 *       colored highlighting</li>
 *     <li>{@link tungsten.types.util.RangeUtils}, which contains methods to generate and manipulate
 *       {@link tungsten.types.Range}s</li>
 *     <li>{@link tungsten.types.util.MathUtils} provides basic functions, including trigonometric, and various
 *       other operations, such as matrix decomposition</li>
 * </ul>
 * Helper classes include:
 * <ul>
 *     <li>{@link tungsten.types.util.AngularDegrees}, which provides a way to convert to and from degrees
 *       in decimal or DMS notation</li>
 *     <li>{@link tungsten.types.util.BernoulliNumbers}</li>
 *     <li>{@link tungsten.types.util.CombiningIterator}, an {@link java.util.Iterator} designed to combine two
 *       iterations into one</li>
 *     <li>{@link tungsten.types.util.LRUCache}, a least-recently-used cache with configurable size</li>
 *     <li>{@link tungsten.types.util.GosperTermIterator}, used for arithmetic operations on continued
 *       fractions by consuming two {@code Iterator<Long>} instances and returning a third</li>
 *     <li>{@link tungsten.types.util.RationalCFTermAdapter}, used for incorporating rational values in
 *       continued fraction operations, presenting terms as an {@code Iterator<Long>}</li>
 *     <li>{@link tungsten.types.util.CFCleaner}, used for cleaning up in-flight continued fraction terms</li>
 * </ul>
 * The {@link tungsten.types.util.rendering} package contains textual rendering strategies for various data types.
 *
 * @since 0.1
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
package tungsten.types.util;

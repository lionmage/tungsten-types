# System Properties


Tungsten defines certain system properties which allow for
customization of behaviors.  These properties may be set from
the Java command line as follows:  
`java -Dtungsten.types.numerics.foo=true`

## Table of Properties
| Property                                                                          | Type    | Default Value | Explanation                                                                                                                                                 |
|:----------------------------------------------------------------------------------|---------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `tungsten.types.numerics.ComplexType.extended.enable`                             | Boolean | false         | If set to true, enables extended complex numbers.                                                                                                           |
| `tungsten.types.numerics.MathUtils.prefer.native`                                 | Boolean | true          | If set to true, uses Java's inbuilt methods for computing some functions (e.g., `BigDecimal.pow()`), which are typically faster but less accurate.          |
| `tungsten.types.numerics.ComplexRectImpl.fastMagnitude.enable`                    | Boolean | false         | If true, enables faster computation of the magnitude of complex numbers in rectangular format at the expense of less accurate identification of the result. |
| `tungsten.types.set.impl.FibonacciNumbers.epsilonLimit`                           | Real    | (none)        | If provided, this value is parsed as a threshold limit for epsilon values used to approximate phi.                                                          |
| `tungsten.types.numerics.RationalType.reduceForEquals`                            | Boolean | false         | When true, rational values are first reduced before comparison for equality.                                                                                |
| `tungsten.types.numerics.MathUtils.Gamma.termScale`                               | Integer | 2048          | Determines how many terms of the Weierstrass formula for 𝚪 will be computed. This is multiplied by the precision.                                          |
| `tungsten.types.numerics.MathUtils.Gamma.blockSize`                               | Integer | 250           | Determines how many Weierstrass terms are computed per block, thus governs work-per-thread.                                                                 |
| `tungsten.types.numerics.MathUtils.Gamma.zeroNeighborhood`                        | Real    | 0.01          | Determines the maximum magnitude of a value that counts as "close enough to zero" for Gamma function approximation.                                         |
| `tungsten.types.util.rendering.matrix.cell.RealCellRenderer.maxFractionDigits`    | Integer | 4             | Determines the maximum number of digits to render after the decimal point.                                                                                  |
| `tungsten.types.util.rendering.matrix.cell.RealCellRenderer.useEllipses`          | Boolean | true          | If true, appends ellipses to values to be truncated, otherwise rounds.                                                                                      |
| `tungsten.types.Matrix.useFrobenius`                                              | Boolean | false         | If true, use the Frobenius norm instead of the max norm for matrices.                                                                                       |
| `tungsten.types.numerics.MathUtils.ln.rational.threshold`                         | Integer | 250           | When computing ln(x) for a rational value x, determines whether to use the integer approximation for ln().                                                  |
| `tungsten.types.util.rendering.matrix.cell.ComplexCellRenderer.maxFractionDigits` | Integer | 3             | Determines the maximum number of digits to render after the decimal point.                                                                                  |
| `tungsten.types.util.rendering.matrix.cell.ComplexCellRenderer.useEllipses`       | Boolean | true          | If true, appends ellipses to values to be truncated, otherwise rounds.                                                                                      |
| `tungsten.types.numerics.OptionalOperations.slow.matrix.scan`                     | Boolean | false         | When determining the element type of a matrix, setting this to true forces scanning the entire matrix.                                                      |

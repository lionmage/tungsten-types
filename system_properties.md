# System Properties


Tungsten defines certain system properties which allow for
customization of behaviors.  These properties may be set from
the Java command line as follows:  
`java -Dtungsten.types.numerics.foo=true`

## Table of Properties
| Property                                                | Type    | Default Value | Explanation                                                                                                                                        |
|:--------------------------------------------------------|---------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `tungsten.types.numerics.ComplexType.extended.enable`   | Boolean | false         | If set to true, enables extended complex numbers.                                                                                                  |
| `tungsten.types.numerics.MathUtils.prefer.native`       | Boolean | true          | If set to true, uses Java's inbuilt methods for computing some functions (e.g., `BigDecimal.pow()`), which are typically faster but less accurate. |
| `tungsten.types.numerics.ComplexType.promote.precision` | Boolean | false         | If set to true, ensures that complex accessors return values with at least the same precision as the complex numnber itself.                       |
|`tungsten.types.numerics.ComplexRectImpl.fastMagnitude.enable`|Boolean|false|If true, enables faster computation of the magnitude of complex numbers in rectangular format at the expense of less accurate identification of the result.|
|`tungsten.types.set.impl.FibonacciNumbers.epsilonLimit`|Real|(none)|If provided, this value is parsed as a threshold limit for epsilon values used to approximate phi.|


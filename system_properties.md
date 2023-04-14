# System Properties


Tungsten defines certain system properties which allow for
customization of behaviors.  These properties may be set from
the Java command line as follows:  
`java -Dtungsten.types.numerics.foo=true`

## Table of Properties
| Property                                              | Type    | Default Value | Explanation                                                                                                                                        |
|:------------------------------------------------------|---------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `tungsten.types.numerics.ComplexType.extended.enable` | Boolean | false         | If set to true, enables extended complex numbers.                                                                                                  |
| `tungsten.types.numerics.MathUtils.prefer.native`     | Boolean | true          | If set to true, uses Java's inbuilt methods for computing some functions (e.g., `BigDecimal.pow()`), which are typically faster but less accurate. |


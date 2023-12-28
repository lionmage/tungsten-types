# tungsten-types
This is a refactoring and extension of my previous work on tower-of-types.
While trying to build a mathematical language, it's important to have a
robust type system to handle various numeric types.  This includes logic
to coerce between different numeric types, the ability to assemble
mathematical functions and perform operations on them (e.g., derivatives),
and the ability to work with vectors and matrices.

Most Lisp-like languages have these features, but they are lacking in Java.
This project bridges that gap by supplying additional meta-data for all
numeric types:
* There is a concept of exactness.
* There is a distinction between rational and irrational numbers.
* Numerics that are actually comparable implement the Comparable interface.
* Full support for complex numbers in both polar and rectangular form.
* There is implicit coercion during most math operations, as well as explicit coercion available to the developer.
* There are representations of infinity:
  * Abstract representations of positive and negative infinity
  * Real representations of +∞ and -∞
  * If enabled, there is a point at infinity ∞ in the extended complex numbers ℂ∪{∞}
* In addition to basic numeric types, vectors and matrices are fully supported.
  * Real, complex, integer, and generic vector types.
  * Several matrix structures supplied, including row-based, column-based, and fully parametric.
  * Matrix exponential, logarithm, square root, and powers.
  * Matrix decomposition with (QR) and without (LU) pivoting.
* Functions are supported:
  * Single- or multi-variate
  * Basic functional building-blocks are provided with full composition possible
  * Symbolic and numeric differentiation are supported (currently, full support for unary functions only)
* A fairly complete set of (static method) functions are provided in MathUtils:
  * Implementations of trig functions such as sin, cos, and a highly optimized atan.
  * Specialized functions that show up a lot, such as 𝚪(z) and 𝜁(s).
  * Multiple ways to compute exponents for all Numeric data types.
  * Methods for computing factorials, binomial coefficients, etc.
* There is preliminary support for data reduction and curve fitting.
* The generated JAR file has no transitive dependencies whatsoever! Feel free to drop it into your project's lib folder as-is.
* Javadoc and source JARs are generated as part of the build; at minimum, the Javadoc JAR is recommended for your IDE.

Systems like Sage leverage a lot of small Unix-like apps to do the work,
which adheres to the Unix philosophy that each application should only do
one thing, and you should aggregate these apps together using e.g. shell
scripts.  There's nothing wrong with this philosophy, except:
* Performance can't be as good as a monolithic app designed for higher math.
* The world isn't just Unix or Linux, and Windows ports can be problematic.
* The JVM is ubiquitous at this point, so it is a desirable target.

To this point, I was actually asked (during a job interview, no less) why
I didn't just use bc (the command line calculator) to do this stuff.
Several reasons:
* Although bc supports arbitrary precision, it defaults to integer precision.
* Who wants to shell out from within an application just to perform some calculations?
    * Shelling out is costly.
    * Avoiding the context switch by using native math capabilities of the target language is preferable.
* If my goal is to write a Java application, then I should be writing these libraries in Java.
* My understanding is that most implementations of bc don't handle complex numbers natively.
* There are multiple implementations of bc, most of which adhere to a POSIX standard, but all of which have different features.

To be clear: This project is intended to create the foundation of mathematical
applications in Java with an emphasis on practical usability.  The end goal is
to create a mathematical language and environment which leverages this work.

In addition to Java support, I am including preliminary support for Groovy. Right now, this
takes the form of adding methods to basic types so that Groovy can use them with its
built-in operators. This also has the side benefit of making testing much faster using
e.g. GroovyConsole (built into IntelliJ IDEA). Operator overloading has been applied to
both vector and matrix types in addition to all numeric types. Per Groovy convention,
Vector supports negative indexing for the [] operator.

**Note:** I had originally intended to
include Groovy scripts and Categories in this project, but due to classpath limitations
with IntelliJ and its Gradle integration, I have decided to move Groovy-specific stuff
into a [separate project](https://github.com/lionmage/tungsten-groovy).
Methods to support Groovy operator overloading will remain.

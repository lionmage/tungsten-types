# tungsten-types
This is a refactoring and extension of my previous work on tower-of-types.
While trying to build a mathematical language, it is important to have a
robust type system to handle various numeric types.  This includes logic
to coerce between different numeric types, the ability to assemble
mathematical functions and perform operations on them (e.g., derivatives),
and the ability to work with vectors and matrices.

Most Lisp-like languages have these features, but they are lacking in Java.
This project bridges that gap by supplying additional meta-data for all
numeric types:
* There is a concept of exactness.  Values with an exact representation are considered exact. Inexact values taint calculations.
* There is a distinction between rational and irrational numbers.
* Numerics that are actually comparable implement the Comparable interface.
* Full support for complex numbers in both polar and rectangular form.
* Simple continued fractions are supported and may be freely mixed with other numeric types.
  * Arithnetic operations are implemented using Gosper's algorithm.
  * Support for integer exponents and roots allows computing any rational power.
  * Implementations of ℯ, ϕ, and 𝜋 with effectively infinite representations, although 𝜋 is an approximation.
  * Special iterators designed to rehabilitate sequences of terms in a non-standard form.
* There is implicit coercion during most math operations, as well as explicit coercion available to the developer.
* There are representations of infinity:
  * Abstract representations of positive and negative infinity
  * Real representations of +∞ and -∞
  * If enabled, there is a point at infinity ∞ in the extended complex numbers ℂ∪{∞}
* In addition to basic numeric types, vectors and matrices are fully supported.
  * Real, rational, complex, integer, and generic vector types.
  * Several matrix structures supplied, including row-based, column-based, and fully parametric.
  * Block matrices (including Jordan matrices) are fully supported.
  * Matrix exponential, logarithm, square root, and powers.
  * Trigonometric operations on matrices (sin, cos, arcsin, arccos).
  * Kronecker product, Hadamard product, Kronecker sum, and matrix vectorization.
  * Matrix decomposition with (QR) and without (LU) pivoting.
* Functions are supported:
  * Single- or multi-variate
  * Basic functional building-blocks are provided with full composition possible
  * Symbolic and numeric differentiation are supported (currently, full support for unary functions only)
* A fairly complete set of (static method) functions are provided in `MathUtils`:
  * Implementations of trig functions such as sin, cos, and a highly optimized atan.
  * Hyperbolic functions sinh, cosh, tanh.
  * Logarithms are supported for multiple types, including continued fractions.
  * Specialized functions that show up a lot, such as 𝚪(z), ln𝚪(z) and 𝜁(s).
  * Multiple ways to compute exponents for all Numeric data types.
  * Methods for computing factorials, binomial coefficients, etc.
  * Methods for obtaining pseudorandom values:
    * evenly distributed over a given range
    * as Gaussian noise from a thread-safe `Supplier` with a specified mean and standard deviation
  * Concurrency is leveraged for many algorithms to speed up computation.
* There is preliminary support for data reduction and curve fitting.
* Most `toString()` methods attempt to generate an accurate representation in standard mathematical notation using Unicode.
  * Constants render using the appropriate symbol.
  * Composed functions will make a best effort to render the composition symbolically.
* There are Unicode tools for scrubbing inputs and for advanced rendering (e.g., rendering matrices).
* The generated JAR file has no transitive dependencies whatsoever! Feel free to drop it into your project's lib folder as-is.
* Javadoc and source JARs are generated as part of the build; at minimum, the Javadoc JAR is recommended for your IDE.

Systems like Sage leverage a lot of small Unix-like apps to do the work,
which adheres to the Unix philosophy that each application should only do
one thing, and you should aggregate these apps together using e.g. shell
scripts.  There's nothing wrong with this philosophy, except:
* Performance can't be as good as a monolithic app designed for higher math.
* The world isn't just Unix or Linux, and Windows ports can be problematic.  Sage ports to Windows tend to lag behind other operating systems.
* The JVM is ubiquitous at this point, so it is a desirable target.  Scientific and AI workloads are migrating to Java.

To this point, I was actually asked (during a job interview, no less) why
I didn't just use bc (the command line calculator) to do this stuff.
Several reasons:
* Although bc supports arbitrary precision, it defaults to integer precision.
* Who wants to shell out from within an application just to perform some calculations?
    * Shelling out is costly.
    * Avoiding the context switch by using native math capabilities of the target language is preferable.
    * I'm looking at you, Python...
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
Vector supports negative indexing for the [] operator.  Continued fractions support
the [] operator for indexing terms, although negative indexing only works for continued
fractions that are finite (i.e., rational).

**Note:** I had originally intended to
include Groovy scripts and Categories in this project, but due to classpath limitations
with IntelliJ and its Gradle integration, I have decided to move Groovy-specific stuff
into a [separate project](https://github.com/lionmage/tungsten-groovy).
Methods to support Groovy operator overloading will remain.

As of version 0.5, I am publishing packages to GitHub.  This library may be included in Maven
dependencies with the following snippet:

```Maven POM
<!-- this should go in a repositories block -->
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/lionmage/tungsten-types</url>
  <snapshots>
    <enabled>true</enabled>
  </snapshots>
</repository>
...
<!-- and this should go in a dependencies block -->
<dependency>
  <groupId>tungsten</groupId>
  <artifactId>tungsten-types</artifactId>
  <version>0.5</version>
</dependency>
```

...or using Gradle:

```Gradle
dependencies {
    implementation 'tungsten:tungsten-types'
}
// etc. etc.
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/lionmage/tungsten-types")
        // for a public repo, reading should not require credentials
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
        }
   }
}
```
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
* If my goal is to write a Java application, therefore, I should be writing these libraries in Java.
* My understanding is that most implementations of bc don't handle complex numbers natively.
* There are multiple implementations of bc, most of which adhere to a POSIX standard, but all of which have different features.

To be clear: This project is intended to create the foundation of mathematical
applications in Java with an emphasis on practical usability.  The end goal is
to create a mathematical language and environment which leverages this work.

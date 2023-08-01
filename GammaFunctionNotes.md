# Notes on implementing the Gamma function, 𝚪(z)
## A work in progress
To be clear, this work is an ongoing effort.  My initial attempt is no doubt naïve in its use of the infinite
product, but this form is easy to understand and evaluate concurrently.  (More on concurrency later...)
I'll be looking at [the Lanczos approximation](https://en.wikipedia.org/wiki/Lanczos_approximation) as the next
logical step in improving speed and accuracy for my implementation of 𝚪(z).

## Why should we even bother?
Why would we want to compute such a difficult function at all? The function 𝚪(z) is a generalization of factorial
for all rational, real, and complex numbers.  𝚪(n) = (n - 1)! for all positive integers n.
The difficulty comes in computing 𝚪(z) for non-integer values, as this involves an infinite product.
Anywhere you wish to generalize things that use factorials for all real or complex numbers, you need to use
the Gamma function.  One example would be computing binomial coefficients where both n and k are non-integers.

## Where we are at right now
I chose to use the Euler limit form of the formula for 𝚪(z) —
see [formula (29) in this article](https://mathworld.wolfram.com/GammaFunction.html) for reference. This was
probably a poor choice, and the Weierstrass form (formula (15) at the previously linked source) might have proven better.
I simply wanted to avoid some of the inherent complexity of that version. There's also the added burden of
implementing the [Euler-Mascheroni constant](https://mathworld.wolfram.com/Euler-MascheroniConstant.html) with
sufficient accuracy at any given precision (i.e., typically within 1 or 2 ulps).

The central part of the algorithm's initial implementation can be summarized neatly in this code block:

        Numeric result = LongStream.range(1L, iterLimit) // .parallel()
                .mapToObj(k -> gammaTerm(z, k))
                .reduce(z.inverse(), Numeric::multiply);

The `z.inverse()` correlates exactly with the 1/z term in front of the infinite product, and `gammaTerm()`
computes the k^th term of the infinite product.  Each term can itself be computed independently, so no shared
state, just a clean map/reduce style of coding with Java streams.

This thing takes a ridiculous number of iterations to converge.  I mean thousands upon thousands, not just hundreds.
And if you do this in a single-threaded way, that's going to take an awful long time...

Note the commented out `parallel()` in the above code snippet.  Since each term is computed independently given
the argument z and a (long) integer value k, we could speed things up by computing things in parallel fashion.
Multiplication is both commutative and associative for everything up to and including complex numbers, so who
cares if order is not preserved?

With the non-parallel version of the code above, and with an iteration limit set to something I felt was
tolerable (approximately 2N^2 where N is the desired precision) in terms of time taken, I was able to get
about 6 digits of accuracy.  Not great.

## The big problem
Try uncommenting `parallel()` and watch the Stream API relatively quickly come up with a wrong answer, many orders
of magnitude larger than it should be. Far different from previously computed results and clearly not even in the
same ballpark as the expected result.  𝚪(1/2) should evaluate to √π, and I can compute √π directly to an accuracy within
1 or 2 ulps of the requested precision!

So, what's happening here?  My initial thought was that completely unpredictable order of operations means that small
correction terms may be applied to large values first, causing severe rounding error. It turns out that something
else may be going on, and it has to do with the [Riemann Series Theorem](https://en.wikipedia.org/wiki/Riemann_series_theorem),
also known as the [Riemann Rearrangement Theorem](https://sites.math.washington.edu/~morrow/335_16/history%20of%20rearrangements.pdf).
Put simply, if you rearrange the terms of an infinite sum that converges conditionally, you can obtain literally any
real value you like — or you can cause the series to diverge.  You will typically see
this behavior with series that have alternating sign.  Since the Euler limit form is a product, not a sum,
and since its terms don't appear to have any negatives, the Riemann Series Theorem may not apply — but the general
idea that reordering can alter a sum (or a product) of an _infinite_ series still applies.
Regardless, it seems that some ordering is necessary here.

Consider this alternate concurrent implementation:

        final ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<Numeric>> segments = new LinkedList<>();
        final long blockSize = 250L;  // chop the infinite product into manageable segments
        for (long k = 1L; k < iterLimit; k += blockSize) {
            final long endstop = Math.min(k + blockSize, iterLimit);
            final long start = k;
            Callable<Numeric> part = () -> LongStream.range(start, endstop)
                    .mapToObj(k1 -> gammaTerm(z, k1))
                    .reduce(Numeric::multiply).orElseThrow();
            Future<Numeric> segment = executor.submit(part);
            segments.add(segment);
        }
        // now reduce the blocks sequentially to avoid rounding error problems
        Numeric result = segments.stream().map(f -> {
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ArithmeticException("Execution interrupted while computing gamma: " + e.getMessage());
            }
        }).reduce(z.inverse(), Numeric::multiply);
        executor.shutdown();  // we should have exhausted all outstanding term calculations; shut down regardless

OK, not nearly as elegant as the previous implementation, but at least here we get a result that's at least as
good as obtained using the original code in non-parallel mode!  Here, the main problem is subdivided into blocks
of 250 terms to compute and multiply, and that multiplication is performed in-order for each of these blocks.

These results are then reduced into a final result, again performing the reduction in-order.  The concurrency gives
us a bit more headroom, so if we pick 8N^2 as our iteration limit, we can get... a whopping 8 digits of accuracy!
Still not great, but better.  Pushing the iteration limit even higher risks causing memory errors, and I can
confirm that the laptop fans are revving quite a bit higher while running these calculations.

One advantage of this version over the pure Stream-based version is that ParallelStream implementations use
fork-join behind the scenes to do the work, which seems like a bit of overkill here.  I have done plenty of
fork-join code before (see my FFT implementation), but usually that sort of stuff is valuable for recursion.
This code isn't recursive, just highly parallelizable.

A more efficient algorithm is in order here.  I might try the Weierstrass form next, or I might jump straight to
Lanczos.  However, I am disinclined to implement Lanczos for a few reasons:

* The Lanczos approximation only works for the positive-real half of the complex plane. (This can be remedied through the use of a reflection formula.)
* Lanczos requires using constants that are themselves derived/computed values, and which must be carefully chosen for a given precision.
* Due to the above, Lanczos is undesirable for applications where arbitrary precision arithmetic is used, such as this project.
* Choosing a set of constants for a given precision may not be possible, forcing a choice between lesser precision or wasted computation of extra digits.
* The calculation of these magic constants is not trivial, and involves searching a solution space exhaustively for "values that work."
* Most implementations of Lanczos therefore use pre-determined constants to compute 𝚪(z) for a particular floating point type's size (float, double).

## Addendum
I have since settled on Weierstrass, which seems to have reasonable performance and generates results on
par with previous best attempts.  This was made possible in part by implementing a
very efficient algorithm for calculating the Euler-Mascheroni constant that produces as many digits of precision
as we could possibly want.  There are tuning parameters available to increase accuracy and/or concurrency,
available as system properties that can be supplied at runtime.

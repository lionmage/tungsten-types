/*
 * The MIT License
 *
 * Copyright © 2025 Robert Poole <Tarquin.AZ@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package tungsten.types.exceptions;

/**
 * An exception to be thrown when a computational algorithm fails
 * to converge. This exception may optionally carry information
 * about which operation was being attempted as well as the number
 * of iterations that were computed before failure.
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class ConvergenceException extends Exception {
    private static final String UNKNOWN = "(unknown operation)";
    private final long iterCount;
    private final String operation;

    /**
     * Construct a ConvergeceException with an exception message.
     * @param message the exception message
     */
    public ConvergenceException(String message) {
        super(message);
        iterCount = -1L;
        operation = UNKNOWN;
    }

    /**
     * Construct a ConvergeceException with an exception message and a chained exception as the cause.
     * @param message the exception message
     * @param cause   a chained or wrapped exception, the cause of this exception
     */
    public ConvergenceException(String message, Throwable cause) {
        super(message, cause);
        iterCount = -1L;
        operation = UNKNOWN;
    }

    /**
     * Construct a ConvergeceException with an exception message, a chained exception, and
     * an iteration count.
     * @param message        the exception message
     * @param cause          the cause of this exception
     * @param iterationCount the number of iterations computed before this exception was thrown
     */
    public ConvergenceException(String message, Throwable cause, long iterationCount) {
        super(message, cause);
        iterCount = iterationCount;
        operation = UNKNOWN;
    }

    /**
     * Construct a ConvergeceException with an exception message and
     * an iteration count.
     * @param message        the exception message
     * @param iterationCount the number of iterations computed before this exception was thrown
     */
    public ConvergenceException(String message, long iterationCount) {
        super(message);
        iterCount = iterationCount;
        operation = UNKNOWN;
    }

    /**
     * Construct a ConvergeceException with an exception message, a chained exception, and
     * an iteration count.
     * @param message        the exception message
     * @param operation      the operation being performed, e.g. the series being computed
     * @param iterationCount the number of iterations computed before this exception was thrown
     */
    public ConvergenceException(String message, String operation, long iterationCount) {
        super(message);
        this.operation = operation;
        this.iterCount = iterationCount;
    }

    /**
     * If known, returns the number of iterations attempted before
     * the implemented algorithm gave up.  If unknown, returns -1.
     * @return the number of iterations tried before giving up, or -1 if unknown
     */
    public long getIterations() {
        return iterCount;
    }

    /**
     * Provides the name of the operation being performed when algorithmic
     * convergence failed.  This {@code String} could be any value, but
     * typically would be the name of the algorithm, the name of the implementing
     * method, or the name of the actual mathematical function being computed.
     * @return the name of the operation, or a {@code String} denoting that
     *   the actual operation name is unknown
     */
    public String getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        if (iterCount > 0L) {
            return String.format("After %1$d iterations, operation %2$s failed to converge: %3$s", iterCount, operation, getMessage());
        }
        return String.format("Convergence failed for %1$s: %2$s", operation, getMessage());
    }
}

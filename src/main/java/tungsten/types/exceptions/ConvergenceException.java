package tungsten.types.exceptions;

/**
 * An exception to be thrown when a computational algorithm fails
 * to converge. This exception may optionally carry information
 * about which operation was being attempted as well as the number
 * of iterations that were computed before failure.
 */
public class ConvergenceException extends Exception {
    private static final String UNKNOWN = "(unknown operation)";
    private final long iterCount;
    private final String operation;

    public ConvergenceException(String message) {
        super(message);
        iterCount = -1L;
        operation = UNKNOWN;
    }

    public ConvergenceException(String message, Throwable cause) {
        super(message, cause);
        iterCount = -1L;
        operation = UNKNOWN;
    }

    public ConvergenceException(String message, Throwable cause, long iterationCount) {
        super(message, cause);
        iterCount = iterationCount;
        operation = UNKNOWN;
    }

    public ConvergenceException(String message, long iterationCount) {
        super(message);
        iterCount = iterationCount;
        operation = UNKNOWN;
    }

    public ConvergenceException(String message, String operation, long iterationCount) {
        super(message);
        this.operation = operation;
        this.iterCount = iterationCount;
    }

    /**
     * If known, returns the number of iterations attempted before
     * the algorithm implemented gave up.  If unknown, returns -1.
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

package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Term;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A representation of a polynomial term.  This implementation
 * supports integer exponents and multiple variables.
 * @param <T> the numeric type consumed by this term
 * @param <R> the result type for this term
 */
public class PolyTerm<T extends Numeric, R extends Numeric> extends Term<T, R> {
    private static final char DOT_OPERATOR = '\u22C5';
    private final Map<String, Long> powers = new TreeMap<>();
    private R coeff;
    private Class<T> argTypeCache;

    /**
     * Instantiate a polynomial term given a list of variable names, a corresponding list
     * of exponents, and the numeric return type for this term.
     * @param variableNames a list of variables participating in this term
     * @param exponents     a list of integral exponents; must be the same length as {@code variableNames}
     * @param rtnClass      the numeric type of the result computed by this term
     */
    public PolyTerm(List<String> variableNames, List<Long> exponents, Class<R> rtnClass) {
        super(variableNames, rtnClass);
        if (variableNames.size() != exponents.size()) {
            throw new IllegalArgumentException("var and exponent lists must match");
        }
        for (int idx = 0; idx < exponents.size(); idx++) {
            powers.put(variableNames.get(idx), exponents.get(idx));
        }
    }

    /**
     * Instantiate a polynomial term given a coefficient, a list of variable names,
     * and a corresponding list of exponents.
     * The return type of this term is inferred from {@code coefficient}.
     * @param coefficient   a multiplicative coefficient applied in the calculation of this term
     * @param variableNames a list of variables participating in this term
     * @param exponents     a list of integral exponents; must be the same length as {@code variableNames}
     */
    public PolyTerm(R coefficient, List<String> variableNames, List<Long> exponents) {
        this(variableNames, exponents, (Class<R>) ClassTools.getInterfaceTypeFor(coefficient.getClass()));
        this.coeff = coefficient;
    }

    /**
     * This is a convenience constructor to instantiate a polynomial term
     * given a single variable name, a coefficient, and an exponent to be
     * applied to the single designated variable.
     * @param variableName the name of a variable
     * @param coefficient  a multiplicative coefficient for this term
     * @param exponent     an integral exponent
     */
    public PolyTerm(String variableName, R coefficient, long exponent) {
        this(coefficient, List.of(variableName), List.of(exponent));
    }

    private static final Pattern coeffPattern = Pattern.compile("^\\s*([+-]?\\d+[./]?\\d*)\\s?");
    private static final Pattern varPattern = Pattern.compile("(\\w+)\\^([+-]?\\d+)\\s?");

    /**
     * Instantiate a term by parsing a {@code String} representing it.
     * @param init     the textual string representing this polynomial term
     * @param rtnClass the return type of this term
     */
    public PolyTerm(String init, Class<R> rtnClass) {
        super(Collections.emptyList(), rtnClass); // force superclass to instantiate a usable collection
        int startOfVars = 0;
        final String cleaned = init.replace(DOT_OPERATOR, ' ');
        Matcher coeffMatcher = coeffPattern.matcher(cleaned);
        if (coeffMatcher.find()) {
            this.coeff = OptionalOperations.dynamicInstantiate(rtnClass, coeffMatcher.group(1));
            startOfVars = coeffMatcher.end();
        }
        // this probably isn't strictly necessary, but helps the regex matcher avoid
        // non-matching stuff at the beginning of the input string
        String remainder = cleaned.substring(startOfVars);
        Matcher varMatcher = varPattern.matcher(remainder);
        List<String> parsedVarNames = new ArrayList<>();
        while (varMatcher.find()) {
            parsedVarNames.add(varMatcher.group(1));
            powers.put(varMatcher.group(1), Long.valueOf(varMatcher.group(2)));
        }
        varNames.addAll(parsedVarNames);
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        if (checkArguments(arguments)) {
            Numeric accum = coefficient();
            for (long index = 0L; index < arguments.length(); index++) {
                final String argName = arguments.labelForIndex(index);
                Long exponent = powers.getOrDefault(argName, 1L);
                if (exponent == null) exponent = 1L;  // just in case powers stores null mappings

                if (exponent == 0L) continue;
                Numeric intermediate = MathUtils.computeIntegerExponent(arguments.elementAt(index),
                    new IntegerImpl(BigInteger.valueOf(exponent)));
                accum = accum.multiply(intermediate);
            }
            try {
                return (R) accum.coerceTo(getReturnType());
            } catch (CoercionException e) {
                throw new IllegalStateException("While coercing evaluated PolyTerm to return type", e);
            }
        }
        throw new IllegalArgumentException("Input vector " + arguments + " failed check");
    }

    @Override
    public Term<T, R> multiply(Term<T, R> multiplier) {
        R coefficient = (R) this.coefficient().multiply(multiplier.coefficient());
        LinkedHashSet<String> combinedArgs = new LinkedHashSet<>(varNames);
        boolean hasNewArgs = combinedArgs.addAll(Arrays.asList(multiplier.expectedArguments()));
        if (hasNewArgs) {
            Logger.getLogger(PolyTerm.class.getName()).log(Level.INFO,
                    "Combined polynomial term has {0} arguments.", combinedArgs.size());
        }
        HashMap<String, Long> combinedExponents = new HashMap<>(powers);
        for (String varName : multiplier.expectedArguments()) {
            combinedExponents.put(varName, this.order(varName) + multiplier.order(varName));
        }
        List<Long> listOfExponents = combinedArgs.stream().map(combinedExponents::get)
                .collect(Collectors.toList());
        assert listOfExponents.size() == combinedArgs.size();
        return new PolyTerm<>(coefficient,
                new LinkedList<>(combinedArgs), listOfExponents);
    }

    @Override
    public Term<T, R> multiply(Pow<T, R> func) {
        if (func.getComposedFunction().isPresent()) throw new IllegalArgumentException("Cannot multiply Term by composed function");
        LinkedHashSet<String> combinedArgs = new LinkedHashSet<>(varNames);
        final String funArg = func.expectedArguments()[0];
        final IntegerType funExponent = (IntegerType) func.getExponent();  // will throw a ClassCastException if exponent is rational
        boolean hasNewArgs = combinedArgs.add(funArg);
        if (hasNewArgs) {
            Logger.getLogger(PolyTerm.class.getName()).log(Level.INFO,
                    "Combined polynomial term has {0} arguments.", combinedArgs.size());
        }
        HashMap<String, Long> combinedExponents = new HashMap<>(powers);
        combinedExponents.put(funArg, this.order(funArg) + funExponent.asBigInteger().longValueExact());
        List<Long> listOfExponents = combinedArgs.stream().map(combinedExponents::get)
                .collect(Collectors.toList());
        assert listOfExponents.size() == combinedArgs.size();
        return new PolyTerm<>(this.coefficient(),
                new LinkedList<>(combinedArgs), listOfExponents);
    }

    @Override
    public Term<T, R> scale(R multiplier) {
        if (One.isUnity(multiplier)) return this;
        if (Zero.isZero(multiplier)) return new ConstantTerm<>(multiplier);
        List<Long> exponents = varNames.stream().map(powers::get).collect(Collectors.toList());
        return new PolyTerm<>((R) coefficient().multiply(multiplier), varNames, exponents);
    }

    /**
     * Checks if the supplied polynomial term has the same general signature as this one.
     * This means that the two terms have the same arity, the same named variables,
     * and for each variable, the same exponent.
     * @param other the other polynomial term for comparison
     * @return true if the two terms have the same signature
     */
    public boolean hasMatchingSignature(PolyTerm<T, R> other) {
        // ensure the variables match up
        if (Arrays.mismatch(this.expectedArguments(), other.expectedArguments()) >= 0) return false;
        // if the arg name arrays match, let's check the exponents
        for (String varName : varNames) {
            if (!Objects.equals(this.powers.get(varName), other.powers.get(varName))) return false;
        }
        return true;
    }

    @Override
    public R coefficient() {
        try {
            return coeff == null ? (R) One.getInstance(MathContext.UNLIMITED).coerceTo(getReturnType()) : coeff;
        } catch (CoercionException e) {
            throw new IllegalStateException("Error while trying to coerce unity", e);
        }
    }

    @Override
    public long order(String varName) {
        if (!varNames.contains(varName)) return 0L;
        return powers.getOrDefault(varName, 1L);
    }

    @Override
    protected boolean checkArguments(ArgVector<T> arguments) {
        if (argTypeCache == null) {
            argTypeCache = arguments.getElementType();
            Logger.getLogger(PolyTerm.class.getName()).log(Level.INFO,
                    "Determined arg type of PolyTerm is {0}.", argTypeCache.getSimpleName());
        } else if (!argTypeCache.isAssignableFrom(arguments.getElementType())) {
            Logger.getLogger(PolyTerm.class.getName()).log(Level.WARNING,
                    "PolyTerm expected arguments of type {0} but received {1} instead.",
                    new Object[] { argTypeCache, arguments.getElementType() });
            return false;
        }
        for (String argName : arguments.getElementLabels()) {
            Range<RealType> argRange = this.inputRange(argName);
            if (argRange == null) continue;
            try {
                RealType toCheck = (RealType) arguments.forVariableName(argName).coerceTo(RealType.class);
                if (!argRange.contains(toCheck)) return false;
            } catch (CoercionException e) {
                Logger.getLogger(PolyTerm.class.getName()).log(Level.WARNING,
                        "Variable {0} has a real-valued range of {1}, but the value {2} cannot be coerced to RealType.",
                        new Object[] { argName, argRange, arguments.forVariableName(argName) });
                return false;
            }
        }
        return super.checkArguments(arguments);
    }

    @Override
    public Class<T> getArgumentType() {
        return argTypeCache;
    }

    @Override
    public boolean isConstant() {
        // if all the exponents are zero, we are effectively a constant
        if (powers.values().stream().allMatch(l -> l == 0L)) return true;
        return super.isConstant();
    }

    @Differentiable
    public Term<T, R> differentiate(String argName) {
        long order = order(argName);
        try {
            R dcoeff = (R) coefficient().multiply(new IntegerImpl(BigInteger.valueOf(order)))
                    .coerceTo(getReturnType());
            List<String> dargs = new ArrayList<>(varNames);
            TreeMap<String, Long> dpowers = new TreeMap<>(powers);
            if (--order == 0L) {
                dargs.remove(argName);
                dpowers.remove(argName);
            } else {
                dpowers.replace(argName, order);
            }
            List<Long> temp = dargs.stream().map(dpowers::get).collect(Collectors.toList());
            return new PolyTerm<>(dcoeff, dargs, temp);
        } catch (CoercionException e) {
            throw new IllegalStateException("While differentiating a PolyTerm", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        PolyTerm<?, ?> polyTerm = (PolyTerm<?, ?>) o;
        return powers.equals(polyTerm.powers) && Objects.equals(coeff, polyTerm.coeff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), powers, coeff);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        if (!One.isUnity(coefficient())) {
            buf.append(coeff);
        }
        if (!isConstant()) {
            // if we already appended something to the buffer, append U+22C5 (dot operator)
            if (buf.length() > 0) buf.append(DOT_OPERATOR);
            for (String varName : varNames) {
                long exponent = order(varName);
                if (exponent == 0L) continue;
                buf.append(varName);
                if (exponent > 1L || exponent < 0L) {
                    buf.append(UnicodeTextEffects.numericSuperscript(powers.get(varName).intValue()));
                }
                buf.append(DOT_OPERATOR);
            }
            // and trim any trailing special characters
            if (buf.charAt(buf.length() - 1) == DOT_OPERATOR) {
                buf.setLength(buf.length() - 1);
            }
        }
        if (buf.length() == 0 && One.isUnity(coefficient())) return "\uD835\uDFCF";  // bold mathematical 1, surrogate pair for U+1D7CF

        return buf.toString();
    }
}

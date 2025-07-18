package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Term;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.*;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RationalExponentPolyTerm<T extends Numeric, R extends Numeric> extends Term<T, R> {
    private static final char DOT_OPERATOR = '\u22C5';
    private final Map<String, RationalType> powers = new TreeMap<>();
    private R coeff;
    private Class<T> argTypeCache;

    /**
     * Instantiate a polynomial term with a list of variable names, a list of rational exponents,
     * and the return type of this term.
     * @param variableNames the names of the variables consumed by this term
     * @param exponents     a list of rational exponents; must be the same length as {@code variableNames}
     * @param rtnClass      the return type of this term
     */
    public RationalExponentPolyTerm(List<String> variableNames, List<RationalType> exponents, Class<R> rtnClass) {
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
     * @param exponents     a list of rational exponents; must be the same length as {@code variableNames}
     */
    public RationalExponentPolyTerm(R coefficient, List<String> variableNames, List<RationalType> exponents) {
        this(variableNames, exponents, (Class<R>) ClassTools.getInterfaceTypeFor(coefficient.getClass()));
        this.coeff = coefficient;
    }

    /**
     * Copy constructor.
     * @param toCopy the polynomial term to copy
     */
    public RationalExponentPolyTerm(PolyTerm<T, R> toCopy) {
        super(toCopy.getReturnType(), toCopy.expectedArguments());
        this.coeff = toCopy.coefficient();
        for (String varName : varNames) {
            powers.put(varName, new RationalImpl(BigInteger.valueOf(toCopy.order(varName)), BigInteger.ONE));
        }
    }

    private static final Pattern coeffPattern = Pattern.compile("^\\s*([+-]?\\d+[./]?\\d*)\\s?");
    private static final Pattern varPattern = Pattern.compile("(\\w+)\\^([+-]?\\d+/\\d+)\\s?");

    /**
     * Instantiate a term by parsing a {@code String} representing it.
     * @param init     the textual string representing this polynomial term
     * @param rtnClass the return type of this term
     */
    public RationalExponentPolyTerm(String init, Class<R> rtnClass) {
        super(Collections.emptyList(), rtnClass); // force superclass to instantiate a usable collection
        int startOfVars = 0;
        Matcher coeffMatcher = coeffPattern.matcher(init);
        if (coeffMatcher.find()) {
            this.coeff = OptionalOperations.dynamicInstantiate(rtnClass, coeffMatcher.group(1));
            startOfVars = coeffMatcher.end();
        }
        // this probably isn't strictly necessary, but helps the regex matcher avoid
        // non-matching stuff at the beginning of the input string
        String remainder = init.substring(startOfVars);
        Matcher varMatcher = varPattern.matcher(remainder);
        List<String> parsedVarNames = new ArrayList<>();
        while (varMatcher.find()) {
            parsedVarNames.add(varMatcher.group(1));
            powers.put(varMatcher.group(1), new RationalImpl(varMatcher.group(2)));
        }
        varNames.addAll(parsedVarNames);
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        if (checkArguments(arguments)) {
            Numeric accum = coefficient();
            try {
                for (long index = 0; index < arguments.length(); index++) {
                    final String argName = arguments.labelForIndex(index);
                    RationalType exponent = exponentFor(argName);

                    if (Zero.isZero(exponent)) continue;
                    if (arguments.elementAt(index) instanceof ComplexType) {
                        ComplexType element = (ComplexType) arguments.elementAt(index);
                        ComplexType intermediate = MathUtils.generalizedExponent(element, exponent, arguments.getMathContext());
                        accum = accum.multiply(intermediate);
                    } else {
                        if (!arguments.elementAt(index).isCoercibleTo(RealType.class)) {
                            throw new IllegalArgumentException("Element " + index + " of input vector cannot be coerced to real");
                        }
                        RealType realArg = (RealType) arguments.elementAt(index).coerceTo(RealType.class);
                        RealType intermediate = MathUtils.generalizedExponent(realArg,
                                exponent, arguments.getMathContext());
                        accum = accum.multiply(intermediate);
                    }
                }
                return (R) accum.coerceTo(getReturnType());
            } catch (CoercionException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("Input vector " + arguments + " is bad");
    }

    @Override
    protected boolean checkArguments(ArgVector<T> arguments) {
        if (argTypeCache == null) {
            argTypeCache = arguments.getElementType();
            Logger.getLogger(RationalExponentPolyTerm.class.getName()).log(Level.INFO,
                    "Determined arg type of RationalExponentPolyTerm is {0}.", argTypeCache.getSimpleName());
        } else if (!argTypeCache.isAssignableFrom(arguments.getElementType())) {
            Logger.getLogger(RationalExponentPolyTerm.class.getName()).log(Level.WARNING,
                    "RationalExponentPolyTerm expected arguments of type {0} but received {1} instead.",
                    new Object[] { argTypeCache, arguments.getElementType() });
            return false;
        }
        return super.checkArguments(arguments);
    }

    @Override
    public Class<T> getArgumentType() {
        return argTypeCache;
    }

    @Override
    public Term<T, R> multiply(Term<T, R> multiplier) {
        List<RationalType> ourExponents = varNames.stream().map(this::exponentFor).collect(Collectors.toList());
        R mergedCoeff = (R) coefficient().multiply(multiplier.coefficient());
        if (multiplier.isConstant()) {
            return new RationalExponentPolyTerm<>(mergedCoeff, varNames, ourExponents);
        }
        Set<String> mergedVars = new LinkedHashSet<>(varNames);
        Arrays.stream(multiplier.expectedArguments()).forEachOrdered(mergedVars::add);
        if (multiplier instanceof RationalExponentPolyTerm) {
            RationalExponentPolyTerm<T, R> other = (RationalExponentPolyTerm<T, R>) multiplier;
            List<RationalType> mergedExponents = mergedVars.stream()
                    .map(varName -> exponentFor(varName).add(other.exponentFor(varName)))
                    .map(RationalType.class::cast).collect(Collectors.toList());
            return new RationalExponentPolyTerm<>(mergedCoeff, new ArrayList<>(mergedVars), mergedExponents);
        } else {
            List<RationalType> mergedExponents = mergedVars.stream()
                    .map(varName -> exponentFor(varName).add(new IntegerImpl(BigInteger.valueOf(multiplier.order(varName)))))
                    .map(RationalType.class::cast).collect(Collectors.toList());
            return new RationalExponentPolyTerm<>(mergedCoeff, new ArrayList<>(mergedVars), mergedExponents);
        }
    }

    @Override
    public Term<T, R> multiply(Pow<T, R> func) {
        if (func.getComposedFunction().isPresent()) throw new IllegalArgumentException("Cannot multiply Term by composed function");
        LinkedHashSet<String> combinedArgs = new LinkedHashSet<>(varNames);
        final String funArg = func.expectedArguments()[0];
        final Numeric funExponent = func.getExponent();
        boolean hasNewArgs = combinedArgs.add(funArg);
        if (hasNewArgs) {
            Logger.getLogger(RationalExponentPolyTerm.class.getName()).log(Level.INFO,
                    "Combined polynomial term has {0} arguments.", combinedArgs.size());
        }
        HashMap<String, RationalType> combinedExponents = new HashMap<>(powers);
        try {
            combinedExponents.put(funArg, (RationalType) this.exponentFor(funArg).add(funExponent).coerceTo(RationalType.class));
        } catch (CoercionException e) {
            throw new IllegalStateException("Unexpected result adding exponent from supplied function: " + funExponent, e);
        }
        List<RationalType> listOfExponents = combinedArgs.stream().map(combinedExponents::get)
                .collect(Collectors.toList());
        assert listOfExponents.size() == combinedArgs.size();
        return new RationalExponentPolyTerm<>(this.coefficient(),
                new LinkedList<>(combinedArgs), listOfExponents);
    }

    @Override
    public Term<T, R> scale(R multiplier) {
        if (One.isUnity(multiplier)) return this;
        if (Zero.isZero(multiplier)) return new ConstantTerm<>(multiplier);
        List<RationalType> listOfExponents = varNames.stream().map(powers::get)
                .collect(Collectors.toList());
        return new RationalExponentPolyTerm<>((R) coefficient().multiply(multiplier), varNames, listOfExponents);
    }

    /**
     * Checks if the supplied polynomial term has the same general signature as this one.
     * This means that the two terms have the same arity, the same named variables,
     * and for each variable, the same exponent.
     * @param other the other polynomial term for comparison
     * @return true if the two terms have the same signature
     */
    public boolean hasMatchingSignature(RationalExponentPolyTerm<T, R> other) {
        // ensure the variables match up
        if (Arrays.mismatch(this.expectedArguments(), other.expectedArguments()) >= 0) return false;
        // if the arg name arrays match, let's check the exponents
        return varNames.stream().allMatch(varName -> Objects.equals(this.exponentFor(varName), other.exponentFor(varName)));
    }

    @Override
    public boolean isConstant() {
        // if all the exponents are zero, we are effectively a constant
        if (powers.values().stream().allMatch(Zero::isZero)) return true;
        return super.isConstant();
    }

    @Differentiable
    public Term<T, R> differentiate(String argName) {
        RationalType exponent = exponentFor(argName);
        try {
            R dcoeff = (R) coefficient().multiply(exponent).coerceTo(getReturnType());
            List<String> dargs = new ArrayList<>(varNames);
            TreeMap<String, RationalType> dpowers = new TreeMap<>(powers);
            exponent = (RationalType) exponent.subtract(One.getInstance(dcoeff.getMathContext())).coerceTo(RationalType.class);
            if (Zero.isZero(exponent)) {
                dargs.remove(argName);
                dpowers.remove(argName);
            } else {
                dpowers.replace(argName, exponent);
            }
            List<RationalType> temp = dargs.stream().map(dpowers::get).collect(Collectors.toList());
            return new RationalExponentPolyTerm<>(dcoeff, dargs, temp);
        } catch (CoercionException e) {
            throw new IllegalStateException("While differentiating a RationalExponentialPolyTerm", e);
        }
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
        return exponentFor(varName).floor().asBigInteger().longValueExact();
    }

    /**
     * Obtain the exponent for a given variable in this term.
     * @param varName the variable name
     * @return the rational exponent corresponding to the given {@code varName}
     */
    public RationalType exponentFor(String varName) {
        try {
            return powers.getOrDefault(varName,
                    (RationalType) ExactZero.getInstance(MathContext.UNLIMITED).coerceTo(RationalType.class));
        } catch (CoercionException e) {
            throw new IllegalStateException("Error while trying to coerce zero", e);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        boolean first = false;
        for (String argName : expectedArguments()) {
            if (!first) first = true;
            else buf.append(DOT_OPERATOR);
            buf.append(argName).append("^{").append(exponentFor(argName)).append('}');
        }
        buf.append('\u2009'); // thin space to set off the last closed curly brace
        return buf.toString();
    }
}

package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Term;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RationalExponentPolyTerm<T extends Numeric, R extends Numeric> extends Term<T, R> {
    private static final char DOT_OPERATOR = '\u22C5';
    private final Map<String, RationalType> powers = new TreeMap<>();
    private R coeff;

    public RationalExponentPolyTerm(List<String> variableNames, List<RationalType> exponents, Class<R> rtnClass) {
        super(variableNames, rtnClass);
        if (variableNames.size() != exponents.size()) {
            throw new IllegalArgumentException("var and exponent lists must match");
        }
        for (int idx = 0; idx < exponents.size(); idx++) {
            powers.put(variableNames.get(idx), exponents.get(idx));
        }
    }

    public RationalExponentPolyTerm(R coefficient, List<String> variableNames, List<RationalType> exponents) {
        this(variableNames, exponents, (Class<R>) coefficient.getClass());
        this.coeff = coefficient;
    }

    public RationalExponentPolyTerm(PolyTerm<T, R> toCopy) {
        super(toCopy.getReturnClass(), toCopy.expectedArguments());
        this.coeff = toCopy.coefficient();
        for (int idx = 0; idx < varNames.size(); idx++) {
            String varName = varNames.get(idx);
            powers.put(varName, new RationalImpl(BigInteger.valueOf(toCopy.order(varName)), BigInteger.ONE));
        }
    }

    private static final Pattern coeffPattern = Pattern.compile("^\\s*([+-]?\\d+[./]?\\d*)\\s?");
    private static final Pattern varPattern = Pattern.compile("(\\w+)\\^([+-]?\\d+/\\d+)\\s?");

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
                            throw new IllegalArgumentException("Element " + index + " of input vector cannot be coerced to real.");
                        }
                        RealType realArg = (RealType) arguments.elementAt(index).coerceTo(RealType.class);
                        RealType intermediate = MathUtils.generalizedExponent(realArg,
                                exponent, arguments.getMathContext());
                        accum = accum.multiply(intermediate);
                    }
                }
                return (R) accum.coerceTo(getReturnClass());
            } catch (CoercionException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("Input vector " + arguments + " is bad.");
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
            R dcoeff = (R) coefficient().multiply(exponent).coerceTo(getReturnClass());
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
            throw new IllegalStateException(e);
        }
    }


    @Override
    public R coefficient() {
        try {
            return coeff == null ? (R) One.getInstance(MathContext.UNLIMITED).coerceTo(getReturnClass()) : coeff;
        } catch (CoercionException e) {
            throw new IllegalStateException("Error while trying to coerce unity", e);
        }
    }

    @Override
    public long order(String varName) {
        if (!varNames.contains(varName)) return 0L;
        return exponentFor(varName).floor().asBigInteger().longValueExact();
    }

    public RationalType exponentFor(String varName) {
        try {
            return powers.getOrDefault(varName,
                    (RationalType) One.getInstance(MathContext.UNLIMITED).coerceTo(RationalType.class));
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

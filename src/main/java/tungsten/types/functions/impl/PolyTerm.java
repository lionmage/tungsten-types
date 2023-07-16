package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Term;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
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

public class PolyTerm<T extends Numeric, R extends Numeric> extends Term<T, R> {
    private static final char DOT_OPERATOR = '\u22C5';
    private final Map<String, Long> powers = new TreeMap<>();
    private R coeff;

    public PolyTerm(List<String> variableNames, List<Long> exponents, Class<R> rtnClass) {
        super(variableNames, rtnClass);
        if (variableNames.size() != exponents.size()) {
            throw new IllegalArgumentException("var and exponent lists must match");
        }
        for (int idx = 0; idx < exponents.size(); idx++) {
            powers.put(variableNames.get(idx), exponents.get(idx));
        }
    }

    public PolyTerm(R coefficient, List<String> variableNames, List<Long> exponents) {
        this(variableNames, exponents, (Class<R>) coefficient.getClass());
        this.coeff = coefficient;
    }

    private static final Pattern coeffPattern = Pattern.compile("^\\s*([+-]?\\d+[./]?\\d*)\\s?");
    private static final Pattern varPattern = Pattern.compile("(\\w+)\\^([+-]?\\d+)\\s?");

    public PolyTerm(String init, Class<R> rtnClass) {
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
            powers.put(varMatcher.group(1), Long.valueOf(varMatcher.group(2)));
        }
        varNames.addAll(parsedVarNames);
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        if (checkArguments(arguments)) {
            Numeric accum = coefficient();
            for (long index = 0; index < arguments.length(); index++) {
                final String argName = arguments.labelForIndex(index);
                Long exponent = powers.getOrDefault(argName, 1L);
                if (exponent == null) exponent = 1L;  // just in case powers stores null mappings

                if (exponent == 0L) continue;
                Numeric intermediate = MathUtils.computeIntegerExponent(arguments.elementAt(index),
                    new IntegerImpl(BigInteger.valueOf(exponent)));
                accum = accum.multiply(intermediate);
            }
            try {
                return (R) accum.coerceTo(getReturnClass());
            } catch (CoercionException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("Input vector " + arguments + " is bad.");
    }

    @Override
    public Term<T, R> multiply(Term<T, R> multiplier) {
        R coefficient = (R) this.coefficient().multiply(multiplier.coefficient());
        LinkedHashSet<String> combinedArgs = new LinkedHashSet<>(varNames);
        boolean hasNewArgs = combinedArgs.addAll(Arrays.asList(multiplier.expectedArguments()));
        if (hasNewArgs) {
            Logger.getLogger(PolyTerm.class.getName()).info("Combined polynomial term has " + combinedArgs.size() + " arguments.");
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
            Logger.getLogger(PolyTerm.class.getName()).info("Combined polynomial term has " + combinedArgs.size() + " arguments.");
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
            return coeff == null ? (R) One.getInstance(MathContext.UNLIMITED).coerceTo(getReturnClass()) : coeff;
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
        for (String argName : arguments.getElementLabels()) {
            Range<RealType> argRange = this.inputRange(argName);
            try {
                RealType toCheck = (RealType) arguments.forVariableName(argName).coerceTo(RealType.class);
                if (argRange != null && !argRange.contains(toCheck)) return false;
            } catch (CoercionException e) {
                Logger.getLogger(PolyTerm.class.getName()).log(Level.WARNING,
                        "Variable {0} has a real-valued range of {1}, but the value {2} cannot be coerced to RealTyoe.",
                        new Object[]{argName, argRange, arguments.forVariableName(argName)});
                return false;
            }
        }
        return super.checkArguments(arguments);
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
        R dcoeff = (R) coefficient().multiply(new IntegerImpl(BigInteger.valueOf(order)));
        List<String> dargs = new ArrayList<>(varNames);
        TreeMap<String, Long> dpowers = new TreeMap<>(powers);
        if (--order == 0) {
            dargs.remove(argName);
            dpowers.remove(argName);
        } else {
            dpowers.replace(argName, order);
        }
        List<Long> temp = dargs.stream().map(dpowers::get).collect(Collectors.toList());
        return new PolyTerm<>(dcoeff, dargs, temp);
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
        if (buf.length() == 0) return "\uD835\uDFCF";  // bold mathematical 1, surrogate pair for U+1D7CF

        return buf.toString();
    }
}

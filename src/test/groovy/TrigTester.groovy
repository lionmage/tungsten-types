import tungsten.types.numerics.RealType
import tungsten.types.numerics.impl.Pi
import tungsten.types.numerics.impl.RealImpl
import tungsten.types.util.MathUtils

import java.math.MathContext

final MathContext ctx = new MathContext(8);
final RealType TEN = new RealImpl(10.0, MathContext.UNLIMITED);
final RealType realTwo = new RealImpl(2.0, ctx);
final RealType maxError = (RealType) MathUtils.computeIntegerExponent(TEN, 1 - ctx.getPrecision(), ctx).divide(realTwo);
println("Maximum error = " + maxError);


final Pi pi = Pi.getInstance(ctx);
//println "pi/2 = " + pi/realTwo
RealType angle = pi.divide(realTwo) as RealType;
println("Sin of pi/2 is " + MathUtils.sin(angle));
println("Cos of pi/2 is " + MathUtils.cos(angle));
println("Sin of -pi/2 is " + MathUtils.sin(angle.negate()));
RealType quarterPi = angle.divide(realTwo) as RealType;
println("Sin of pi/4 is " + MathUtils.sin(quarterPi));
println("Cos of pi/4 is " + MathUtils.cos(quarterPi));
println("sin of pi is " + MathUtils.sin(pi));
println("cos of pi is " + MathUtils.cos(pi));

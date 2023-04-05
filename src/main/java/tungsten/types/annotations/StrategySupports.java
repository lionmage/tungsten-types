package tungsten.types.annotations;

import tungsten.types.functions.curvefit.CurveType;

import java.lang.annotation.*;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface StrategySupports {
    /**
     * This should be a short, human-readable name
     * sufficient to identify this strategy.
     * @return the name
     */
    String name();

    /**
     * This represents the type of data we should
     * be fitting to a curve
     * @return one of the enumeration values
     */
    CurveType type();
}

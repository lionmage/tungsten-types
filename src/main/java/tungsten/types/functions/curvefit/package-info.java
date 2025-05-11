/**
 * This package includes code for curve fitting (taking raw data and
 * fitting it to a curve), including strategies for fitting data to
 * a curve.<br>
 * All curve fitting strategies must implement the {@link tungsten.types.functions.curvefit.CurveFittingStrategy}
 * interface; implementations are found in {@link tungsten.types.functions.curvefit.strategies}.<br>
 * The {@link tungsten.types.functions.curvefit.CurveType} enumeration is used to identify the operational
 * domain of a {@code CurveFittingStrategy} as well as to select a desired type of curve to fit data to.<br>
 * Strategies must be annotated with {@link tungsten.types.annotations.StrategySupports} to be discoverable.<br>
 * {@link tungsten.types.functions.curvefit.CurveFitter} is the main entry point for most applications.
 * <ul>
 *     <li>{@code CurveFitter} can be instantiated with a set of {@link tungsten.types.functions.support.Coordinates}</li>
 *     <li>Data can be sorted by any ordinate, or by multiple ordinates (independent variables).</li>
 *     <li>A strategy can be selected purely based on {@code CurveType} or by name lookup.</li>
 *     <li>Bounds in any dimension can be extracted.</li>
 *     <li>There is a static method {@code CurveFitter.reduce()} which allows reducing a list of
 *       {@code Coordinates} of arbitrary dimension to a list of {@code Coordinates2D} with
 *       standard deviation values computed when there is a multiplicity of values for a given
 *       ordinate value.</li>
 *     <li>There are multiple strategies to fit a curve based upon linear regression (least squares),
 *       including weighted least squares strategies that take error into account.  There is also
 *       a strategy which uses cubic splines.</li>
 *     <li>A utility class, {@link tungsten.types.functions.curvefit.RegressionHelper}, is provided
 *       to ease the construction of curve fitting strategies.  It can, for example, build a
 *       design matrix for a polynomial regression of arbitrary order.</li>
 * </ul>
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
package tungsten.types.functions.curvefit;

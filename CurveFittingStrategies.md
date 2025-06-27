# Curve Fitting Strategies

There are several strategies that can be employed to fit data to a curve.
The method `CurveFitter.fitToData()` takes an optional `String` argument specifying the name
of a strategy for fitting data to a curve.  The method supports wildcards, so only the
initial unique characters of the name need be specified.  Weighted strategies require
the presence of 𝜎 (standard deviation) values for all data points.

## Table of strategies

| Name                   | Description                                                                                              |
|------------------------|----------------------------------------------------------------------------------------------------------|
| cubic splines          | Generates a piecewise function consisting of cubic spline segments. The curve passes through all points. |
| exponential fit        | Fits the data to an exponential.                                                                         |
| linear fit             | Performs a least-squares fit of a linear relationship to the data.                                       |
| multidimensional fit   | Generates a polynomial of N dimensions for N unknowns.                                                   |
| parabolic fit          | Generates a quadratic polynomial to fit a parabolic curve.                                               |
| simple 3D fit          | Fit data points in X, Y, Z format to a curve in terms of X, Y, and XY.                                   |
| weighted linear fit    | Perform a weighted least-squares fit to a line.                                                          |
| weighted parabolic fit | Perform a weighted least-squares fit to a quadratic polynomial.                                          |

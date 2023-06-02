package tungsten.types.util.rendering.matrix.cell;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
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
 */

import tungsten.types.Numeric;
import tungsten.types.annotations.RendererSupports;
import tungsten.types.exceptions.NumericRenderingException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.NumericHierarchy;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.util.MathUtils;
import tungsten.types.vector.RowVector;

import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A matrix cell renderer for {@link RealType real} values.
 * This renderer attempts to enforce the following rules:
 * <ul>
 *     <li>Decimal points are aligned within a column.</li>
 *     <li>For entries such as inverses of special constants, a solidus (&ldquo;/&rdquo;)
 *         is treated the same as a decimal point.</li>
 *     <li>For values whose {@code toString()} methods include an indicator of precision,
 *         this is stripped out.</li>
 *     <li>If no decimal point (or its equivalent) is present, assume that an &ldquo;invisible&rdquo;
 *         decimal point exists just after the final character (typically, but not always, a digit).</li>
 * </ul>
 * By default, this renderer will trim the number of digits after the decimal point; the default value
 * is 4, but this may be overridden via {@link #MAX_FRACTION_DIGITS_PROPERTY system property}.
 * By default, if extra digits are trimmed after the decimal point, an ellipsis (&hellip;) is appended.
 * This allows the user to visually distinguish between values that have been trimmed and values that
 * have not.
 * This behavior can be changed via another {@link #USE_ELLIPSES_PROPERTY system property}; setting
 * this property to {@code false} will force rounding all values to the given number of decimal places.
 */
@RendererSupports(name = "real-cell", type = NumericHierarchy.REAL)
public class RealCellRenderer implements CellRenderingStrategy {
    public static final String MAX_FRACTION_DIGITS_PROPERTY = "tungsten.types.util.rendering.matrix.cell.RealCellRenderer.maxFractionDigits";
    public static final String USE_ELLIPSES_PROPERTY = "tungsten.types.util.rendering.matrix.cell.RealCellRenderer.useEllipses";
    private static final String HORIZONTAL_ELLIPSIS = "\u2026";
    private int minimumCellWidth = 1; // 1 digit, no decimal point or fraction part
    private int maximumCellWidth = -1;  // unlimited
    private int maxFractionDigits = Integer.getInteger(MAX_FRACTION_DIGITS_PROPERTY, 4);

    @Override
    public void setMinCellWidth(int minWidth) {
        if (minWidth < 0) throw new IllegalArgumentException("Minimum cell width can never be < 0");
        if (maximumCellWidth != -1 && minWidth > maximumCellWidth) {
            throw new IllegalArgumentException("Minimum cell width cannot be > " + maximumCellWidth);
        }
        minimumCellWidth = minWidth;
    }

    @Override
    public void setMaxCellWidth(int maxWidth) {
        if (maxWidth == 0 || maxWidth < -1) throw new IllegalArgumentException("Invalid maximum cell with " + maxWidth);
        if (maxWidth != -1 && maxWidth < minimumCellWidth) {
            throw new IllegalArgumentException("Maximum cell width cannot be < " + minimumCellWidth);
        }
        maximumCellWidth = maxWidth;
    }

    /**
     * Sets the maximum number of digits allowed after the decimal point.
     * @param maxDigits the maximum number of digits allowed
     */
    public void setMaxFractionDigits(int maxDigits) {
        if (maximumCellWidth != -1 && maxDigits > maximumCellWidth - 2) {  // at minimum, allow for 1 whole digit and dec point
            throw new IllegalArgumentException("Requested number of fraction digits exceeds the maximum allowed cell size");
        }
        maxFractionDigits = maxDigits;
    }

    private boolean useEllipses() {
        String value = System.getProperty(USE_ELLIPSES_PROPERTY, "true");
        return Boolean.parseBoolean(value);
    }

    @Override
    public Class<? extends Numeric> getElementType() {
        return RealType.class;
    }

    private static final DecimalFormatSymbols dfSymbols = DecimalFormatSymbols.getInstance();
    private static final char DEC_POINT = dfSymbols.getDecimalSeparator();
    private static final Pattern precisionPat = Pattern.compile("\\s*\\[\\d+\\]\\s*");

    private int[] colWidth;
    private int[] decPos;

    @Override
    public void inspect(RowVector<? extends Numeric> row) {
        if (colWidth == null) {
            colWidth = new int[(int) row.length()];
            decPos   = new int[(int) row.length()];
            Arrays.fill(colWidth, minimumCellWidth);
            Arrays.fill(decPos, -1);
        }
        for (int col = 0; col < row.columns(); col++) {
            RealType value = (RealType) row.elementAt(col);
            String strRep = value.toString();
            Matcher m = precisionPat.matcher(strRep);
            if (m.find()) {
                if (m.end() == strRep.length()) strRep = strRep.substring(0, m.start());
                else strRep = strRep.substring(0, m.start()) + strRep.substring(m.end());
            }
            int decPoint = strRep.indexOf(DEC_POINT);
            if (decPoint == -1) {
                decPoint = strRep.indexOf('/');  // solidus
                if (decPoint < 0) decPoint = strRep.length();  // virtual decimal point
            }
            IntegerType wholePart = MathUtils.trunc(value);
            int wholeChars = (int) wholePart.numberOfDigits();
            if (value.sign() == Sign.NEGATIVE) wholeChars++;
            if (maximumCellWidth != -1 && wholeChars > maximumCellWidth) {
                throw new NumericRenderingException("Whole portion of " + strRep + " is too big to render", value);
            }
            int fracPartSize = strRep.length() - decPoint;
            if (fracPartSize > maxFractionDigits) {
                strRep = strRep.substring(0, decPoint + maxFractionDigits);
                if (useEllipses()) strRep += HORIZONTAL_ELLIPSIS;
            }
            if (maximumCellWidth != -1 && strRep.length() > maximumCellWidth) {
                throw  new NumericRenderingException(strRep + " is too long to render in a cell of width " + maximumCellWidth, value);
            }
            if (decPoint > decPos[col]) decPos[col] = decPoint;
            if (strRep.length() > colWidth[col]) colWidth[col] = strRep.length();
        }
    }

    @Override
    public String render(Numeric value, int column) {
        String strRep = value.toString();
        Matcher m = precisionPat.matcher(strRep);
        if (m.find()) {
            if (m.end() == strRep.length()) strRep = strRep.substring(0, m.start());
            else strRep = strRep.substring(0, m.start()) + strRep.substring(m.end());
        }
        int decPoint = strRep.indexOf(DEC_POINT);
        if (decPoint == -1) {
            decPoint = strRep.indexOf('/');  // solidus
            if (decPoint < 0) decPoint = strRep.length();  // virtual decimal point
        }
        int fracPartSize = strRep.length() - decPoint;  // do this here so we can modify decPoint below
        StringBuilder buf = new StringBuilder().append(strRep);
        while (decPoint++ < decPos[column]) {
            buf.insert(0, ' ');
        }
        if (fracPartSize > maxFractionDigits) {
            buf.setLength(decPoint + maxFractionDigits);
            if (useEllipses()) buf.append(HORIZONTAL_ELLIPSIS);
        }
        while (buf.length() < colWidth[column]) {
            buf.append(' ');
        }
        return buf.toString();
    }
}

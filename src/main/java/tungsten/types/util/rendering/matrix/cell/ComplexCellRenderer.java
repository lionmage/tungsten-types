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
import tungsten.types.annotations.Constant;
import tungsten.types.annotations.RendererSupports;
import tungsten.types.exceptions.NumericRenderingException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.NumericHierarchy;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.ImaginaryUnit;
import tungsten.types.util.UnicodeTextEffects;
import tungsten.types.vector.RowVector;

import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * A matrix cell renderer for {@link ComplexType complex} values.  This renderer
 * attempts to align the real and imaginary components in a column to the + or
 * &minus; sign separating those components.  Currently, no special handling
 * is offered for polar complex values.
 */
@RendererSupports(name = "complex-cell", type = NumericHierarchy.COMPLEX)
public class ComplexCellRenderer implements CellRenderingStrategy {
    public static final String MAX_FRACTION_DIGITS_PROPERTY = "tungsten.types.util.rendering.matrix.cell.ComplexCellRenderer.maxFractionDigits";
    public static final String USE_ELLIPSES_PROPERTY = "tungsten.types.util.rendering.matrix.cell.ComplexCellRenderer.useEllipses";
    private static final String HORIZONTAL_ELLIPSIS = "\u2026";
    private int minimumCellWidth = 1; // 1 digit, no decimal point or fraction part
    private int maximumCellWidth = -1;  // unlimited
    private int maxFractionDigits = Integer.getInteger(MAX_FRACTION_DIGITS_PROPERTY, 3);

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
        return ComplexType.class;
    }

    private static final DecimalFormatSymbols dfSymbols = DecimalFormatSymbols.getInstance();
    private static final char DEC_POINT = dfSymbols.getDecimalSeparator();
    private static final Pattern precisionPat = Pattern.compile("\\s*\\[\\d+\\]\\s*");
    private static final String IMAG_UNIT = ImaginaryUnit.class.getAnnotation(Constant.class).representation();

    private int[] colWidth;
    private int[] separatorPos;

    @Override
    public void inspect(RowVector<? extends Numeric> row) {
        if (colWidth == null) {
            colWidth = new int[(int) row.columns()];
            separatorPos = new int[(int) row.columns()];
            Arrays.fill(colWidth, minimumCellWidth);
            Arrays.fill(separatorPos, 1);
        }

        for (int col = 0; col < row.columns(); col++) {
            // ignore non-complex elements for now
            if (!(row.elementAt(col) instanceof ComplexType)) continue;

            ComplexType value = (ComplexType) row.elementAt(col);
            int width = 0;
            // real portion
            RealType real = value.real();
            if (real.sign() == Sign.NEGATIVE) width++;  // this is a total kludge
            if (real.getClass().isAnnotationPresent(Constant.class)) {
                StringBuilder buf = new StringBuilder();
                precisionPat.matcher(real.toString()).appendReplacement(buf, "");
                width += UnicodeTextEffects.computeCharacterWidth(buf);
            } else {
                width += UnicodeTextEffects.computeCharacterWidth(trimDecimalPlaces(real.toString()));
            }
            // spaces, + or - separator
            width += 1;
            if (width > separatorPos[col]) separatorPos[col] = width;
            width += 2;
            // imaginary part
            RealType imag = value.imaginary();
            if (imag.sign() == Sign.NEGATIVE) {
                imag = imag.negate();
            }
            if (imag.getClass().isAnnotationPresent(Constant.class)) {
                StringBuilder buf = new StringBuilder();
                precisionPat.matcher(imag.toString()).appendReplacement(buf, "");
                width += UnicodeTextEffects.computeCharacterWidth(buf);
            } else {
                width += UnicodeTextEffects.computeCharacterWidth(trimDecimalPlaces(imag.toString()));
            }
            width++;  // imaginary unit symbol
            if (width > colWidth[col]) colWidth[col] = width;
        }
    }

    private String trimDecimalPlaces(String original) {
        int decPos = original.indexOf(DEC_POINT);
        if (decPos == -1) return original;

        if (original.length() - decPos > maxFractionDigits) {
            StringBuilder buf = new StringBuilder().append(original);
            UnicodeTextEffects.trimToNFractionDigits(buf, decPos, maxFractionDigits);
            if (useEllipses()) buf.append(HORIZONTAL_ELLIPSIS);
            return buf.toString();
        }
        return original;
    }

    @Override
    public String render(Numeric value, int column) {
        StringBuilder buf = new StringBuilder();

        if (!(value instanceof ComplexType)) {
            if (value instanceof RealType) {
                buf.append(trimDecimalPlaces(value.toString()));
            } else {
                buf.append(value);
            }
            if (UnicodeTextEffects.computeCharacterWidth(buf) <= separatorPos[column] - 2) {
                while (UnicodeTextEffects.computeCharacterWidth(buf) < separatorPos[column] - 2) {
                    buf.insert(0, ' ');
                }
            } else {
                while (UnicodeTextEffects.computeCharacterWidth(buf) < colWidth[column]) {
                    buf.insert(0, ' ');
                }
            }
            // and if value winds up being too wide for the column, throw an exception
            if (UnicodeTextEffects.computeCharacterWidth(buf) > colWidth[column]) {
                throw new NumericRenderingException("Value is too wide for column " + column, value);
            }
            return buf.toString();
        }

        ComplexType cpVal = (ComplexType) value;
        // real portion
        RealType reVal = cpVal.real();
        if (reVal.getClass().isAnnotationPresent(Constant.class)) {
            precisionPat.matcher(reVal.toString()).appendReplacement(buf, "").appendTail(buf);
        } else {
            buf.append(trimDecimalPlaces(reVal.toString()));
        }
        while (UnicodeTextEffects.computeCharacterWidth(buf) < separatorPos[column] - 2) {
            buf.insert(0, '\u2007'); // figure space
        }
        buf.append('\u205F');  // U+205F is a medium mathematical space

        // imaginary part
        RealType imVal = cpVal.imaginary();
        if (imVal.sign() == Sign.NEGATIVE) {
            buf.append("\u2212\u205F");
            imVal = imVal.negate();
        } else {
            buf.append("+\u205F");
        }
        if (imVal.getClass().isAnnotationPresent(Constant.class)) {
            precisionPat.matcher(imVal.toString()).appendReplacement(buf, "").appendTail(buf);
        } else {
            buf.append(trimDecimalPlaces(imVal.toString()));
        }
        buf.append(IMAG_UNIT);
        while (UnicodeTextEffects.computeCharacterWidth(buf) < colWidth[column]) {
            buf.append('\u2007');
        }

        return buf.toString();
    }

    @Override
    public String render(RowVector<? extends Numeric> row) {
        String basic = CellRenderingStrategy.super.render(row);
        return basic.replace(' ', '\u2002'); // replace space with en-space
    }
}

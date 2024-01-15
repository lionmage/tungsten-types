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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.exceptions.NumericRenderingException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.NumericHierarchy;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.impl.One;
import tungsten.types.util.UnicodeTextEffects;
import tungsten.types.vector.RowVector;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * A matrix cell renderer for {@link RationalType rational} values.
 * This renderer will attempt to align fractional values in a column
 * to the solidus (slash or &ldquo;/&rdquo;).
 * <strong>Note:</strong> This renderer recognizes both the plain forward-slash
 * character present on most keyboards as well as U+2044, which has special
 * meaning for the rendering of fractions.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
@RendererSupports(name = "rational-cell", type = NumericHierarchy.RATIONAL)
public class RationalCellRenderer implements CellRenderingStrategy {
    private int minimumCellWidth = 3; // 1 digit + 1 solidus + 1 digit minimum
    private int maximumCellWidth = -1;  // unlimited

    @Override
    public void setMinCellWidth(int minWidth) {
        if (minWidth < 0) throw new IllegalArgumentException("Minimum cell width can never be < 0");
        minimumCellWidth = minWidth;
    }

    @Override
    public void setMaxCellWidth(int maxWidth) {
        if (maxWidth == 0 || maxWidth < -1) throw new IllegalArgumentException("Invalid maximum cell with " + maxWidth);
        maximumCellWidth = maxWidth;
    }

    @Override
    public Class<? extends Numeric> getElementType() {
        return IntegerType.class;
    }

    int[] numLen, denomLen, slashPos;

    @Override
    public void inspect(RowVector<? extends Numeric> row) {
        final int columns = (int) row.columns();
        if (numLen == null) {
            numLen = new int[columns];
            denomLen = new int[columns];
            slashPos = new int[columns];
            Arrays.fill(slashPos, 1); // minimum possible value
            Arrays.fill(numLen, 1);
            Arrays.fill(denomLen, 1);
        }
        for (int col = 0; col < columns; col++) {
            Numeric cval = row.elementAt(col);
            String strRep = cval.toString();
            if (cval instanceof IntegerType) {
                // sometimes, integer values get mixed in, so let's cope with it
                if (strRep.length() > numLen[col]) {
                    numLen[col] = strRep.length();
                    if (strRep.length() > slashPos[col]) slashPos[col] = strRep.length();
                }
                continue;
            }
            RationalType ratVal = (RationalType) cval;
            int slashIdx = indexOfSolidus(strRep);
            if (ratVal.denominator().numberOfDigits() > denomLen[col]) {
                denomLen[col] = (int) ratVal.denominator().numberOfDigits();
                if (slashIdx > slashPos[col]) slashPos[col] = slashIdx;
            }
            String numRep = strRep.substring(0, slashIdx);
            if (numRep.length() > numLen[col]) {
                numLen[col] = numRep.length();
                if (slashIdx > slashPos[col]) slashPos[col] = slashIdx;
            }
        }
    }

    private static final String SOLIDUS_REGEX = "[/\u2044\u2215]";
    private static final Pattern solidusPattern = Pattern.compile(SOLIDUS_REGEX);
    private static final String ONE_DENOM = SOLIDUS_REGEX + "1$";
    private static final Pattern oneDenomPattern = Pattern.compile(ONE_DENOM);

    private int indexOfSolidus(String val) {
        Matcher m = solidusPattern.matcher(val);
        if (m.find()) {
            return m.start();
        }
        Logger.getLogger(RationalCellRenderer.class.getName()).log(Level.FINE,
                "{0} does not appear to be an actual rational value.", val);
        return -1;
    }

    @Override
    public String render(Numeric value, int column) {
        StringBuilder cell = new StringBuilder();
        String strVal = value.toString();
        int valPos   = indexOfSolidus(strVal);
        int alignTo  = slashPos[column];
        int cellWidth = numLen[column] + denomLen[column] + 1;
        if (maximumCellWidth != -1 && (cellWidth > maximumCellWidth || strVal.length() > maximumCellWidth)) {
            throw new IllegalStateException("Maximum cell width exceeded");
        }
        if (cellWidth < minimumCellWidth) {
            // easy fix that shouldn't harm anything
            cellWidth = minimumCellWidth;
        }
        if (valPos == -1) valPos = strVal.length();
        if (alignTo > valPos) {
            // leading space
            IntStream.range(0, alignTo - valPos).forEach(dummy -> cell.append(' '));
        } else if (alignTo < valPos) {
            int chWidth = UnicodeTextEffects.computeCharacterWidth(strVal);
            if (chWidth == cellWidth)  {
                return strVal;
            } else if (chWidth > cellWidth) {
                Logger.getLogger(RationalCellRenderer.class.getName()).log(Level.SEVERE,
                        "Cannot format {0} to fit within a cell {1} characters wide; " +
                                "solidus is misaligned (position {2} for {0}, {3} for the cell). " +
                                "Consider inspecting more matrix rows for a better result.",
                        new Object[] {value, cellWidth, valPos, alignTo});
                throw new IllegalStateException("Cannot format value to fit in cell");
            }
        }
        if (!(value instanceof RationalType)) {
            Logger logger = Logger.getLogger(RationalCellRenderer.class.getName());
            logger.log(Level.WARNING, "Expected a rational matrix element, but got a {0}: {1}",
                    new Object[] { value.getClass().getTypeName(), value });
            try {
                value  = value.coerceTo(RationalType.class);
                strVal = value.toString();
                valPos = indexOfSolidus(strVal);
            } catch (CoercionException fatal) {
                logger.log(Level.SEVERE, "Matrix element is inconvertible to a rational.", fatal);
                throw new NumericRenderingException("Unable to render as a rational", value);
            }
        }
        final RationalType ratValue = (RationalType) value;
        if (One.isUnity(ratValue.denominator())) {
            // remove the /1 from the end of the string, as it is superfluous
            Matcher m = oneDenomPattern.matcher(strVal);
            if (!m.find()) {
                throw new NumericRenderingException("Failed to detect unity denominator in " + strVal, ratValue);
            }
            m.appendReplacement(cell, "\u2008"); // no need to appendTail() here since we're cutting off the end anyway
        } else if (One.isUnity(ratValue)) {
            // if our denominator is not 1, but we are unity anyway, complain and clean up
            Logger.getLogger(RationalCellRenderer.class.getName()).log(Level.WARNING,
                    "Encountered rational value {0} with equal numerator and " +
                    "denominator. This should have been reduced to 1/1.", ratValue);
            // this is really just unity (1), so substitute a 1 here
            IntStream.range(0, valPos - 1).forEach(dummy -> cell.append('\u2007'));  // figure space
            cell.append("1\u2008");  // U+2008 punctuation space
        } else {
            cell.append(strVal);
        }
        // trailing space
        int remaining = cellWidth - UnicodeTextEffects.computeCharacterWidth(cell);
        IntStream.range(0, remaining).forEach(dummy -> cell.append('\u2007'));  // figure space

        return cell.toString();
    }
}

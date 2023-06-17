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
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.NumericHierarchy;
import tungsten.types.numerics.RationalType;
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
            RationalType ratVal = (RationalType) row.elementAt(col);
            String strRep = ratVal.toString();
            int slashIdx = indexOfSolidus(strRep);
            if (ratVal.denominator().numberOfDigits() > denomLen[col]) {
                denomLen[col] = (int) ratVal.denominator().numberOfDigits();
                slashPos[col] = slashIdx;
            }
            String numRep = strRep.substring(0, slashIdx);
            if (numRep.length() > numLen[col]) {
                numLen[col] = numRep.length();
                if (slashIdx > slashPos[col]) slashPos[col] = slashIdx;
            }
        }
    }

    private static final String SOLIDUS_REGEX = "[/\u2044]";
    private static final Pattern solidusPattern = Pattern.compile(SOLIDUS_REGEX);

    private int indexOfSolidus(String val) {
        Matcher m = solidusPattern.matcher(val);
        if (m.find()) {
            return m.start();
        }
        Logger.getLogger(RationalCellRenderer.class.getName()).log(Level.INFO,
                val + " does not appear to be an actual rational value.");
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
        if (alignTo > valPos) {
            IntStream.range(0, alignTo - valPos).forEach(dummy -> cell.append(' '));
        } else if (alignTo < valPos) {
            if (strVal.length() == cellWidth)  {
                return strVal;
            } else if (strVal.length() > cellWidth) {
                Logger.getLogger(RationalCellRenderer.class.getName()).log(Level.SEVERE,
                        "Cannot format {0} to fit within a cell {1} characters wide; " +
                                "solidus is misaligned (position {2} for {0}, {3} for the cell). " +
                                "Consider inspecting more matrix rows for a better result.",
                        new Object[] {value, cellWidth, valPos, alignTo});
                throw new IllegalStateException("Cannot format value to fit in cell");
            }
        }
        cell.append(strVal);
        IntStream.range(0, cellWidth - cell.length()).forEach(dummy -> cell.append(' '));

        return cell.toString();
    }
}

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
import tungsten.types.vector.RowVector;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * A renderer for matrix elements of type {@link IntegerType}.
 * This renderer right-justifies integer values, so that their
 * least-significant digits line up.
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
@RendererSupports(name = "integer-cell", type = NumericHierarchy.INTEGER)
public class IntegerCellRenderer implements CellRenderingStrategy {
    private int minimumCellWidth = 0;
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

    private int[] cellWidths;

    @Override
    public void inspect(RowVector<? extends Numeric> row) {
        if (cellWidths == null) {
            cellWidths = new int[(int) row.columns()];
            Arrays.fill(cellWidths, minimumCellWidth);
        }
        for (int col = 0; col < row.columns(); col++) {
            IntegerType cellVal = (IntegerType) row.elementAt(col);
            if (cellWidths[col] < cellVal.numberOfDigits()) {
                cellWidths[col] = maximumCellWidth == -1 ? cellVal.toString().length() :
                        Math.min(cellVal.toString().length(), maximumCellWidth);
            }
            if (maximumCellWidth != -1 && cellVal.numberOfDigits() > maximumCellWidth) {
                Logger.getLogger(IntegerCellRenderer.class.getName()).log(Level.WARNING,
                        "Column {0} value {1} exceeds maximum column width of {2}.",
                        new Object[] {col, cellVal, maximumCellWidth});
            }
        }
    }

    @Override
    public String render(Numeric value, int column) {
        int cellWidth = cellWidths[column];
        StringBuilder buf = new StringBuilder();
        String valStr = value.toString();
        IntStream.range(0, cellWidth - valStr.length()).forEach(dummy -> buf.append(' '));
        buf.append(valStr);
        return buf.toString();
    }

    @Override
    public String render(RowVector<? extends Numeric> row) {
        StringBuilder buf = new StringBuilder();
        for (int col = 0; col < row.columns(); col++) {
            buf.append(render(row.elementAt(col), col));
            if (col != row.columns() - 1) buf.append(' ');
        }
        return buf.toString();
    }
}

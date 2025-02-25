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
import tungsten.types.vector.RowVector;

/**
 * Service interface for strategies that render the cells
 * of matrices.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public interface CellRenderingStrategy {
    /**
     * Set the minimum character width of any cell,
     * assuming a fixed-width font.  (In practice,
     * most fonts render digits in a fixed width.)
     * @param minWidth the minimum acceptable character width of this cell
     */
    void setMinCellWidth(int minWidth);

    /**
     * Set the maximum character width of any cell,
     * assuming a fixed-width font.
     * @param maxWidth the maximum acceptable character width, or -1 for unlimited
     */
    void setMaxCellWidth(int maxWidth);

    /**
     * Returns the type of the matrix cell values
     * that this rendering strategy is supposed to support.
     * @return a subtype of {@code Numeric}
     */
    Class<? extends Numeric> getElementType();

    /**
     * Inspect a row of a matrix and use its
     * characteristics to build up a model for
     * how the matrix should be rendered.
     * Multiple calls to this method (given unique
     * rows for each invocation) will improve
     * rendering results, though for very large
     * matrices, it may be impractical to scan all
     * rows.
     * @param row a {@link RowVector} containing the row
     *            of elements to be inspected
     */
    void inspect(RowVector<? extends Numeric> row);

    /**
     * Render a cell value for a given column.
     * @param value  the value to be rendered
     * @param column the 0-based index of the column
     * @return an appropriate rendition of {@code value}
     */
    String render(Numeric value, int column);

    /**
     * Render an entire row of a {@code Matrix}.
     * @param row the {@code Matrix} row to be rendered
     * @return an appropriate rendition of the entire row
     */
    default String render(RowVector<? extends Numeric> row) {
        StringBuilder buf = new StringBuilder();
        for (int col = 0; col < row.columns(); col++) {
            buf.append(render(row.elementAt(col), col));
            if (col != row.columns() - 1) buf.append(' ');
        }
        return buf.toString();
    }
}

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
    String render(Numeric value, int column);
    String render(RowVector<? extends Numeric> row);
}

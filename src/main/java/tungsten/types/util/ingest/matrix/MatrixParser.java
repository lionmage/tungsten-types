package tungsten.types.util.ingest.matrix;
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

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.matrix.impl.BasicMatrix;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.impl.ComplexPolarImpl;
import tungsten.types.numerics.impl.ComplexRectImpl;
import tungsten.types.util.OptionalOperations;
import tungsten.types.vector.impl.ArrayRowVector;

import java.io.*;
import java.lang.reflect.Array;
import java.math.MathContext;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A reusable parser that can be used to generate {@link Matrix} instances.
 * Each instance will only generate matrices of a specific {@link Numeric}
 * subtype, and each element of that matrix will have the same {@link MathContext}.
 * (The exception to the last rule is that, with the current implementation,
 * integer matrices will have no set {@link MathContext}.)<br>
 * To keep things simple, the parser expects one of the following formats:
 * <ul>
 *     <li>For parsing complex values, the values in a row are delimited by a
 *         pipe character (&ldquo;|&rdquo;) optionally surrounded by whitespace.</li>
 *     <li>For parsing all other numeric values, the values in a row are
 *         simply delimited with whitespace.</li>
 *     <li>Each row is terminated by a newline, as recognized by {@link BufferedReader#readLine()}.</li>
 *     <li>A blank line (empty or containing nothing but whitespace) terminates input.</li>
 *     <li>If parsing a file rather than a stream, a warning is emitted if the file name
 *         does not end in &ldquo;.matrix&rdquo; &mdash; note that file name extensions
 *         are not currently enforced, but may be in the future.</li>
 * </ul>
 *
 * @param <T> the {@link Numeric} subtype for the matrices generated by this parser
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Gmail</a>
 */
public class MatrixParser<T extends Numeric> {
    private final Class<T> matrixType;
    private final MathContext mctx;

    public MatrixParser(MathContext mctx, Class<T> type) {
        this.mctx = mctx;
        this.matrixType = type;
    }

    public Matrix<T> read(InputStream input) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            if (ComplexType.class.isAssignableFrom(matrixType)) {
                return cplxRead(reader);
            }
            return innerRead(reader);
        } catch (IOException e) {
            throw new RuntimeException("While reading matrix from InputStream", e);
        }
    }

    public Matrix<T> read(File file) {
        if (!file.isFile()) {
            throw new IllegalArgumentException("File argument must be an actual file");
        }
        if (!file.getName().endsWith(".matrix")) {
            Logger.getLogger(MatrixParser.class.getName()).log(Level.WARNING,
                    "Unknown file extension for {0}.", file.getName());
        }
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            if (ComplexType.class.isAssignableFrom(matrixType)) {
                return cplxRead(reader);
            }
            return innerRead(reader);
        } catch (SecurityException se) {
            Logger.getLogger(MatrixParser.class.getName()).log(Level.SEVERE,
                    "Unable to access {0} in file system.", file.getName());
            throw new IllegalStateException(se);
        } catch (IOException ioe) {
            throw new IllegalArgumentException("Error while opening " + file.getName(), ioe);
        }
    }

    private Matrix<T> innerRead(BufferedReader reader) {
        final String delimiter = "\\s+";
        BasicMatrix<T> result = new BasicMatrix<>();
        reader.lines().takeWhile(line -> !line.isBlank())
                .map(line -> line.split(delimiter))
                .map(this::convert)
                .map(ArrayRowVector::new)
                .forEachOrdered(result::append);
        return result;
    }

    private T[] convert(String[] tokens) {
        final boolean notAnInteger = !IntegerType.class.isAssignableFrom(matrixType);
        T[] converted = (T[]) Array.newInstance(matrixType, tokens.length);

        for (int i = 0; i < tokens.length; i++) {
            converted[i] = OptionalOperations.dynamicInstantiate(matrixType, tokens[i]);
            if (notAnInteger) OptionalOperations.setMathContext(converted[i], mctx);
        }

        return converted;
    }

    private Matrix<T> cplxRead(BufferedReader reader) {
        final String delimiter = "\\s*\\|\\s*"; // pipe delimited with optional whitespace
        BasicMatrix<ComplexType> result = new BasicMatrix<>();
        reader.lines().takeWhile(line -> !line.isBlank())
                .map(line -> line.split(delimiter))
                .map(this::convertCplx)
                .map(ArrayRowVector::new)
                .forEachOrdered(result::append);
        return (Matrix<T>) result;  // we know T is ComplexType, or we wouldn't be here
    }

    private ComplexType[] convertCplx(String[] tokens) {
        ComplexType[] converted = new ComplexType[tokens.length];

        for (int i = 0; i < tokens.length; i++) {
            try {
                converted[i] = new ComplexRectImpl(tokens[i]);
            } catch (Exception ex) {
                // check if it's formatted as a polar value
                converted[i] = new ComplexPolarImpl(tokens[i]);
            }
            OptionalOperations.setMathContext(converted[i], mctx);
        }

        return converted;
    }

    /**
     * Obtain a {@link Matrix} instance from some textual resource
     * located at the given URL.  If the resource is on the local
     * filesystem, the {@link File}-based code path will be used.
     * Otherwise, an {@link InputStream} will be obtained for the
     * resource.
     * @param resource any valid URL that points to a properly
     *                 formatted text document that encodes a
     *                 valid matrix
     * @return a matrix instance representing the resource
     */
    public Matrix<T> read(URL resource) {
        try {
            if (resource.getProtocol().startsWith("file")) {
                return read(new File(resource.toURI()));
            } else {
                return read(resource.openStream());
            }
        } catch (URISyntaxException syntaxException) {
            throw new IllegalArgumentException("Invalid URL", syntaxException);
        } catch (IOException ioe) {
            throw new IllegalStateException("Could not open " + resource + " for reading", ioe);
        }
    }
}

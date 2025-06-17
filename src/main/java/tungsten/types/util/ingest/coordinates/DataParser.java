/*
 * The MIT License
 *
 * Copyright © 2025 Robert Poole <Tarquin.AZ@gmail.com>.
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
 *
 */

package tungsten.types.util.ingest.coordinates;

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.functions.curvefit.CurveType;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.functions.support.Coordinates2D;
import tungsten.types.functions.support.Coordinates3D;
import tungsten.types.matrix.impl.BasicMatrix;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.ingest.matrix.MatrixParser;
import tungsten.types.vector.impl.ArrayRowVector;

import java.io.*;
import java.lang.reflect.Array;
import java.math.MathContext;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A reusable parser that can be used to generate {@code List<Coordinates>} instances.
 * To keep things simple, the parser expects one of the following formats:
 * <ul>
 *     <li>A row beginning with a pound sign (&ldquo;#&rdquo;) is treated as a comment.</li>
 *     <li>For parsing all values, the values in a row are delimited by a
 *         pipe character (&ldquo;|&rdquo;) optionally surrounded by whitespace.</li>
 *     <li>Each row is terminated by a newline, as recognized by {@link BufferedReader#readLine()}.</li>
 *     <li>A blank line (empty or containing nothing but whitespace) terminates input.</li>
 *     <li>If parsing a file rather than a stream, a warning is emitted if the file name
 *         does not end in &ldquo;.data&rdquo; &mdash; note that file name extensions
 *         are not currently enforced, but may be in the future.</li>
 * </ul>
 *
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Gmail</a>
 * @since 0.7
 */
public class DataParser {
    private final MathContext mctx;
    private final CurveType readDatumAs;

    public DataParser(MathContext ctx, CurveType readDatumAs) {
        this.mctx = ctx;
        this.readDatumAs = readDatumAs;
    }

    /**
     * Read a list of coordinates from an {@link InputStream} and
     * generate a representative {@code List<Coordinates>} object.
     * @param input the {@link InputStream} from which to read the values
     * @return a representation of the coordinates described by the input
     */
    public List<Coordinates> read(InputStream input) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return innerRead(reader);
        } catch (IOException e) {
            throw new IllegalStateException("While reading data from InputStream", e);
        }
    }

    /**
     * Read a {@code List<Coordinates>} whose values are stored in a textual file.
     * @param file the file system object containing the data
     * @return a list of {@link Coordinates} defined by the contents of {@code file}
     * @apiNote This method will log a warning if it is supplied a file with a name
     *   not ending in &ldquo;.data&rdquo; but will continue to parse the file.
     *   Future versions of this API may do more stringent file name checking.
     */
    public List<Coordinates> read(File file) {
        if (!file.isFile()) {
            throw new IllegalArgumentException("File argument must be an actual file");
        }
        if (!file.getName().endsWith(".data")) {
            Logger.getLogger(DataParser.class.getName()).log(Level.WARNING,
                    "Unknown file extension for {0}.", file.getName());
        }
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return innerRead(reader);
        } catch (SecurityException se) {
            Logger.getLogger(DataParser.class.getName()).log(Level.SEVERE,
                    "Unable to access {0} in file system.", file.getName());
            throw new IllegalStateException(se);
        } catch (IOException ioe) {
            throw new IllegalArgumentException("Error while opening " + file.getName(), ioe);
        }
    }

    /**
     * Obtain a {@code List<Coodrdinates>} from some textual resource
     * located at the given URL.  If the resource is on the local
     * filesystem, the {@link File}-based code path will be used.
     * Otherwise, an {@link InputStream} will be obtained for the
     * resource.
     * @param resource any valid URL that points to a properly
     *                 formatted text document that encodes a
     *                 valid data set
     * @return a list of coordinates representing the resource
     */
    public List<Coordinates> read(URL resource) {
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

    private List<Coordinates> innerRead(BufferedReader reader) {
        final String delimiter = "\\s*\\|\\s*";
        List<Coordinates> result = new ArrayList<>();
        reader.lines().takeWhile(line -> !line.isBlank())
                .map(this::stripBOM)
                .filter(line -> !line.startsWith("#")) // ignore comments
                .map(line -> line.split(delimiter))
                .map(this::convert)
                .map(this::fromArray)
                .forEachOrdered(result::add);
        return result;
    }

    private Coordinates fromArray(RealType[] values) {
        switch (readDatumAs) {
            case CURVE_2D:
                if (values.length < 2) throw new IllegalArgumentException("2D datum must have at least 2 values");
                if (values.length > 2) {
                    // values[2] is assumed to be the standard deviation
                    return new Coordinates2D(values[0], values[1], values[2]);
                }
                return new Coordinates2D(values[0], values[1]);
            case CURVE_3D:
                if (values.length < 3) throw new IllegalArgumentException("3D datum must have 3 values");
                return new Coordinates3D(values[0], values[1], values[2]);
            default:
                if (values.length < 4) {
                    Logger.getLogger(DataParser.class.getName()).log(Level.WARNING,
                            "Suspicious set of datum values: {0}", values);
                }
                return new Coordinates(Arrays.asList(values));
        }
    }

    private RealType[] convert(String[] tokens) {
        RealType[] converted = new RealType[tokens.length];
        final int lastToken = tokens.length - 1;

        tokens[lastToken] = tokens[lastToken].stripTrailing(); // in case of trailing whitespace
        for (int i = 0; i < tokens.length; i++) {
            converted[i] = new RealImpl(tokens[i], mctx);
        }

        return converted;
    }

    private String stripBOM(String original) {
        if (original.startsWith("\ufeff")) {
            original = original.substring(1); // throw away the initial BOM character
        }
        return original.stripLeading();  // remove initial whitespace, if any
    }
}

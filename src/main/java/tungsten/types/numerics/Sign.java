/* 
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.numerics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

/**
 *
 * @author tarquin
 */
public enum Sign {
    NEGATIVE("\u2212"), ZERO("0"), POSITIVE("+");
    
    private final String symbol;
    
    private Sign(String symbol) {
        this.symbol = symbol;
    }
    
    public static Sign fromValue(long value) {
        if (value == 0L) return ZERO;
        if (value < 0L) return NEGATIVE;
        return POSITIVE;
    }
    
    public static Sign fromValue(BigInteger value) {
        int signum = value.signum();
        if (signum == 0) return ZERO;
        if (signum < 0) return NEGATIVE;
        return POSITIVE;
    }
    
    public static Sign fromValue(BigDecimal value) {
        int signum = value.signum();
        if (signum == 0) return ZERO;
        if (signum < 0) return NEGATIVE;
        return POSITIVE;
    }
    
    public Sign negate() {
        switch (this) {
            case POSITIVE:
                return NEGATIVE;
            case NEGATIVE:
                return POSITIVE;
            case ZERO:
            default:
                return ZERO;
        }
    }
    
    public String getSymbol() { return symbol; }
    
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}

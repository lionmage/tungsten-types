package tungsten.types.numerics.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class RepeatingDecimalTest {
    private final String ONE_SIXTH_DEC = "0.16";
    private final long ONE_SIXTH_STARTPOS = 1L; // decimal position where repeat starts
    private final String NINE_ELEVENTHS_DEC = "0.81";
    private final long NINE_ELEVENTHS_STARTPOS = 0L;
    private final String SEVEN_TWELFTHS_DEC = "0.583";
    private final long SEVEN_TWELFTHS_STARTPOS = 2L;
    private RepeatingDecimal oneSixth;
    private RepeatingDecimal nineElevenths;
    private RepeatingDecimal sevenTwelfths;

    @BeforeEach
    void setUp() {
        oneSixth = new RepeatingDecimal(ONE_SIXTH_DEC, new IntegerImpl(BigInteger.valueOf(ONE_SIXTH_STARTPOS)));
        nineElevenths = new RepeatingDecimal(NINE_ELEVENTHS_DEC, new IntegerImpl(BigInteger.valueOf(NINE_ELEVENTHS_STARTPOS)));
        sevenTwelfths = new RepeatingDecimal(SEVEN_TWELFTHS_DEC, new IntegerImpl(BigInteger.valueOf(SEVEN_TWELFTHS_STARTPOS)));
    }

    @Test
    void cycleLength() {
        final IntegerType one = new IntegerImpl(BigInteger.ONE);
        assertTrue(oneSixth.cycleLength().isPresent());
        assertEquals(one, oneSixth.cycleLength().get());
    }

    @Test
    void cycleStart() {
        final IntegerType one = new IntegerImpl(BigInteger.ONE);
        assertTrue(oneSixth.cycleStart().isPresent());
        assertEquals(one, oneSixth.cycleStart().get());
    }

    @Test
    void testEquivalence() {
        final RationalType target = new RationalImpl(BigInteger.ONE, BigInteger.valueOf(6L));
        assertEquals(target, oneSixth);

        final RationalType target2 = new RationalImpl(BigInteger.valueOf(9L), BigInteger.valueOf(11L));
        assertEquals(target2, nineElevenths);

        final RationalType target3 = new RationalImpl(BigInteger.valueOf(7L), BigInteger.valueOf(12L));
        assertEquals(target3, sevenTwelfths);
    }

    @Test
    void testToString() {
        String representation = oneSixth.toString();
        representation = removeTrailingEllipsis(representation);
        assertEquals(ONE_SIXTH_DEC, UnicodeTextEffects.stripCombiningCharacters(representation));

        representation = sevenTwelfths.toString();
        representation = removeTrailingEllipsis(representation);
        assertEquals(SEVEN_TWELFTHS_DEC, UnicodeTextEffects.stripCombiningCharacters(representation));
    }

    private String removeTrailingEllipsis(String original) {
        if (!Character.isDigit(original.charAt(original.length() - 1))) {
            // remove any trailing ellipsis from the toString() result
            return original.substring(0, original.length() - 1);
        }
        return original;
    }
}
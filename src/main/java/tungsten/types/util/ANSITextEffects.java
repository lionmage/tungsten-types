/*
 * The MIT License
 *
 * Copyright © 2024 Robert Poole <Tarquin.AZ@gmail.com>.
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

package tungsten.types.util;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A collection of utility methods for rendering text in select styles
 * and colors.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 * @since 0.5
 */
public class ANSITextEffects {
    private static final char ESCAPE = '\u001b';
    public static final EnumSet<Effect> STYLES = EnumSet.of(Effect.BOLD, Effect.ITALIC, Effect.UNDERLINE);
    public static final EnumSet<Effect> COLORS = EnumSet.complementOf(STYLES);
    public static final EnumSet<Effect> BACKGROUND_COLORS =
            EnumSet.of(Effect.BG_CYAN, Effect.BG_YELLOW, Effect.BG_GREEN, Effect.DEFAULT_BACKGROUND);
    public static final EnumSet<Effect> RESET_COMMANDS = EnumSet.of(Effect.DEFAULT_COLOR,
            Effect.DEFAULT_BACKGROUND, Effect.RESET);

    static {
        COLORS.remove(Effect.RESET);
    }

    /**
     * An enumeration specifying various ANSI text effects,
     * e.g. bold or red.
     */
    public enum Effect {
        BOLD("1", "22"),
        ITALIC("3", "23"),
        UNDERLINE("4", "24"),
        BLACK("30"),
        RED("31"),
        GREEN("32"),
        YELLOW("33"),
        BLUE("34"),
        MAGENTA("35"),
        CYAN("36"),
        WHITE("37"),
        DEFAULT_COLOR("39"),  // resets text color
        DEFAULT_BACKGROUND("49"),  // resets text background
        BG_GREEN("42"),
        BG_YELLOW("43"),
        BG_CYAN("46"),
        RESET("0");  // resets ALL colors and text effects

        private final String escSet;
        private final String escReset;

        Effect(String setCode, String resetCode) {
            this.escSet = setCode;
            this.escReset = resetCode;
        }

        Effect(String code) {
            this.escSet = code;
            this.escReset = null;
        }
    }

    private ANSITextEffects() { }  // non-instantiable

    /**
     * Assemble an ANSI command sequence to apply one or more
     * effects to some subsequent text.
     * @param effects one or more effects to apply
     * @return the assembled command sequence
     */
    public static String assembleSetCommand(Effect... effects) {
        StringBuilder buf = new StringBuilder();
        buf.append(ESCAPE).append('[');
        if (Arrays.stream(effects).filter(STYLES::contains).count() > 1L) {
            throw new IllegalArgumentException("Multiple text styles specified");
        }
        Optional<Effect> textStyle = Arrays.stream(effects).filter(STYLES::contains).findFirst();
        List<Effect> color = Arrays.stream(effects).filter(COLORS::contains).collect(Collectors.toList());
        textStyle.ifPresentOrElse(style -> buf.append(style.escSet), () -> buf.append('0'));
        color.forEach(colorStyle -> buf.append(';').append(colorStyle.escSet));
        buf.append('m');  // end of command

        return buf.toString();
    }

    /**
     * Assemble an ANSI command sequence to reset one or more
     * effects applied to some previous text.
     * @param effect the style to reset, or a more generalized reset
     * @return the assembled reset command
     * @see #RESET_COMMANDS
     */
    public static String assembleResetCommand(Effect effect) {
        StringBuilder buf = new StringBuilder();
        buf.append(ESCAPE).append('[');
        if (STYLES.contains(effect)) {
            buf.append(effect.escReset);
        } else {
            if (!RESET_COMMANDS.contains(effect)) {
                throw new IllegalArgumentException("Not a valid reset token: " + effect);
            }
            buf.append(effect.escSet);
        }
        buf.append('m');  // end of command

        return buf.toString();
    }

    /**
     * Highlight a selection of a given {@code String} using a supplied {@code Effect}.
     * @param source the original {@code String} to be decorated
     * @param start  the start index of the portion of {@code source} to be highlighted
     * @param end    the end index of the section to be highlighted
     * @param effect the display effect to apply to the highlighted text
     * @return a decorated {@code String} with the chosen section highlighted
     * @throws IndexOutOfBoundsException if {@code start} or {@code end} fall outside the bounds of {@code source}
     */
    public static String highlightSection(String source, int start, int end, Effect effect) {
        if (source == null || source.isBlank()) return "";
        if (start < 0 || start >= source.length() || end < start || end > source.length()) {
            throw new IndexOutOfBoundsException("Start and end values must be consistent with source");
        }

        StringBuilder buf = new StringBuilder();
        if (start > 0) {
            buf.append(source, 0, start);
        }
        buf.append(assembleSetCommand(effect));
        buf.append(source, start, end);
        if (BACKGROUND_COLORS.contains(effect)) {
            buf.append(assembleResetCommand(Effect.DEFAULT_BACKGROUND));
        } else if (COLORS.contains(effect)) {
            buf.append(assembleResetCommand(Effect.DEFAULT_COLOR));
        } else {
            buf.append(assembleResetCommand(effect));
        }
        if (end < source.length()) {
            buf.append(source, end, source.length());
        }

        return buf.toString();
    }

    /**
     * Highlight a selection of a given {@code String} using the supplied {@code Effect}s.
     * This is similar to {@link #highlightSection(String, int, int, Effect)} but with
     * the ability to apply multiple effects (e.g., bold + blue text + yellow background)
     * at the expense of the varargs penalty.
     * @param source  the original {@code String} to be decorated
     * @param start   the start index of the portion of {@code source} to be highlighted
     * @param end     the end index of the section to be highlighted
     * @param effects the display effects to apply to the highlighted text
     * @return a decorated {@code String} with the chosen section highlighted
     * @throws IndexOutOfBoundsException if {@code start} or {@code end} fall outside the bounds of {@code source}
     */
    public static String highlightSelection(String source, int start, int end, Effect... effects) {
        if (source == null || source.isBlank()) return "";
        if (start < 0 || start >= source.length() || end < start || end > source.length()) {
            throw new IndexOutOfBoundsException("Start and end values must be consistent with source");
        }

        StringBuilder buf = new StringBuilder();
        if (start > 0) {
            buf.append(source, 0, start);
        }
        buf.append(assembleSetCommand(effects));
        buf.append(source, start, end);
        // use the catch-all reset rather than disabling effects one by one
        buf.append(assembleResetCommand(Effect.RESET));
        if (end < source.length()) {
            buf.append(source, end, source.length());
        }

        return buf.toString();
    }

    /**
     * Given a source {@code String}, apply the given highlight effects to any
     * substring that matches the supplied value.
     * @param source    the original, unadorned string
     * @param substring the literal substring to match for highlighting purposes
     * @param effects   the display effects to apply to any highlighted text
     * @return a decorated {@code String} with highlighting applied
     */
    public static String highlightSubstring(String source, String substring, Effect... effects) {
        if (source == null || source.isBlank()) return "";

        final String highlighted = assembleSetCommand(effects) + substring +
                assembleResetCommand(Effect.RESET);
        return source.replace(substring, highlighted);
    }

    /**
     * Helper method to find the first character difference between two
     * character sequences.
     * @param lhs the first character sequence to compare
     * @param rhs the second character sequence to compare
     * @return the position of the first difference between the two
     *   inputs, or -1 if no difference is found
     */
    public static int findFirstDifference(CharSequence lhs, CharSequence rhs) {
        int limit = Math.min(lhs.length(), rhs.length());
        for (int i = 0; i < limit; i++) {
            if (lhs.charAt(i) != rhs.charAt(i)) return i;
        }
        return lhs.length() != rhs.length() ? limit : -1;
    }
}

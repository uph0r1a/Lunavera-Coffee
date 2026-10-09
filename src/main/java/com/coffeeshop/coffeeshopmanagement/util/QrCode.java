package com.coffeeshop.coffeeshopmanagement.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Small, dependency-free QR Code (Model 2) encoder - the project's pom has no QR library, and a
 * receipt only needs "turn a short string into a square of dark/light modules".
 *
 * <p>Scope, deliberately narrow: the text is encoded as UTF-8 in <b>byte mode</b> at error
 * correction level <b>M</b> (about 15% of the code can be damaged and still scan), using the
 * smallest version (1-40) that fits, with the best of the 8 mask patterns chosen by the standard
 * penalty rules. Numeric/alphanumeric/kanji modes, mixed segments and ECI are not implemented -
 * they only make the symbol a bit smaller, never more scannable.
 *
 * <p>The class is UI-free: {@link #getModule} says whether a module is dark, and the caller
 * (HoaDonController) paints it. Structure follows the ISO/IEC 18004 algorithm (the same
 * construction used by well-known reference encoders): build the data bit stream, split it into
 * blocks, append Reed-Solomon error-correction codewords, interleave, then place the bits in the
 * zig-zag order around the fixed finder/timing/alignment patterns and apply the mask.
 */
public final class QrCode {

    /** Version 40 at level M holds 2331 bytes; receipts need a few dozen. */
    public static final int MAX_BYTES = 2331;

    private static final int MIN_VERSION = 1;
    private static final int MAX_VERSION = 40;

    /** Error-correction codewords per block, level M, indexed by version (index 0 unused). */
    private static final int[] ECC_CODEWORDS_PER_BLOCK_M = {
            -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28,
            28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28};
    /** Number of error-correction blocks, level M, indexed by version (index 0 unused). */
    private static final int[] NUM_ERROR_CORRECTION_BLOCKS_M = {
            -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28,
            29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49};
    /** Format-information bits for level M. */
    private static final int FORMAT_BITS_M = 0;

    private final int version;
    private final int size;
    private final int mask;
    private final boolean[][] modules;     // true = dark; [y][x]
    private final boolean[][] isFunction;  // true = part of a fixed pattern (never masked / not data)

    private QrCode(int version, byte[] dataCodewords, int forcedMask) {
        this.version = version;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];

        drawFunctionPatterns();
        byte[] allCodewords = addEccAndInterleave(dataCodewords);
        drawCodewords(allCodewords);

        int chosen = forcedMask;
        if (chosen < 0) {
            int bestPenalty = Integer.MAX_VALUE;
            for (int candidate = 0; candidate < 8; candidate++) {
                applyMask(candidate);
                drawFormatBits(candidate);
                int penalty = penaltyScore();
                if (penalty < bestPenalty) {
                    bestPenalty = penalty;
                    chosen = candidate;
                }
                applyMask(candidate); // XOR again = undo
            }
        }
        this.mask = chosen;
        applyMask(mask);
        drawFormatBits(mask);
    }

    // ------------------------------------------------------------------ public API

    /** Encodes {@code text} (UTF-8, level M, smallest fitting version, best mask). */
    public static QrCode encodeText(String text) {
        return encodeText(text, -1);
    }

    /** As {@link #encodeText(String)} but forcing a mask 0-7 (-1 = choose automatically); for tests. */
    static QrCode encodeText(String text, int forcedMask) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        if (forcedMask < -1 || forcedMask > 7) {
            throw new IllegalArgumentException("mask must be -1..7");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Text too long for a QR code: " + bytes.length + " bytes");
        }

        int version = MIN_VERSION;
        int dataCapacityBits;
        while (true) {
            dataCapacityBits = numDataCodewords(version) * 8;
            int countBits = version <= 9 ? 8 : 16;
            int used = 4 + countBits + bytes.length * 8;
            if (used <= dataCapacityBits) {
                break;
            }
            if (version >= MAX_VERSION) {
                throw new IllegalArgumentException("Text too long for a QR code");
            }
            version++;
        }

        BitBuffer bits = new BitBuffer();
        bits.append(0x4, 4);                                  // byte mode indicator
        bits.append(bytes.length, version <= 9 ? 8 : 16);     // character count
        for (byte b : bytes) {
            bits.append(b & 0xFF, 8);
        }
        bits.append(0, Math.min(4, dataCapacityBits - bits.length())); // terminator
        bits.append(0, (8 - bits.length() % 8) % 8);                   // pad to a byte boundary
        for (int pad = 0xEC; bits.length() < dataCapacityBits; pad ^= 0xEC ^ 0x11) {
            bits.append(pad, 8);
        }
        return new QrCode(version, bits.toBytes(), forcedMask);
    }

    public int getVersion() {
        return version;
    }

    public int getSize() {
        return size;
    }

    public int getMask() {
        return mask;
    }

    /** True if the module at column {@code x}, row {@code y} is dark; false outside the symbol. */
    public boolean getModule(int x, int y) {
        return 0 <= x && x < size && 0 <= y && y < size && modules[y][x];
    }

    // ------------------------------------------------------------------ function patterns

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) { // timing patterns
            setFunctionModule(6, i, i % 2 == 0);
            setFunctionModule(i, 6, i % 2 == 0);
        }
        drawFinderPattern(3, 3);
        drawFinderPattern(size - 4, 3);
        drawFinderPattern(3, size - 4);

        int[] alignPos = alignmentPatternPositions();
        int count = alignPos.length;
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < count; j++) {
                boolean overlapsFinder = (i == 0 && j == 0) || (i == 0 && j == count - 1) || (i == count - 1 && j == 0);
                if (!overlapsFinder) {
                    drawAlignmentPattern(alignPos[i], alignPos[j]);
                }
            }
        }
        drawFormatBits(0); // reserve the format area; rewritten with the real mask later
        drawVersion();
    }

    private void drawFormatBits(int msk) {
        int data = FORMAT_BITS_M << 3 | msk;
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = (data << 10 | rem) ^ 0x5412;

        for (int i = 0; i <= 5; i++) setFunctionModule(8, i, getBit(bits, i));
        setFunctionModule(8, 7, getBit(bits, 6));
        setFunctionModule(8, 8, getBit(bits, 7));
        setFunctionModule(7, 8, getBit(bits, 8));
        for (int i = 9; i < 15; i++) setFunctionModule(14 - i, 8, getBit(bits, i));

        for (int i = 0; i < 8; i++) setFunctionModule(size - 1 - i, 8, getBit(bits, i));
        for (int i = 8; i < 15; i++) setFunctionModule(8, size - 15 + i, getBit(bits, i));
        setFunctionModule(8, size - 8, true); // the always-dark module
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) {
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        }
        int bits = version << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean bit = getBit(bits, i);
            int a = size - 11 + i % 3;
            int b = i / 3;
            setFunctionModule(a, b, bit);
            setFunctionModule(b, a, bit);
        }
    }

    private void drawFinderPattern(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx;
                int y = cy + dy;
                if (0 <= x && x < size && 0 <= y && y < size) {
                    setFunctionModule(x, y, dist != 2 && dist != 4);
                }
            }
        }
    }

    private void drawAlignmentPattern(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunctionModule(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void setFunctionModule(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private int[] alignmentPatternPositions() {
        if (version == 1) {
            return new int[0];
        }
        int count = version / 7 + 2;
        int step = version == 32 ? 26 : (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2;
        int[] result = new int[count];
        result[0] = 6;
        for (int i = count - 1, pos = size - 7; i >= 1; i--, pos -= step) {
            result[i] = pos;
        }
        return result;
    }

    // ------------------------------------------------------------------ data + error correction

    private static int numRawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) {
                result -= 36;
            }
        }
        return result;
    }

    private static int numDataCodewords(int ver) {
        return numRawDataModules(ver) / 8
                - ECC_CODEWORDS_PER_BLOCK_M[ver] * NUM_ERROR_CORRECTION_BLOCKS_M[ver];
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int numBlocks = NUM_ERROR_CORRECTION_BLOCKS_M[version];
        int blockEccLen = ECC_CODEWORDS_PER_BLOCK_M[version];
        int rawCodewords = numRawDataModules(version) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;

        byte[][] blocks = new byte[numBlocks][];
        byte[] divisor = reedSolomonDivisor(blockEccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            byte[] dat = Arrays.copyOfRange(data, k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
            k += dat.length;
            byte[] block = Arrays.copyOf(dat, shortBlockLen + 1);
            byte[] ecc = reedSolomonRemainder(dat, divisor);
            System.arraycopy(ecc, 0, block, block.length - blockEccLen, ecc.length);
            blocks[i] = block;
        }

        byte[] result = new byte[rawCodewords];
        for (int i = 0, k = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < blocks.length; j++) {
                // Short blocks have one fewer data byte: skip their padding slot.
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[k++] = blocks[j][i];
                }
            }
        }
        return result;
    }

    private static byte[] reedSolomonDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = (byte) gfMultiply(result[j] & 0xFF, root);
                if (j + 1 < result.length) {
                    result[j] ^= result[j + 1];
                }
            }
            root = gfMultiply(root, 0x02);
        }
        return result;
    }

    private static byte[] reedSolomonRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) gfMultiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    /** Multiplication in GF(2^8) with the QR reducing polynomial x^8+x^4+x^3+x^2+1 (0x11D). */
    private static int gfMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private void drawCodewords(byte[] data) {
        int i = 0; // bit index into data
        for (int right = size - 1; right >= 1; right -= 2) { // pairs of columns, right to left
            if (right == 6) {
                right = 5; // skip the vertical timing column
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = getBit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ masking

    private void applyMask(int msk) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert = switch (msk) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                };
                modules[y][x] ^= invert & !isFunction[y][x];
            }
        }
    }

    /** The four standard penalty rules (long runs, 2x2 blocks, finder-like patterns, dark/light balance). */
    private int penaltyScore() {
        int result = 0;

        // Rule 1: runs of 5+ same-colour modules in a row/column.
        for (int a = 0; a < size; a++) {
            for (int dir = 0; dir < 2; dir++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean same = dir == 0 ? modules[a][b] == modules[a][b - 1] : modules[b][a] == modules[b - 1][a];
                    if (same) {
                        run++;
                        if (run == 5) result += 3;
                        else if (run > 5) result++;
                    } else {
                        run = 1;
                    }
                }
            }
        }

        // Rule 2: 2x2 blocks of one colour.
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    result += 3;
                }
            }
        }

        // Rule 3: finder-like 1:1:3:1:1 pattern with 4 light modules on either side.
        boolean[] pattern = {true, false, true, true, true, false, true};
        for (int a = 0; a < size; a++) {
            for (int b = 0; b + 7 <= size; b++) {
                for (int dir = 0; dir < 2; dir++) {
                    boolean match = true;
                    for (int k = 0; k < 7 && match; k++) {
                        boolean m = dir == 0 ? modules[a][b + k] : modules[b + k][a];
                        match = m == pattern[k];
                    }
                    if (!match) continue;
                    if (lightRun(a, b - 4, b, dir) || lightRun(a, b + 7, b + 11, dir)) {
                        result += 40;
                    }
                }
            }
        }

        // Rule 4: deviation of the dark share from 50%, in 5% steps.
        int dark = 0;
        for (boolean[] row : modules) {
            for (boolean m : row) {
                if (m) dark++;
            }
        }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        result += k * 10;
        return result;
    }

    /** True if modules [from, to) along the given line are all light (cells outside the symbol count as light). */
    private boolean lightRun(int line, int from, int to, int dir) {
        for (int p = from; p < to; p++) {
            if (p < 0 || p >= size) continue;
            boolean m = dir == 0 ? modules[line][p] : modules[p][line];
            if (m) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean getBit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private static final class BitBuffer {
        private final java.util.BitSet bits = new java.util.BitSet();
        private int length;

        void append(int value, int numBits) {
            for (int i = numBits - 1; i >= 0; i--, length++) {
                bits.set(length, getBit(value, i));
            }
        }

        int length() {
            return length;
        }

        byte[] toBytes() {
            byte[] result = new byte[(length + 7) / 8];
            for (int i = 0; i < length; i++) {
                result[i >>> 3] |= (byte) ((bits.get(i) ? 1 : 0) << (7 - (i & 7)));
            }
            return result;
        }
    }
}

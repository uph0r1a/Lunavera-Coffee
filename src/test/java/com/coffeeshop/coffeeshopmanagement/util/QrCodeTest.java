package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class QrCodeTest {

    /** Reference matrix for "Lunavera", level M, version 1, mask 4 - produced by an independent
     *  QR implementation (Python "qrcode"), so this pins the data placement, Reed-Solomon codes,
     *  format bits and mask against something other than this class itself. */
    private static final String[] LUNAVERA_V1_MASK4 = {
            "111111101011101111111",
            "100000100111101000001",
            "101110100111001011101",
            "101110101010001011101",
            "101110101101101011101",
            "100000101110101000001",
            "111111101010101111111",
            "000000001110000000000",
            "100010111010111111001",
            "010001011100111011100",
            "010001111010110010010",
            "100000011001100100000",
            "100000100001011000000",
            "000000001101100111011",
            "111111101111001011110",
            "100000100100011010000",
            "101110101010111100010",
            "101110100010111010111",
            "101110100100110010000",
            "100000100111100100000",
            "111111101101001101001"};

    @Test
    public void matchesIndependentReferenceEncoder() {
        QrCode qr = QrCode.encodeText("Lunavera", 4);
        assertEquals(1, qr.getVersion());
        assertEquals(21, qr.getSize());
        for (int y = 0; y < 21; y++) {
            for (int x = 0; x < 21; x++) {
                assertEquals("module (" + x + "," + y + ")",
                        LUNAVERA_V1_MASK4[y].charAt(x) == '1', qr.getModule(x, y));
            }
        }
    }

    @Test
    public void picksSmallestVersionThatFits() {
        assertEquals(1, QrCode.encodeText("x".repeat(14)).getVersion()); // version 1-M holds 14 bytes
        assertEquals(2, QrCode.encodeText("x".repeat(15)).getVersion());
        assertEquals(40, QrCode.encodeText("x".repeat(QrCode.MAX_BYTES)).getVersion());
    }

    @Test
    public void sizeFollowsVersionAndOutsideIsLight() {
        QrCode qr = QrCode.encodeText("x".repeat(100));
        assertEquals(qr.getVersion() * 4 + 17, qr.getSize());
        assertFalse(qr.getModule(-1, 0));
        assertFalse(qr.getModule(0, qr.getSize()));
    }

    @Test
    public void finderPatternsAndTimingArePresent() {
        QrCode qr = QrCode.encodeText("LUNAVERA|HD12|125000|20261009051200");
        int n = qr.getSize();
        for (int[] corner : new int[][]{{0, 0}, {n - 7, 0}, {0, n - 7}}) {
            assertTrue(qr.getModule(corner[0], corner[1]));
            assertFalse(qr.getModule(corner[0] + 1, corner[1] + 1));
            assertTrue(qr.getModule(corner[0] + 3, corner[1] + 3));
        }
        for (int i = 8; i < n - 8; i++) {
            assertEquals(i % 2 == 0, qr.getModule(i, 6));
            assertEquals(i % 2 == 0, qr.getModule(6, i));
        }
    }

    @Test
    public void handlesNonAsciiText() {
        QrCode qr = QrCode.encodeText("Hóa đơn – Cà phê ☕");
        assertTrue(qr.getSize() >= 21);
    }

    @Test
    public void sameInputSameSymbol() {
        QrCode a = QrCode.encodeText("LUNAVERA|HD7|50000|20261009");
        QrCode b = QrCode.encodeText("LUNAVERA|HD7|50000|20261009");
        assertEquals(a.getMask(), b.getMask());
        for (int y = 0; y < a.getSize(); y++) {
            for (int x = 0; x < a.getSize(); x++) {
                assertEquals(a.getModule(x, y), b.getModule(x, y));
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNull() {
        QrCode.encodeText(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooLongText() {
        QrCode.encodeText("x".repeat(QrCode.MAX_BYTES + 1));
    }
}

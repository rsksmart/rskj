package org.ethereum.crypto.cryptohash;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * Created by bakaking on 25/10/2019.
 */
public class Black2bTest {

    @Test
    public void testF() {
        long[] h = {
                0x6a09e667f2bdc948L, 0xbb67ae8584caa73bL,
                0x3c6ef372fe94f82bL, 0xa54ff53a5f1d36f1L,
                0x510e527fade682d1L, 0x9b05688c2b3e6c1fL,
                0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L,
        };
        long[] m = {
                0x0000000000636261, 0x0000000000000000, 0x0000000000000000,
                0x0000000000000000, 0x0000000000000000, 0x0000000000000000,
                0x0000000000000000, 0x0000000000000000, 0x0000000000000000,
                0x0000000000000000, 0x0000000000000000, 0x0000000000000000,
                0x0000000000000000, 0x0000000000000000, 0x0000000000000000,
                0x0000000000000000,
        };
        long[] c = {3, 0};
        boolean f = true;
        int rounds = 12;

        long[] expected = {
                0x0D4D1C983FA580BAL, 0xE9F6129FB697276AL,
                0xB7C45A68142F214CL, 0xD1A2FFDB6FBB124BL,
                0x2D79AB2A39C5877DL, 0x95CC3345DED552C2L,
                0x5A92F1DBA88AD318L, 0x239900D4ED8623B9L};

        Blake2b.F(h, m, c, f, rounds);
        assertArrayEquals(expected, h);
    }
}

package org.ethereum.crypto.cryptohash;

/**
 * Created by bakaking on 25/10/2019.
 */
public class Blake2b {

    private Blake2b() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * IV is an initialization vector for BLAKE2b
     */
    private static final long[] IV = {
            0x6a09e667f3bcc908L, 0xbb67ae8584caa73bL, 0x3c6ef372fe94f82bL, 0xa54ff53a5f1d36f1L,
            0x510e527fade682d1L, 0x9b05688c2b3e6c1fL, 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L,
    };

    /**
     * the PRECOMPUTED values for BLAKE2b
     * there are 10 16-byte arrays - one for each round
     * the entries are calculated from the sigma constants.
     */
    private static final byte[][] PRECOMPUTED = {
            {0, 2, 4, 6, 1, 3, 5, 7, 8, 10, 12, 14, 9, 11, 13, 15},
            {14, 4, 9, 13, 10, 8, 15, 6, 1, 0, 11, 5, 12, 2, 7, 3},
            {11, 12, 5, 15, 8, 0, 2, 13, 10, 3, 7, 9, 14, 6, 1, 4},
            {7, 3, 13, 11, 9, 1, 12, 14, 2, 5, 4, 15, 6, 10, 0, 8},
            {9, 5, 2, 10, 0, 7, 4, 15, 14, 11, 6, 3, 1, 12, 8, 13},
            {2, 6, 0, 8, 12, 10, 11, 3, 4, 7, 15, 1, 13, 5, 14, 9},
            {12, 1, 14, 4, 5, 15, 13, 10, 0, 6, 9, 8, 7, 3, 2, 11},
            {13, 7, 12, 3, 11, 14, 1, 9, 5, 15, 8, 2, 0, 4, 6, 10},
            {6, 14, 11, 0, 15, 9, 3, 8, 12, 13, 1, 10, 2, 7, 4, 5},
            {10, 8, 7, 1, 2, 4, 6, 5, 15, 9, 3, 13, 11, 14, 12, 0},
    };

    /**
     * F is a compression function for BLAKE2b. The state vector
     * provided as the first parameter is modified by the function.
     *
     * @param h      the state vector
     * @param m      the message block vector
     * @param c      offset counter
     * @param f      final block indicator flag
     * @param rounds number of rounds
     */
    public static void F(long[] h, long[] m, long[] c, boolean f, long rounds) {

        long t0 = c[0];
        long t1 = c[1];

        long[] v = new long[16];
        System.arraycopy(h, 0, v, 0, 8);
        System.arraycopy(IV, 0, v, 8, 8);

        v[12] ^= t0;
        v[13] ^= t1;

        if (f) {
            v[14] ^= 0xffffffffffffffffL;
        }

        for (long j = 0; j < rounds; ++j) {
            byte[] s = PRECOMPUTED[(int) (j % 10)];

            mix(v, m[s[0]], m[s[4]], 0, 4, 8, 12);
            mix(v, m[s[1]], m[s[5]], 1, 5, 9, 13);
            mix(v, m[s[2]], m[s[6]], 2, 6, 10, 14);
            mix(v, m[s[3]], m[s[7]], 3, 7, 11, 15);
            mix(v, m[s[8]], m[s[12]], 0, 5, 10, 15);
            mix(v, m[s[9]], m[s[13]], 1, 6, 11, 12);
            mix(v, m[s[10]], m[s[14]], 2, 7, 8, 13);
            mix(v, m[s[11]], m[s[15]], 3, 4, 9, 14);
        }

        // update h:
        for (int offset = 0; offset < h.length; offset++) {
            h[offset] ^= v[offset] ^ v[offset + 8];
        }
    }

    private static void mix(long[] v,
            final long a, final long b, final int i, final int j, final int k, final int l) {
        v[i] += a + v[j];
        v[l] = Long.rotateLeft(v[l] ^ v[i], -32);
        v[k] += v[l];
        v[j] = Long.rotateLeft(v[j] ^ v[k], -24);

        v[i] += b + v[j];
        v[l] = Long.rotateLeft(v[l] ^ v[i], -16);
        v[k] += v[l];
        v[j] = Long.rotateLeft(v[j] ^ v[k], -63);
    }
}

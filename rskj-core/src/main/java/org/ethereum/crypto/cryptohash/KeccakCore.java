/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

// $Id: KeccakCore.java 258 2011-07-15 22:16:50Z tp $

package org.ethereum.crypto.cryptohash;

/**
 * This class implements the core operations for the Keccak digest
 * algorithm.
 *
 * <pre>
 * ==========================(LICENSE BEGIN)============================
 *
 * Copyright (c) 2007-2010  Projet RNRT SAPHIR
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * ===========================(LICENSE END)=============================
 * </pre>
 *
 * @version   $Revision: 258 $
 * @author    Thomas Pornin &lt;thomas.pornin@cryptolog.com&gt;
 */

abstract class KeccakCore extends DigestEngine {

	KeccakCore()
	{
	}

	private long[] a;
	private byte[] tmpOut;

	private static final long[] RC = {
		0x0000000000000001L, 0x0000000000008082L,
		0x800000000000808AL, 0x8000000080008000L,
		0x000000000000808BL, 0x0000000080000001L,
		0x8000000080008081L, 0x8000000000008009L,
		0x000000000000008AL, 0x0000000000000088L,
		0x0000000080008009L, 0x000000008000000AL,
		0x000000008000808BL, 0x800000000000008BL,
		0x8000000000008089L, 0x8000000000008003L,
		0x8000000000008002L, 0x8000000000000080L,
		0x000000000000800AL, 0x800000008000000AL,
		0x8000000080008081L, 0x8000000000008080L,
		0x0000000080000001L, 0x8000000080008008L
	};

	/**
	 * Encode the 64-bit word {@code val} into the array
	 * {@code buf} at offset {@code off}, in little-endian
	 * convention (least significant byte first).
	 *
	 * @param val   the value to encode
	 * @param buf   the destination buffer
	 * @param off   the destination offset
	 */
	private static final void encodeLELong(long val, byte[] buf, int off)
	{
		buf[off + 0] = (byte)val;
		buf[off + 1] = (byte)(val >>> 8);
		buf[off + 2] = (byte)(val >>> 16);
		buf[off + 3] = (byte)(val >>> 24);
		buf[off + 4] = (byte)(val >>> 32);
		buf[off + 5] = (byte)(val >>> 40);
		buf[off + 6] = (byte)(val >>> 48);
		buf[off + 7] = (byte)(val >>> 56);
	}

	/**
	 * Decode a 64-bit little-endian word from the array {@code buf}
	 * at offset {@code off}.
	 *
	 * @param buf   the source buffer
	 * @param off   the source offset
	 * @return  the decoded value
	 */
	private static final long decodeLELong(byte[] buf, int off)
	{
		return (buf[off + 0] & 0xFFL)
			| ((buf[off + 1] & 0xFFL) << 8)
			| ((buf[off + 2] & 0xFFL) << 16)
			| ((buf[off + 3] & 0xFFL) << 24)
			| ((buf[off + 4] & 0xFFL) << 32)
			| ((buf[off + 5] & 0xFFL) << 40)
			| ((buf[off + 6] & 0xFFL) << 48)
			| ((buf[off + 7] & 0xFFL) << 56);
	}

	/** @see org.ethereum.crypto.cryptohash.DigestEngine */
	protected void engineReset()
	{
		doReset();
	}

	/** @see org.ethereum.crypto.cryptohash.DigestEngine */
	protected void processBlock(byte[] data)
	{
		/* Input block */
		for (int i = 0; i < data.length; i += 8) {
            a[i >>> 3] ^= decodeLELong(data, i);
        }

		long t0;
		long t1;
		long t2;
		long t3;
		long t4;
		long tt0;
		long tt1;
		long tt2;
		long tt3;
		long tt4;
		long t;
		long kt;
		long c0;
		long c1;
		long c2;
		long c3;
		long c4;
		long bnn;

		/*
		 * Unrolling four rounds kills performance big time
		 * on Intel x86 Core2, in both 32-bit and 64-bit modes
		 * (less than 1 MB/s instead of 55 MB/s on x86-64).
		 * Unrolling two rounds appears to be fine.
		 */
		for (int j = 0; j < 24; j += 2) {

			tt0 = a[ 1] ^ a[ 6];
			tt1 = a[11] ^ a[16];
			tt0 ^= a[21] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[ 4] ^ a[ 9];
			tt3 = a[14] ^ a[19];
			tt0 ^= a[24];
			tt2 ^= tt3;
			t0 = tt0 ^ tt2;

			tt0 = a[ 2] ^ a[ 7];
			tt1 = a[12] ^ a[17];
			tt0 ^= a[22] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[ 0] ^ a[ 5];
			tt3 = a[10] ^ a[15];
			tt0 ^= a[20];
			tt2 ^= tt3;
			t1 = tt0 ^ tt2;

			tt0 = a[ 3] ^ a[ 8];
			tt1 = a[13] ^ a[18];
			tt0 ^= a[23] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[ 1] ^ a[ 6];
			tt3 = a[11] ^ a[16];
			tt0 ^= a[21];
			tt2 ^= tt3;
			t2 = tt0 ^ tt2;

			tt0 = a[ 4] ^ a[ 9];
			tt1 = a[14] ^ a[19];
			tt0 ^= a[24] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[ 2] ^ a[ 7];
			tt3 = a[12] ^ a[17];
			tt0 ^= a[22];
			tt2 ^= tt3;
			t3 = tt0 ^ tt2;

			tt0 = a[ 0] ^ a[ 5];
			tt1 = a[10] ^ a[15];
			tt0 ^= a[20] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[ 3] ^ a[ 8];
			tt3 = a[13] ^ a[18];
			tt0 ^= a[23];
			tt2 ^= tt3;
			t4 = tt0 ^ tt2;

			a[ 0] = a[ 0] ^ t0;
			a[ 5] = a[ 5] ^ t0;
			a[10] = a[10] ^ t0;
			a[15] = a[15] ^ t0;
			a[20] = a[20] ^ t0;
			a[ 1] = a[ 1] ^ t1;
			a[ 6] = a[ 6] ^ t1;
			a[11] = a[11] ^ t1;
			a[16] = a[16] ^ t1;
			a[21] = a[21] ^ t1;
			a[ 2] = a[ 2] ^ t2;
			a[ 7] = a[ 7] ^ t2;
			a[12] = a[12] ^ t2;
			a[17] = a[17] ^ t2;
			a[22] = a[22] ^ t2;
			a[ 3] = a[ 3] ^ t3;
			a[ 8] = a[ 8] ^ t3;
			a[13] = a[13] ^ t3;
			a[18] = a[18] ^ t3;
			a[23] = a[23] ^ t3;
			a[ 4] = a[ 4] ^ t4;
			a[ 9] = a[ 9] ^ t4;
			a[14] = a[14] ^ t4;
			a[19] = a[19] ^ t4;
			a[24] = a[24] ^ t4;
			a[ 5] = (a[ 5] << 36) | (a[ 5] >>> (64 - 36));
			a[10] = (a[10] << 3) | (a[10] >>> (64 - 3));
			a[15] = (a[15] << 41) | (a[15] >>> (64 - 41));
			a[20] = (a[20] << 18) | (a[20] >>> (64 - 18));
			a[ 1] = (a[ 1] << 1) | (a[ 1] >>> (64 - 1));
			a[ 6] = (a[ 6] << 44) | (a[ 6] >>> (64 - 44));
			a[11] = (a[11] << 10) | (a[11] >>> (64 - 10));
			a[16] = (a[16] << 45) | (a[16] >>> (64 - 45));
			a[21] = (a[21] << 2) | (a[21] >>> (64 - 2));
			a[ 2] = (a[ 2] << 62) | (a[ 2] >>> (64 - 62));
			a[ 7] = (a[ 7] << 6) | (a[ 7] >>> (64 - 6));
			a[12] = (a[12] << 43) | (a[12] >>> (64 - 43));
			a[17] = (a[17] << 15) | (a[17] >>> (64 - 15));
			a[22] = (a[22] << 61) | (a[22] >>> (64 - 61));
			a[ 3] = (a[ 3] << 28) | (a[ 3] >>> (64 - 28));
			a[ 8] = (a[ 8] << 55) | (a[ 8] >>> (64 - 55));
			a[13] = (a[13] << 25) | (a[13] >>> (64 - 25));
			a[18] = (a[18] << 21) | (a[18] >>> (64 - 21));
			a[23] = (a[23] << 56) | (a[23] >>> (64 - 56));
			a[ 4] = (a[ 4] << 27) | (a[ 4] >>> (64 - 27));
			a[ 9] = (a[ 9] << 20) | (a[ 9] >>> (64 - 20));
			a[14] = (a[14] << 39) | (a[14] >>> (64 - 39));
			a[19] = (a[19] << 8) | (a[19] >>> (64 - 8));
			a[24] = (a[24] << 14) | (a[24] >>> (64 - 14));
			bnn = ~a[12];
			kt = a[ 6] | a[12];
			c0 = a[ 0] ^ kt;
			kt = bnn | a[18];
			c1 = a[ 6] ^ kt;
			kt = a[18] & a[24];
			c2 = a[12] ^ kt;
			kt = a[24] | a[ 0];
			c3 = a[18] ^ kt;
			kt = a[ 0] & a[ 6];
			c4 = a[24] ^ kt;
			a[ 0] = c0;
			a[ 6] = c1;
			a[12] = c2;
			a[18] = c3;
			a[24] = c4;
			bnn = ~a[22];
			kt = a[ 9] | a[10];
			c0 = a[ 3] ^ kt;
			kt = a[10] & a[16];
			c1 = a[ 9] ^ kt;
			kt = a[16] | bnn;
			c2 = a[10] ^ kt;
			kt = a[22] | a[ 3];
			c3 = a[16] ^ kt;
			kt = a[ 3] & a[ 9];
			c4 = a[22] ^ kt;
			a[ 3] = c0;
			a[ 9] = c1;
			a[10] = c2;
			a[16] = c3;
			a[22] = c4;
			bnn = ~a[19];
			kt = a[ 7] | a[13];
			c0 = a[ 1] ^ kt;
			kt = a[13] & a[19];
			c1 = a[ 7] ^ kt;
			kt = bnn & a[20];
			c2 = a[13] ^ kt;
			kt = a[20] | a[ 1];
			c3 = bnn ^ kt;
			kt = a[ 1] & a[ 7];
			c4 = a[20] ^ kt;
			a[ 1] = c0;
			a[ 7] = c1;
			a[13] = c2;
			a[19] = c3;
			a[20] = c4;
			bnn = ~a[17];
			kt = a[ 5] & a[11];
			c0 = a[ 4] ^ kt;
			kt = a[11] | a[17];
			c1 = a[ 5] ^ kt;
			kt = bnn | a[23];
			c2 = a[11] ^ kt;
			kt = a[23] & a[ 4];
			c3 = bnn ^ kt;
			kt = a[ 4] | a[ 5];
			c4 = a[23] ^ kt;
			a[ 4] = c0;
			a[ 5] = c1;
			a[11] = c2;
			a[17] = c3;
			a[23] = c4;
			bnn = ~a[ 8];
			kt = bnn & a[14];
			c0 = a[ 2] ^ kt;
			kt = a[14] | a[15];
			c1 = bnn ^ kt;
			kt = a[15] & a[21];
			c2 = a[14] ^ kt;
			kt = a[21] | a[ 2];
			c3 = a[15] ^ kt;
			kt = a[ 2] & a[ 8];
			c4 = a[21] ^ kt;
			a[ 2] = c0;
			a[ 8] = c1;
			a[14] = c2;
			a[15] = c3;
			a[21] = c4;
			a[ 0] = a[ 0] ^ RC[j + 0];

			tt0 = a[ 6] ^ a[ 9];
			tt1 = a[ 7] ^ a[ 5];
			tt0 ^= a[ 8] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[24] ^ a[22];
			tt3 = a[20] ^ a[23];
			tt0 ^= a[21];
			tt2 ^= tt3;
			t0 = tt0 ^ tt2;

			tt0 = a[12] ^ a[10];
			tt1 = a[13] ^ a[11];
			tt0 ^= a[14] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[ 0] ^ a[ 3];
			tt3 = a[ 1] ^ a[ 4];
			tt0 ^= a[ 2];
			tt2 ^= tt3;
			t1 = tt0 ^ tt2;

			tt0 = a[18] ^ a[16];
			tt1 = a[19] ^ a[17];
			tt0 ^= a[15] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[ 6] ^ a[ 9];
			tt3 = a[ 7] ^ a[ 5];
			tt0 ^= a[ 8];
			tt2 ^= tt3;
			t2 = tt0 ^ tt2;

			tt0 = a[24] ^ a[22];
			tt1 = a[20] ^ a[23];
			tt0 ^= a[21] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[12] ^ a[10];
			tt3 = a[13] ^ a[11];
			tt0 ^= a[14];
			tt2 ^= tt3;
			t3 = tt0 ^ tt2;

			tt0 = a[ 0] ^ a[ 3];
			tt1 = a[ 1] ^ a[ 4];
			tt0 ^= a[ 2] ^ tt1;
			tt0 = (tt0 << 1) | (tt0 >>> 63);
			tt2 = a[18] ^ a[16];
			tt3 = a[19] ^ a[17];
			tt0 ^= a[15];
			tt2 ^= tt3;
			t4 = tt0 ^ tt2;

			a[ 0] = a[ 0] ^ t0;
			a[ 3] = a[ 3] ^ t0;
			a[ 1] = a[ 1] ^ t0;
			a[ 4] = a[ 4] ^ t0;
			a[ 2] = a[ 2] ^ t0;
			a[ 6] = a[ 6] ^ t1;
			a[ 9] = a[ 9] ^ t1;
			a[ 7] = a[ 7] ^ t1;
			a[ 5] = a[ 5] ^ t1;
			a[ 8] = a[ 8] ^ t1;
			a[12] = a[12] ^ t2;
			a[10] = a[10] ^ t2;
			a[13] = a[13] ^ t2;
			a[11] = a[11] ^ t2;
			a[14] = a[14] ^ t2;
			a[18] = a[18] ^ t3;
			a[16] = a[16] ^ t3;
			a[19] = a[19] ^ t3;
			a[17] = a[17] ^ t3;
			a[15] = a[15] ^ t3;
			a[24] = a[24] ^ t4;
			a[22] = a[22] ^ t4;
			a[20] = a[20] ^ t4;
			a[23] = a[23] ^ t4;
			a[21] = a[21] ^ t4;
			a[ 3] = (a[ 3] << 36) | (a[ 3] >>> (64 - 36));
			a[ 1] = (a[ 1] << 3) | (a[ 1] >>> (64 - 3));
			a[ 4] = (a[ 4] << 41) | (a[ 4] >>> (64 - 41));
			a[ 2] = (a[ 2] << 18) | (a[ 2] >>> (64 - 18));
			a[ 6] = (a[ 6] << 1) | (a[ 6] >>> (64 - 1));
			a[ 9] = (a[ 9] << 44) | (a[ 9] >>> (64 - 44));
			a[ 7] = (a[ 7] << 10) | (a[ 7] >>> (64 - 10));
			a[ 5] = (a[ 5] << 45) | (a[ 5] >>> (64 - 45));
			a[ 8] = (a[ 8] << 2) | (a[ 8] >>> (64 - 2));
			a[12] = (a[12] << 62) | (a[12] >>> (64 - 62));
			a[10] = (a[10] << 6) | (a[10] >>> (64 - 6));
			a[13] = (a[13] << 43) | (a[13] >>> (64 - 43));
			a[11] = (a[11] << 15) | (a[11] >>> (64 - 15));
			a[14] = (a[14] << 61) | (a[14] >>> (64 - 61));
			a[18] = (a[18] << 28) | (a[18] >>> (64 - 28));
			a[16] = (a[16] << 55) | (a[16] >>> (64 - 55));
			a[19] = (a[19] << 25) | (a[19] >>> (64 - 25));
			a[17] = (a[17] << 21) | (a[17] >>> (64 - 21));
			a[15] = (a[15] << 56) | (a[15] >>> (64 - 56));
			a[24] = (a[24] << 27) | (a[24] >>> (64 - 27));
			a[22] = (a[22] << 20) | (a[22] >>> (64 - 20));
			a[20] = (a[20] << 39) | (a[20] >>> (64 - 39));
			a[23] = (a[23] << 8) | (a[23] >>> (64 - 8));
			a[21] = (a[21] << 14) | (a[21] >>> (64 - 14));
			bnn = ~a[13];
			kt = a[ 9] | a[13];
			c0 = a[ 0] ^ kt;
			kt = bnn | a[17];
			c1 = a[ 9] ^ kt;
			kt = a[17] & a[21];
			c2 = a[13] ^ kt;
			kt = a[21] | a[ 0];
			c3 = a[17] ^ kt;
			kt = a[ 0] & a[ 9];
			c4 = a[21] ^ kt;
			a[ 0] = c0;
			a[ 9] = c1;
			a[13] = c2;
			a[17] = c3;
			a[21] = c4;
			bnn = ~a[14];
			kt = a[22] | a[ 1];
			c0 = a[18] ^ kt;
			kt = a[ 1] & a[ 5];
			c1 = a[22] ^ kt;
			kt = a[ 5] | bnn;
			c2 = a[ 1] ^ kt;
			kt = a[14] | a[18];
			c3 = a[ 5] ^ kt;
			kt = a[18] & a[22];
			c4 = a[14] ^ kt;
			a[18] = c0;
			a[22] = c1;
			a[ 1] = c2;
			a[ 5] = c3;
			a[14] = c4;
			bnn = ~a[23];
			kt = a[10] | a[19];
			c0 = a[ 6] ^ kt;
			kt = a[19] & a[23];
			c1 = a[10] ^ kt;
			kt = bnn & a[ 2];
			c2 = a[19] ^ kt;
			kt = a[ 2] | a[ 6];
			c3 = bnn ^ kt;
			kt = a[ 6] & a[10];
			c4 = a[ 2] ^ kt;
			a[ 6] = c0;
			a[10] = c1;
			a[19] = c2;
			a[23] = c3;
			a[ 2] = c4;
			bnn = ~a[11];
			kt = a[ 3] & a[ 7];
			c0 = a[24] ^ kt;
			kt = a[ 7] | a[11];
			c1 = a[ 3] ^ kt;
			kt = bnn | a[15];
			c2 = a[ 7] ^ kt;
			kt = a[15] & a[24];
			c3 = bnn ^ kt;
			kt = a[24] | a[ 3];
			c4 = a[15] ^ kt;
			a[24] = c0;
			a[ 3] = c1;
			a[ 7] = c2;
			a[11] = c3;
			a[15] = c4;
			bnn = ~a[16];
			kt = bnn & a[20];
			c0 = a[12] ^ kt;
			kt = a[20] | a[ 4];
			c1 = bnn ^ kt;
			kt = a[ 4] & a[ 8];
			c2 = a[20] ^ kt;
			kt = a[ 8] | a[12];
			c3 = a[ 4] ^ kt;
			kt = a[12] & a[16];
			c4 = a[ 8] ^ kt;
			a[12] = c0;
			a[16] = c1;
			a[20] = c2;
			a[ 4] = c3;
			a[ 8] = c4;
			a[ 0] = a[ 0] ^ RC[j + 1];
			t = a[ 5];
			a[ 5] = a[18];
			a[18] = a[11];
			a[11] = a[10];
			a[10] = a[ 6];
			a[ 6] = a[22];
			a[22] = a[20];
			a[20] = a[12];
			a[12] = a[19];
			a[19] = a[15];
			a[15] = a[24];
			a[24] = a[ 8];
			a[ 8] = t;
			t = a[ 1];
			a[ 1] = a[ 9];
			a[ 9] = a[14];
			a[14] = a[ 2];
			a[ 2] = a[13];
			a[13] = a[23];
			a[23] = a[ 4];
			a[ 4] = a[21];
			a[21] = a[16];
			a[16] = a[ 3];
			a[ 3] = a[17];
			a[17] = a[ 7];
			a[ 7] = t;
		}
	}

	/** @see org.ethereum.crypto.cryptohash.DigestEngine */
	protected void doPadding(byte[] out, int off)
	{
		int ptr = flush();
		byte[] buf = getBlockBuffer();
		if ((ptr + 1) == buf.length) {
			buf[ptr] = (byte)0x81;
		} else {
			buf[ptr] = (byte)0x01;
			for (int i = ptr + 1; i < (buf.length - 1); i ++) {
                buf[i] = 0;
            }
			buf[buf.length - 1] = (byte)0x80;
		}
		processBlock(buf);
		a[ 1] = ~a[ 1];
		a[ 2] = ~a[ 2];
		a[ 8] = ~a[ 8];
		a[12] = ~a[12];
		a[17] = ~a[17];
		a[20] = ~a[20];
		int dlen = getDigestLength();
		for (int i = 0; i < dlen; i += 8) {
            encodeLELong(a[i >>> 3], tmpOut, i);
        }
		System.arraycopy(tmpOut, 0, out, off, dlen);
	}

	/** @see org.ethereum.crypto.cryptohash.DigestEngine */
	protected void doInit()
	{
		a = new long[25];
		tmpOut = new byte[(getDigestLength() + 7) & ~7];
		doReset();
	}

	/** @see org.ethereum.crypto.cryptohash.Digest */
	public int getBlockLength()
	{
		return 200 - 2 * getDigestLength();
	}

	private final void doReset()
	{
		for (int i = 0; i < 25; i ++) {
            a[i] = 0;
        }
		a[ 1] = 0xFFFFFFFFFFFFFFFFL;
		a[ 2] = 0xFFFFFFFFFFFFFFFFL;
		a[ 8] = 0xFFFFFFFFFFFFFFFFL;
		a[12] = 0xFFFFFFFFFFFFFFFFL;
		a[17] = 0xFFFFFFFFFFFFFFFFL;
		a[20] = 0xFFFFFFFFFFFFFFFFL;
	}

	/** @see org.ethereum.crypto.cryptohash.DigestEngine */
	protected Digest copyState(KeccakCore dst)
	{
		System.arraycopy(a, 0, dst.a, 0, 25);
		return super.copyState(dst);
	}

	/** @see org.ethereum.crypto.cryptohash.Digest */
	public String toString()
	{
		return "Keccak-" + (getDigestLength() << 3);
	}
}

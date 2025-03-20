package org.ethereum.util;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.BigIntegers;
import org.junit.jupiter.api.Assertions;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.Tag;

class ByteUtilFuzzTest {

    @Tag("ByteUtilFuzzBUFuzz")
    @FuzzTest
    public void testBUFuzz(FuzzedDataProvider data) {
        byte b = data.consumeByte();
        String s = data.consumeString(128);
        //Check that it doesn't throw only
        ByteUtil.appendByte(s.getBytes(), b);

        byte[] bs = data.consumeBytes(256);
        if (bs.length == 0) {
            // we know how base case works
            bs = new byte[1];
        }
        int i = 0;
        while (bs[0] == 0) {
            bs[0] = data.consumeByte();
            if (i++ == 10) { //lets just skip empty input somehow
                bs[0] = 1;
            }
        }
        BigInteger bi = BigIntegers.fromUnsignedByteArray(bs);
        byte[] out = ByteUtil.bigIntegerToBytes(bi);
        if (!Arrays.equals(bs, out)) {
            System.out.println("Breakpoint");
        }
        Assertions.assertArrayEquals(bs, out);

        bs = data.consumeBytes(256);
        String hex = ByteUtil.toHexString(bs);
        Assertions.assertArrayEquals(bs, Hex.decode(hex));

        bs = data.consumeBytes(8);
        long res = ByteUtil.byteArrayToLong(bs);
        if (res < 0) { //documentation says that it's positive, but this can be negative
            //TODO: throw new IllegalStateException();
        }

        bs = data.consumeBytes(32);
        int ires = ByteUtil.byteArrayToInt(bs); //this works different than long function, it does not throw on overflow
        //TODO fix above, should not accept such long values
        if (ires < 0) { //documentation says that it's positive, but this can be negative
            //TODO: throw new IllegalStateException();
        }

        bs = data.consumeBytes(256);
        bs = ByteUtil.stripLeadingZeroes(bs);
        bi = BigIntegers.fromUnsignedByteArray(bs);
        Assertions.assertArrayEquals(bs, ByteUtil.bigIntegerToBytes(bi));

        bs = data.consumeBytes(256);
        ByteUtil.firstNonZeroByte(bs);

        i = data.consumeInt();
        out = ByteUtil.intToBytesNoLeadZeroes(i);
        Assertions.assertEquals(i, ByteUtil.byteArrayToInt(out)); //TODO should not work for negative or just update doc

        i = data.consumeInt();
        out = ByteUtil.intToBytes(i);
        Assertions.assertEquals(i, ByteUtil.byteArrayToInt(out)); //TODO should not work for negative or just update doc

        long l = data.consumeLong();
        out = ByteUtil.longToBytesNoLeadZeroes(l);
        Assertions.assertEquals(l, ByteUtil.byteArrayToLong(out)); //TODO should not work for negative or just update doc

        l = data.consumeLong();
        out = ByteUtil.longToBytes(l);
        Assertions.assertEquals(l, ByteUtil.byteArrayToLong(out)); //TODO should not work for negative or just update doc

        bs = data.consumeBytes(256);
        if (bs.length != 0 ) { //TODO: handle 0 length array
            ByteUtil.increment(bs);
        }

        bs = data.consumeBytes(32); //Maximum supported length by copyToArray
        bi = BigIntegers.fromUnsignedByteArray(bs);
        if (!bi.equals(BigInteger.ZERO)) {
            out = ByteUtil.copyToArray(bi);
            ByteUtil.fastEquals(ByteUtil.stripLeadingZeroes(bs), ByteUtil.stripLeadingZeroes(out));
        }

        bs = data.consumeBytes(256);
        i = data.consumeInt(Integer.max(0, bs.length - 10), bs.length + 10);
        ByteUtil.leftPadBytes(bs, i);

        bs = data.consumeBytes(256);
        i = ByteUtil.firstNonZeroByte(bs);
        int j = ByteUtil.numberOfLeadingZeros(bs);
        Assertions.assertTrue(
                j / 8 == i ||
                        j / 8 == bs.length
        );

        bs = data.consumeBytes(256);
        ByteUtil.nibblesToPrettyString(bs);

        bs = data.consumeBytes(256);
        ByteUtil.calcPacketLength(bs);

        bs = data.consumeBytes(256);
        ByteUtil.toHexString(bs);

        bs = data.consumeBytes(256);
        byte[] bs2 = data.consumeBytes(256);
        ByteUtil.matchingNibbleLength(bs, bs2);

        bs = data.consumeBytes(256);
        i = data.consumeInt(0, 256);
        ByteUtil.parseWord(bs, i);

        bs = data.consumeBytes(256);
        i = data.consumeInt(0, 256);
        bi = BigIntegers.fromUnsignedByteArray(bs);
        out = ByteUtil.bigIntegerToBytes(bi, i);
        Assertions.assertTrue(bi.compareTo(BigIntegers.fromUnsignedByteArray(out)) >= 0);

        bs = data.consumeBytes(256);
        i = data.consumeInt(0, 320);
        out = ByteUtil.toBytesWithLeadingZeros(bs, i);
        Assertions.assertArrayEquals(ByteUtil.stripLeadingZeroes(bs), ByteUtil.stripLeadingZeroes(out));

        bs = data.consumeBytes(256);
        if (bs.length > 0) {
            i = data.consumeInt(0, 8 * bs.length - 1);
            j = data.consumeInt(0, 1);
            ByteUtil.setBit(bs, i, j); //TODO: this throws an ERROR if out of bounds, not exception
            int k = ByteUtil.getBit(bs, i);
            Assertions.assertEquals(j, k);
        }

        bs = data.consumeBytes(256);
        bs2 = data.consumeBytes(256);
        try {
            ByteUtil.and(bs, bs2);
            ByteUtil.or(bs, bs2);
        } catch (RuntimeException e) {
            //TODO RuntimeException, really?
            if (bs.length == bs2.length) {
                throw new IllegalStateException();
            }
        }

        bs = data.consumeBytes(256);
        i = data.consumeInt(0, 8 * 320 );
        ByteUtil.shiftLeft(bs, i);

        bs = data.consumeBytes(256);
        i = data.consumeInt(0, 8 * 320 );
        ByteUtil.shiftRight(bs, i);

        bs = data.consumeBytes(256);
        bs2 = data.consumeBytes(256);
        ByteUtil.merge(bs, bs2);

        bs = data.consumeBytes(10);
        ByteUtil.isSingleZero(bs);

        bs = data.consumeBytes(10);
        ByteUtil.isAllZeroes(bs);

        bs = data.consumeBytes(256);
        bs2 = data.consumeBytes(256);
        res = ByteUtil.length(bs, bs2);
        Assertions.assertEquals(res, bs.length + bs2.length);

        
    }
}

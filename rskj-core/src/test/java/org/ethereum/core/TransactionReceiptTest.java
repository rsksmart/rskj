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

package org.ethereum.core;

import co.rsk.util.HexUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * @author Roman Mandeleil
 * @since 05.12.2014
 */
class TransactionReceiptTest {

    private static final Logger logger = LoggerFactory.getLogger("test");

    @Test
    void nullBytes_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReceipt((byte[]) null));
    }

    @Test
    void emptyBytes_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReceipt(new byte[0]));
    }

    @Test
    void type1Receipt_decode_fourFieldBody_gasUsedIsEmpty_andStatusIsSet() {
        byte[] status = new byte[]{0x01};
        byte[] cumulativeGas = new byte[]{0x52, 0x08}; // 21000
        byte[] body = RLP.encodeList(
                RLP.encodeElement(status),
                RLP.encodeElement(cumulativeGas),
                RLP.encodeElement(new byte[256]),
                RLP.encodeList()
        );
        byte[] encoded = ByteUtil.merge(new byte[]{0x01}, body);

        TransactionReceipt receipt = new TransactionReceipt(encoded);

        assertArrayEquals(status, receipt.getStatus(),
                "Type 1 receipt must decode the status from the 4-field body");
        assertArrayEquals(cumulativeGas, receipt.getCumulativeGas(),
                "Type 1 receipt must decode cumulativeGas from the 4-field body");
        assertArrayEquals(EMPTY_BYTE_ARRAY, receipt.getGasUsed(),
                "Type 1 receipt must leave gasUsed empty (derived from cumulative delta in Web3Impl)");
        assertEquals(0, receipt.getLogInfoList().size(),
                "Type 1 receipt with empty logs list must have no log entries");
    }

    @Test
    void type2Receipt_decode_fourFieldBody_gasUsedIsEmpty_andStatusIsSet() {
        byte[] status = new byte[]{0x01};
        byte[] cumulativeGas = new byte[]{(byte) 0x01, (byte) 0x86, (byte) 0xA0}; // 100000
        byte[] body = RLP.encodeList(
                RLP.encodeElement(status),
                RLP.encodeElement(cumulativeGas),
                RLP.encodeElement(new byte[256]),
                RLP.encodeList()
        );
        byte[] encoded = ByteUtil.merge(new byte[]{0x02}, body);

        TransactionReceipt receipt = new TransactionReceipt(encoded);

        assertArrayEquals(status, receipt.getStatus(),
                "Type 2 receipt must decode the status from the 4-field body");
        assertArrayEquals(cumulativeGas, receipt.getCumulativeGas(),
                "Type 2 receipt must decode cumulativeGas from the 4-field body");
        assertArrayEquals(EMPTY_BYTE_ARRAY, receipt.getGasUsed(),
                "Type 2 receipt must leave gasUsed empty (derived from cumulative delta in Web3Impl)");
    }

    @Test
    void type1Receipt_failedStatus_decode_statusIsEmpty() {
        byte[] failedStatus = EMPTY_BYTE_ARRAY;
        byte[] cumulativeGas = new byte[]{0x52, 0x08};
        byte[] body = RLP.encodeList(
                RLP.encodeElement(failedStatus),
                RLP.encodeElement(cumulativeGas),
                RLP.encodeElement(new byte[256]),
                RLP.encodeList()
        );
        byte[] encoded = ByteUtil.merge(new byte[]{0x01}, body);

        TransactionReceipt receipt = new TransactionReceipt(encoded);

        assertArrayEquals(EMPTY_BYTE_ARRAY, receipt.getStatus(),
                "Type 1 receipt with 0x-status must decode as failed (empty byte array)");
        assertFalse(receipt.isSuccessful());
    }

    @Test
    void type1Receipt_encodeDecoded_preservesFields() {
        byte[] status = new byte[]{0x01};
        byte[] cumulativeGas = new byte[]{0x52, 0x08};
        byte[] body = RLP.encodeList(
                RLP.encodeElement(status),
                RLP.encodeElement(cumulativeGas),
                RLP.encodeElement(new byte[256]),
                RLP.encodeList()
        );
        byte[] originalEncoded = ByteUtil.merge(new byte[]{0x01}, body);

        TransactionReceipt receipt = new TransactionReceipt(originalEncoded);
        assertArrayEquals(originalEncoded, receipt.getEncoded(),
                "Cached getEncoded() must return the original bytes without re-encoding");
    }

    @Test
    void test_2() {
        assertEquals("0x", HexUtils.toUnformattedJsonHex(EMPTY_BYTE_ARRAY));
        assertEquals("0x00", HexUtils.toJsonHex(EMPTY_BYTE_ARRAY));

        byte[] rlpSuccess = Hex.decode("f9010c808255aeb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c08255ae01");
        byte[] rlpFailed  = Hex.decode("f9010c808255aeb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c08255ae80");
        TransactionReceipt txReceipt = new TransactionReceipt(rlpSuccess);
        assertArrayEquals(new byte[]{0x01}, txReceipt.getStatus());
        txReceipt = new TransactionReceipt(rlpFailed);
        assertArrayEquals(EMPTY_BYTE_ARRAY, txReceipt.getStatus());
    }

    @Test // rlp decode;
    void test_1() {

        byte[] rlp = Hex.decode("f8c5a0966265cc49fa1f10f0445f035258d116563931022a3570a640af5d73a214a8da822b6fb84" +
                "0000000100000000100000000000080000000000000000000000000000000000000000000000000000000000200000000000" +
                "00014000000000400000000000440f85cf85a94d5ccd26ba09ce1d85148b5081fa3ed77949417bef842a0000000000000000" +
                "000000000459d3a7595df9eba241365f4676803586d7d199ca0436f696e73000000000000000000000000000000000000000" +
                "0000000000000008002");

        TransactionReceipt txReceipt = new TransactionReceipt(rlp);

        assertEquals(1, txReceipt.getLogInfoList().size());

        assertEquals("966265cc49fa1f10f0445f035258d116563931022a3570a640af5d73a214a8da",
                ByteUtil.toHexString(txReceipt.getPostTxState()));

        assertEquals("2b6f",
                ByteUtil.toHexString(txReceipt.getCumulativeGas()));

        assertEquals("02",
                ByteUtil.toHexString(txReceipt.getGasUsed()));

        assertEquals("00000010000000010000000000008000000000000000000000000000000000000000000000000000000000020000000000000014000000000400000000000440",
                ByteUtil.toHexString(txReceipt.getBloomFilter().getData()));

        logger.info("{}", txReceipt);
    }

    @Test
    void setGasUsed_afterEncoded_invalidatesCache() {
        byte[] rlp = Hex.decode("f8c5a0966265cc49fa1f10f0445f035258d116563931022a3570a640af5d73a214a8da822b6fb84" +
                "0000000100000000100000000000080000000000000000000000000000000000000000000000000000000000200000000000" +
                "00014000000000400000000000440f85cf85a94d5ccd26ba09ce1d85148b5081fa3ed77949417bef842a0000000000000000" +
                "000000000459d3a7595df9eba241365f4676803586d7d199ca0436f696e73000000000000000000000000000000000000000" +
                "0000000000000008002");
        TransactionReceipt receipt = new TransactionReceipt(rlp);

        byte[] firstEncoding = receipt.getEncoded();
        receipt.setGasUsed(21000L);
        byte[] secondEncoding = receipt.getEncoded();

        assertFalse(Arrays.equals(firstEncoding, secondEncoding),
                "getEncoded() must return a fresh encoding after setGasUsed()");
        TransactionReceipt roundTripped = new TransactionReceipt(secondEncoding);
        assertEquals(21000L, ByteUtil.byteArrayToLong(roundTripped.getGasUsed()));
    }

    @Test
    void setCumulativeGas_afterEncoded_invalidatesCache() {
        byte[] rlp = Hex.decode("f8c5a0966265cc49fa1f10f0445f035258d116563931022a3570a640af5d73a214a8da822b6fb84" +
                "0000000100000000100000000000080000000000000000000000000000000000000000000000000000000000200000000000" +
                "00014000000000400000000000440f85cf85a94d5ccd26ba09ce1d85148b5081fa3ed77949417bef842a0000000000000000" +
                "000000000459d3a7595df9eba241365f4676803586d7d199ca0436f696e73000000000000000000000000000000000000000" +
                "0000000000000008002");
        TransactionReceipt receipt = new TransactionReceipt(rlp);

        byte[] firstEncoding = receipt.getEncoded();
        receipt.setCumulativeGas(99999L);
        byte[] secondEncoding = receipt.getEncoded();

        assertFalse(Arrays.equals(firstEncoding, secondEncoding),
                "getEncoded() must return a fresh encoding after setCumulativeGas()");
        TransactionReceipt roundTripped = new TransactionReceipt(secondEncoding);
        assertEquals(99999L, ByteUtil.byteArrayToLong(roundTripped.getCumulativeGas()));
    }

    @Test
    void setPostTxState_afterEncoded_invalidatesCache() {
        byte[] rlp = Hex.decode("f8c5a0966265cc49fa1f10f0445f035258d116563931022a3570a640af5d73a214a8da822b6fb84" +
                "0000000100000000100000000000080000000000000000000000000000000000000000000000000000000000200000000000" +
                "00014000000000400000000000440f85cf85a94d5ccd26ba09ce1d85148b5081fa3ed77949417bef842a0000000000000000" +
                "000000000459d3a7595df9eba241365f4676803586d7d199ca0436f696e73000000000000000000000000000000000000000" +
                "0000000000000008002");
        TransactionReceipt receipt = new TransactionReceipt(rlp);

        byte[] firstEncoding = receipt.getEncoded();
        byte[] newState = new byte[] {0x01};
        receipt.setPostTxState(newState);
        byte[] secondEncoding = receipt.getEncoded();

        assertFalse(Arrays.equals(firstEncoding, secondEncoding),
                "getEncoded() must return a fresh encoding after setPostTxState()");
        TransactionReceipt roundTripped = new TransactionReceipt(secondEncoding);
        assertArrayEquals(newState, roundTripped.getPostTxState());
    }

}

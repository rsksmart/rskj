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

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.util.HexUtils;


/**
 * @author Roman Mandeleil
 * @since 05.12.2014
 */
public class TransactionReceiptTest {

    private static final Logger logger = LoggerFactory.getLogger("test");

    @Test
    public void test_2() {
        assertTrue(HexUtils.toUnformattedJsonHex(EMPTY_BYTE_ARRAY).equals("0x"));
        assertTrue(HexUtils.toJsonHex(EMPTY_BYTE_ARRAY).equals("0x00"));

        byte[] rlpSuccess = Hex.decode("f9010c808255aeb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c08255ae01");
        byte[] rlpFailed  = Hex.decode("f9010c808255aeb9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c08255ae80");
        TransactionReceipt txReceipt = new TransactionReceipt(rlpSuccess);
        assertTrue(Arrays.equals(txReceipt.getStatus(), new byte[]{0x01}));
        txReceipt = new TransactionReceipt(rlpFailed);
        assertTrue(Arrays.equals(txReceipt.getStatus(), EMPTY_BYTE_ARRAY));
    }

    @Test // rlp decode;
    public void test_1() {

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

}

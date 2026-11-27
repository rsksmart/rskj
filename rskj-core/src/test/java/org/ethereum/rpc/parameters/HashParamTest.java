/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package org.ethereum.rpc.parameters;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashParamTest {

    @Test
    void testBlockHashParam() {
        String validHash = "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8";
        String invalidHash = "invalidhash";
        String shorterHash = "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a3568";
        String invalidCharHash = "0xc2b835124172db5bdzz1bb94fa123721eacac43b5cba2499b22c7583a3568";

        BlockHashParam blockHashParam = new BlockHashParam(validHash);

        assertEquals(validHash, blockHashParam.getHash().toJsonString());

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(null));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(""));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(invalidHash));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(shorterHash));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(invalidCharHash));
    }

    @Test
    void testTxHashParam() {
        String validHash = "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8";
        String invalidHash = "invalidhash";
        String shorterHash = "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a3568";
        String invalidCharHash = "0xc2b835124172db5bdzz1bb94fa123721eacac43b5cba2499b22c7583a3568";

        TxHashParam txHashParam = new TxHashParam(validHash);

        assertEquals(validHash, txHashParam.getHash().toJsonString());

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(null));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(""));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(invalidHash));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(shorterHash));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(invalidCharHash));
    }

    @Test
    void blockHashParam_validLengthDecodingToOtherThan32Bytes_isRejected() {
        String thirtyOneBytes = "0x" + "ab".repeat(31);
        String thirtyThreeBytesWithoutPrefix = "ab".repeat(33);

        RskJsonRpcRequestException shorter = assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(thirtyOneBytes));
        RskJsonRpcRequestException longer = assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(thirtyThreeBytesWithoutPrefix));

        assertEquals("Invalid block hash: incorrect length.", shorter.getMessage());
        assertEquals("Invalid block hash: incorrect length.", longer.getMessage());
    }

    @Test
    void isPrefixedHash32Hex_onlyAcceptsPrefixAndSixtyFourHexDigits() {
        assertTrue(HashParam32.isPrefixedHash32Hex("0x" + "aB".repeat(32)));
        assertFalse(HashParam32.isPrefixedHash32Hex(null));
        assertFalse(HashParam32.isPrefixedHash32Hex("0x" + "a".repeat(63)));
        assertFalse(HashParam32.isPrefixedHash32Hex("0x" + "a".repeat(65)));
        assertFalse(HashParam32.isPrefixedHash32Hex("a".repeat(66)));
        assertFalse(HashParam32.isPrefixedHash32Hex("0x" + "g".repeat(64)));
    }
}

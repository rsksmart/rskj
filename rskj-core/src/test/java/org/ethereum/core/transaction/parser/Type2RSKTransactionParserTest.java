/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core.transaction.parser;

import org.ethereum.config.Constants;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class Type2RSKTransactionParserTest {

    private final Type2RSKTransactionParser parser = new Type2RSKTransactionParser();

    @Test
    void parse_rlpFields_returnsNull() {
        RLPList fields = RLP.decodeList(RLP.encodeList());

        assertNull(parser.parse(TransactionTypePrefix.rskNamespace((byte) 0x03), fields));
    }

    @Test
    void parse_callArguments_returnsNull() {
        CallArguments args = new CallArguments();
        args.setType("0x2");
        args.setRskSubtype("0x3");
        args.setTo("0x1234567890123456789012345678901234567890");
        args.setGas("0x5208");
        args.setGasPrice("0xa");
        args.setNonce("0x0");

        assertNull(parser.parse(
                TransactionTypePrefix.rskNamespace((byte) 0x03),
                TransactionInput.fromCallArguments(args, () -> "0"),
                (byte) 33));
    }

    @Test
    void validate_doesNotThrow() {
        assertDoesNotThrow(() -> parser.validate(0L, null, Constants.regtest()));
    }
}

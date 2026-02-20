package co.rsk.util;
/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;

import java.util.List;


public final class RpcTransactionAssertions {

    private RpcTransactionAssertions() {}


    public static long assertMinedSuccess(int rpcPort, final int maxAttempts,  final long sleepMills, String txHash) {
        try {
            JsonNode receipt = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                JsonNode response = OkHttpClientTestFixture.getJsonResponseForGetTransactionReceipt(rpcPort, txHash);
                receipt = response.get("result");
                if (receipt != null && !receipt.isNull()) {
                    break;
                }
                Thread.sleep(sleepMills);
            }

            Assertions.assertNotNull(receipt, () -> "Transaction receipt not found after " + maxAttempts + " attempts for tx " + txHash);
            Assertions.assertFalse(receipt.isNull(), () -> "Transaction receipt is null for tx " + txHash);
            Assertions.assertTrue(receipt.hasNonNull("blockNumber"), () -> "Transaction not mined (no blockNumber) for tx " + txHash);
            return HexUtils.jsonHexToLong(receipt.get("blockNumber").asText());

        } catch (Exception e) {
            Assertions.fail("Failed while asserting mined transaction " + txHash, e);
            return -1; // unreachable, required by compiler
        }
    }

    public static long assertAllMinedInSameBlock(int rpcPort, List<String> txHashes) {
        Assertions.assertFalse(txHashes.isEmpty(), "No tx hashes provided");

        Long expectedBlock = null;

        for (String txHash : txHashes) {
            long block = RpcTransactionAssertions.assertMinedSuccess(rpcPort, 50, 200, txHash);

            if (expectedBlock == null) {
                expectedBlock = block;
            } else {
                Assertions.assertEquals(
                        expectedBlock.longValue(),
                        block,
                        "Tx mined in a different block. tx=" + txHash
                );
            }
        }

        return expectedBlock;
    }

}

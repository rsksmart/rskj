package co.rsk.util;

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

/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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
package co.rsk.cli.tools;

import co.rsk.cli.PicoCliToolRskContextAware;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;

/**
 * Scans the blockchain for transactions with non-canonical nonce encoding.
 *
 * Checks the same rules as TxValidatorNonceEncodingValidator:
 *   - nonce byte length must be <= 8 (Long.BYTES)
 *   - no unnecessary leading zero bytes
 *
 * Uses the actual Transaction.getNonce() byte array from the RLP-decoded
 * block data stored in the node's database.
 *
 * Build:
 *   ./gradlew :rskj-core:fatJar -x test
 *
 * Run (mainnet):
 *   java -cp rskj-core/build/libs/rskj-core-*-all.jar \
 *       co.rsk.cli.tools.VerifyNonceEncoding \
 *       --fromBlock 0 --toBlock -1 \
 *       --output mainnet_nonce_findings.txt \
 *       --mainnet
 *
 * Run (testnet):
 *   java -cp rskj-core/build/libs/rskj-core-*-all.jar \
 *       co.rsk.cli.tools.VerifyNonceEncoding \
 *       --fromBlock 0 --toBlock -1 \
 *       --output testnet_nonce_findings.txt \
 *       --testnet
 *
 * Run (partial scan, e.g. last 100K blocks):
 *   java -cp rskj-core/build/libs/rskj-core-*-all.jar \
 *       co.rsk.cli.tools.VerifyNonceEncoding \
 *       --fromBlock 8765000 \
 *       --output mainnet_recent_findings.txt \
 *       --mainnet
 *
 * Exit codes:
 *   0 - All nonces use canonical encoding
 *   1 - Non-canonical nonce(s) found (review output file)
 */
@CommandLine.Command(name = "verify-nonce-encoding", mixinStandardHelpOptions = true, version = "verify-nonce-encoding 1.0",
        description = "Scans blockchain for transactions with non-canonical nonce encoding")
public class VerifyNonceEncoding extends PicoCliToolRskContextAware {

    @CommandLine.Option(names = {"-fb", "--fromBlock"}, description = "From block number (default: 0)", defaultValue = "0")
    private Long fromBlockNumber;

    @CommandLine.Option(names = {"-tb", "--toBlock"}, description = "To block number (default: best block)", defaultValue = "-1")
    private Long toBlockNumber;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file for findings", defaultValue = "nonce_encoding_findings.txt")
    private String outputPath;

    public static void main(String[] args) {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        BlockStore blockStore = ctx.getBlockStore();

        long bestBlockNumber = blockStore.getBestBlock().getNumber();
        long endBlock = toBlockNumber < 0 ? bestBlockNumber : Math.min(toBlockNumber, bestBlockNumber);

        System.out.printf("Scanning blocks %d to %d (%,d blocks)%n", fromBlockNumber, endBlock, endBlock - fromBlockNumber + 1);
        System.out.printf("Output: %s%n%n", outputPath);

        long totalTxs = 0;
        long findingsCount = 0;
        long startTime = System.currentTimeMillis();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write("block_number,tx_index,tx_hash,nonce_hex,nonce_byte_length,reason");
            writer.newLine();

            for (long n = fromBlockNumber; n <= endBlock; n++) {
                Block block = blockStore.getChainBlockByNumber(n);
                if (block == null) {
                    continue;
                }

                List<Transaction> txs = block.getTransactionsList();
                totalTxs += txs.size();

                for (int i = 0; i < txs.size(); i++) {
                    Transaction tx = txs.get(i);
                    byte[] nonce = tx.getNonce();

                    String reason = checkNonceEncoding(nonce);
                    if (reason != null) {
                        findingsCount++;
                        String line = String.format("%d,%d,%s,%s,%d,%s",
                                n, i,
                                tx.getHash(),
                                nonce != null ? ByteUtil.toHexString(nonce) : "null",
                                nonce != null ? nonce.length : 0,
                                reason);
                        writer.write(line);
                        writer.newLine();
                        writer.flush();

                        System.out.printf("  FINDING at block %d tx %d: %s (nonce=%s, %d bytes)%n",
                                n, i, reason,
                                nonce != null ? ByteUtil.toHexString(nonce) : "null",
                                nonce != null ? nonce.length : 0);
                    }
                }

                if (n % 100000 == 0 || n == endBlock) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    long blocksScanned = n - fromBlockNumber + 1;
                    double rate = elapsed > 0 ? blocksScanned * 1000.0 / elapsed : 0;
                    double pct = (double) blocksScanned / (endBlock - fromBlockNumber + 1) * 100;
                    System.out.printf("  [%5.1f%%] block %,d | %,d txs | %d findings | %.0f blk/s%n",
                            pct, n, totalTxs, findingsCount, rate);
                }
            }
        }

        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        System.out.printf("%nDone in %ds%n", elapsed);
        System.out.printf("  Blocks scanned:    %,d%n", endBlock - fromBlockNumber + 1);
        System.out.printf("  Transactions:      %,d%n", totalTxs);
        System.out.printf("  Non-canonical:     %d%n", findingsCount);
        System.out.printf("  Output:            %s%n", outputPath);

        if (findingsCount > 0) {
            System.out.printf("%n!! WARNING: %d non-canonical nonce(s) found. Review %s before deploying.%n", findingsCount, outputPath);
            return 1;
        } else {
            System.out.printf("%nAll nonces use canonical encoding. Safe to deploy.%n");
            return 0;
        }
    }

    private static String checkNonceEncoding(byte[] nonce) {
        if (nonce == null) {
            return null;
        }
        if (nonce.length > Long.BYTES) {
            return "byte length exceeds 8";
        }
        if (nonce.length > 1 && nonce[0] == 0) {
            return "leading zero bytes";
        }
        return null;
    }
}

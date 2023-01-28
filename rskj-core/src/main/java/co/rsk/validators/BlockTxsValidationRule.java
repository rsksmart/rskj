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

package co.rsk.validators;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.panic.PanicProcessor;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.math.BigInteger.ONE;

/**
 * Validate a transactions list.
 * It validates each transaction nonce againts the expeced sender account nonce
 * It allows many transactions with the same sender
 *
 * @return true if the transactions are valid, false if any transaction is invalid
 */
public class BlockTxsValidationRule implements BlockParentDependantValidationRule{

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final RepositoryLocator repositoryLocator;
    private final SignatureCache signatureCache;

    public BlockTxsValidationRule(RepositoryLocator repositoryLocator, SignatureCache signatureCache) {
        this.repositoryLocator = repositoryLocator;
        this.signatureCache = signatureCache;
    }

    @Override
    public boolean isValid(Block block, Block parent, BlockExecutor blockExecutor) {

        if(block == null || parent == null) {
            logger.warn("BlockTxsValidationRule - block or parent are null");
            return false;
        }

        long startGetTxList = System.nanoTime();
        List<Transaction> txs = block.getTransactionsList();
        long numberOfTxs = txs.size();
        if (txs.isEmpty()) {
            return true;
        }
        long endGetTxList = System.nanoTime();
        long startGetSnapshot = System.nanoTime();
        RepositorySnapshot parentRepo = repositoryLocator.snapshotAt(parent.getHeader());
        long endGetSnapshot = System.nanoTime();

        Map<RskAddress, BigInteger> curNonce = new HashMap<>();
        long startVerificationOfTxs = System.nanoTime();


        long startGetSender = 0;
        long endGetSender = 0;
        long startGetNonce = 0;
        long endGetNonce = 0;
        long verifyTxs = 0;
        for (Transaction tx : txs) {
            try {
                long startVerifyTx = System.nanoTime();
                tx.verify();
                long endVerifyTx = System.nanoTime();
                verifyTxs += (endVerifyTx - startVerifyTx);
            } catch (RuntimeException e) {
                logger.warn("Unable to verify transaction", e);
                return false;
            }
            startGetSender = System.nanoTime();
            RskAddress sender = signatureCache != null ? tx.getSender(signatureCache) : tx.getSender();
            endGetSender = System.nanoTime();
            startGetNonce = System.nanoTime();
            BigInteger expectedNonce = curNonce.get(sender);
            if (expectedNonce == null) {
                expectedNonce = parentRepo.getNonce(sender);
            }
            curNonce.put(sender, expectedNonce.add(ONE));
            BigInteger txNonce = new BigInteger(1, tx.getNonce());

            if (!expectedNonce.equals(txNonce)) {
                logger.warn("Invalid transaction: Tx nonce {} != expected nonce {} (parent nonce: {}): {}",
                        txNonce, expectedNonce, parentRepo.getNonce(sender), tx);

                panicProcessor.panic("invalidtransaction",
                                     String.format("Invalid transaction: Tx nonce %s != expected nonce %s (parent nonce: %s): %s",
                                                   txNonce, expectedNonce, parentRepo.getNonce(sender), tx.getHash()));

                return false;
            }
            endGetNonce = System.nanoTime();
        }

        long endVerificationOfTxs = System.nanoTime();

        if (!blockExecutor.isMetrics()) {
            String filePath_times = blockExecutor.getFilePath_timesRules();
            Path file_times = Paths.get(filePath_times);
            String header_times = "playOrGenerate,rskip144,moment,bnumber,time,txs\r";
            String playOrGenerate = blockExecutor.isPlay() ? "play" : "generate";
            long blockNumber = block.getNumber();
            boolean rskip144Active = blockExecutor.getActivationConfig().isActive(ConsensusRule.RSKIP144, blockNumber);
            String getTxList_times = playOrGenerate +","+ rskip144Active + ",getTxList," + blockNumber + "," + (endGetTxList - startGetTxList) + "," + numberOfTxs + "\r";
            String verifyTx_times_total = playOrGenerate +","+ rskip144Active + ",verifyTx," + blockNumber + "," + verifyTxs + "," + numberOfTxs + "\r";
            String getSnapshot_times = playOrGenerate +","+ rskip144Active + ",getSnapshot," + blockNumber + "," + (endGetSnapshot - startGetSnapshot) + "," + numberOfTxs + "\r";
            String verificationOfTxs_times = playOrGenerate +","+ rskip144Active + ",verificationOfTxs," + blockNumber + "," + (endVerificationOfTxs - startVerificationOfTxs) + "," + numberOfTxs + "\r";
            String getSender_times = playOrGenerate +","+ rskip144Active + ",getSender," + blockNumber + "," + (endGetSender - startGetSender) + "," + numberOfTxs + "\r";
            String getNonce_times = playOrGenerate +","+ rskip144Active + ",getNonce," + blockNumber + "," + (endGetNonce - startGetNonce) + "," + numberOfTxs + "\r";

            try {
                FileWriter myWriter_times = new FileWriter(filePath_times, true);

                if (!Files.exists(file_times) || Files.size(file_times) == 0) {
                    myWriter_times.write(header_times);
                }

                myWriter_times.write(getTxList_times);
                myWriter_times.write(getSnapshot_times);
                myWriter_times.write(verificationOfTxs_times);
                myWriter_times.write(verifyTx_times_total);
                myWriter_times.write(getSender_times);
                myWriter_times.write(getNonce_times);
                myWriter_times.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }
}

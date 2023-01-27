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
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
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

        List<Transaction> txs = block.getTransactionsList();
        if (txs.isEmpty()) {
            return true;
        }

        RepositorySnapshot parentRepo = repositoryLocator.snapshotAt(parent.getHeader());

        Map<RskAddress, BigInteger> curNonce = new HashMap<>();

        for (Transaction tx : txs) {
            try {
                tx.verify();
            } catch (RuntimeException e) {
                logger.warn("Unable to verify transaction", e);
                return false;
            }

            RskAddress sender = signatureCache != null ? tx.getSender(signatureCache) : tx.getSender();
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
        }

        return true;
    }
}

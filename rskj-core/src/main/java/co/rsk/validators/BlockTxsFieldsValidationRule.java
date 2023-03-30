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

import co.rsk.core.bc.BlockExecutor;
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by SDL on 12/4/2017.
 */
public class BlockTxsFieldsValidationRule implements BlockParentDependantValidationRule {
    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private final SignatureCache signatureCache;
    
    public BlockTxsFieldsValidationRule(SignatureCache signatureCache) {
        this.signatureCache = signatureCache;
    }

    @Override
    public boolean isValid(Block block, Block parent, BlockExecutor blockExecutor) {
        if (block == null) {
            logger.warn("BlockTxsFieldsValidationRule - block is null");
            return false;
        }

        List<Transaction> txs = block.getTransactionsList();
        for (Transaction tx : txs) {
            try {
                tx.verify(signatureCache);
            } catch (RuntimeException e) {
                logger.warn("Unable to verify transaction", e);
                return false;
            }
        }

        return true;
    }
}

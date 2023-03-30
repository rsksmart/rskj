/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

import co.rsk.core.Coin;
import co.rsk.core.bc.BlockExecutor;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Validates that all block's transactions have lower gas price than the maximum allowed
 */
public class BlockTxsMaxGasPriceRule implements BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private final ActivationConfig activationConfig;

    public BlockTxsMaxGasPriceRule(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
    }

    @Override
    public boolean isValid(Block block, BlockExecutor blockExecutor) {
        boolean isRskip252Enabled = activationConfig.isActive(ConsensusRule.RSKIP252, block.getNumber());
        if (!isRskip252Enabled) {
            return true;
        }

        Coin minGasPrice = block.getMinimumGasPrice();

        List<Transaction> txs = block.getTransactionsList();
        for (Transaction tx : txs) {
            if (TxGasPriceCap.FOR_BLOCK.isSurpassed(tx, minGasPrice)) {
                logger.warn("Tx gas price={} is above the cap. Block={}, Tx={}", tx.getGasPrice(), block.getHash(), tx.getHash());
                return false;
            }
        }

        return true;
    }
}

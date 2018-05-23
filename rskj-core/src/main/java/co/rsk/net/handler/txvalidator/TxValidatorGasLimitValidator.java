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

package co.rsk.net.handler.txvalidator;

import co.rsk.core.Coin;
import org.ethereum.config.Constants;
import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * Checks that the transaction gas limit is lower than the `block` gas limit
 * though there's no check that the actual block gas limit is used
 * Also Checks that the transaction gas limit is not higher than the max allowed value
 */
public class TxValidatorGasLimitValidator implements TxValidatorStep {
    private static final Logger logger = LoggerFactory.getLogger("txvalidator");

    @Override
    public boolean validate(Transaction tx, @Nullable AccountState state, BigInteger gasLimit, Coin minimumGasPrice, long bestBlockNumber, boolean isFreeTx) {
        BigInteger txGasLimit = tx.getGasLimitAsInteger();

        if (txGasLimit.compareTo(gasLimit) <= 0 && txGasLimit.compareTo(Constants.getTransactionGasCap()) <= 0) {
            return true;
        }

        logger.warn("Invalid transaction {}: its gas limit {} is higher than the block gas limit {}", tx.getHash(), txGasLimit, gasLimit);

        return false;
    }
}

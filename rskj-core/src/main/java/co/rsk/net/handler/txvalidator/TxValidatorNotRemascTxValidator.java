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
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * Checks that a transaction is not a Remasc type transaction. Helps to simplify some code.
 *
 * Transaction must not be null
 */
public class TxValidatorNotRemascTxValidator implements TxValidatorStep {
    private static final Logger logger = LoggerFactory.getLogger("txvalidator");

    @Override
    public boolean validate(Transaction tx, @Nullable AccountState state, BigInteger gasLimit, Coin minimumGasPrice, long bestBlockNumber, boolean isFreeTx) {
        if (!(tx instanceof RemascTransaction)) {
            return true;
        }

        logger.warn("Invalid transaction {}: it is a Remasc transaction", tx.getHash());

        return false;
    }

}

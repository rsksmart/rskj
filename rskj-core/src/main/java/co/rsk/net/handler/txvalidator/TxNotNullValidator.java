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
import co.rsk.net.TransactionValidationResult;
import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * Simple check that a tx is not null.
 *
 * Looks a little overhead, but simplifies a little bit the code in other places
 */
public class TxNotNullValidator implements  TxValidatorStep {

    @Override
    public TransactionValidationResult validate(Transaction tx, @Nullable AccountState state, BigInteger gasLimit, Coin minimumGasPrice, long bestBlockNumber, boolean isFreeTx) {
        if (tx != null) {
            return TransactionValidationResult.ok();
        }

        return TransactionValidationResult.withError("transaction is null");
    }

}

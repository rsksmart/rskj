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
import java.math.BigInteger;
import javax.annotation.Nullable;
import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;

/**
 * Checks that the transaction nonce is not too higher than the account nonce. This helps limiting
 * the in memory transactions for a given address
 */
public class TxValidatorNonceRangeValidator implements TxValidatorStep {

    @Override
    public TransactionValidationResult validate(
            Transaction tx,
            @Nullable AccountState state,
            BigInteger gasLimit,
            Coin minimumGasPrice,
            long bestBlockNumber,
            boolean isFreeTx) {
        BigInteger nonce = tx.getNonceAsInteger();
        BigInteger stateNonce = state == null ? BigInteger.ZERO : state.getNonce();
        BigInteger maxNumberOfTxsPerAddress = BigInteger.valueOf(4);

        if (stateNonce.compareTo(nonce) > 0) {
            return TransactionValidationResult.withError("transaction nonce too low");
        }
        if (stateNonce.add(maxNumberOfTxsPerAddress).compareTo(nonce) < 0) {
            return TransactionValidationResult.withError("transaction nonce too high");
        }

        return TransactionValidationResult.ok();
    }
}

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
import co.rsk.core.TransactionUtils;
import co.rsk.core.SenderResolverVisitor;
import co.rsk.net.TransactionValidationResult;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * Checks if a minimum gas limit estimated based on the data is lower than
 * the gas limit of the transaction
 */
public class TxValidatorIntrinsicGasLimitValidator implements TxValidatorStep {

    private final Constants constants;
    private final ActivationConfig activationConfig;
    private final SenderResolverVisitor senderResolver;

    public TxValidatorIntrinsicGasLimitValidator(Constants constants, ActivationConfig activationConfig, SenderResolverVisitor senderResolver) {
        this.constants = constants;
        this.activationConfig = activationConfig;
        this.senderResolver = senderResolver;
    }

    @Override
    public TransactionValidationResult validate(Transaction tx, @Nullable AccountState state, BigInteger gasLimit, Coin minimumGasPrice, long bestBlockNumber, boolean isFreeTx) {
        long transactionCost = TransactionUtils.getTransactionCost(tx, tx.accept(senderResolver), constants, activationConfig.forBlock(bestBlockNumber));

        if (BigInteger.valueOf(transactionCost).compareTo(tx.getGasLimitAsInteger()) <= 0) {
            return TransactionValidationResult.ok();
        }

        return TransactionValidationResult.withError("transaction's basic cost is above the gas limit");
    }

}

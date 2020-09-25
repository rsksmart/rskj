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

package co.rsk.net.handler;

import co.rsk.core.Coin;
import co.rsk.net.TransactionValidationResult;
import co.rsk.net.handler.txvalidator.*;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.BigIntegers;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

/**
 * Validator for using in pending state.
 *
 * Add/remove checks here.
 */
public class TxPendingValidator {
    private static final Logger logger = LoggerFactory.getLogger("txpendingvalidator");

    private final List<TxValidatorStep> validatorSteps = new LinkedList<>();

    private final Constants constants;
    private final ActivationConfig activationConfig;

    public TxPendingValidator(Constants constants, ActivationConfig activationConfig, int accountSlots) {
        this.constants = constants;
        this.activationConfig = activationConfig;

        validatorSteps.add(new TxNotNullValidator());
        validatorSteps.add(new TxValidatorNotRemascTxValidator());
        validatorSteps.add(new TxValidatorGasLimitValidator());
        validatorSteps.add(new TxValidatorAccountStateValidator());
        validatorSteps.add(new TxValidatorNonceRangeValidator(accountSlots));
        validatorSteps.add(new TxValidatorAccountBalanceValidator());
        validatorSteps.add(new TxValidatorMinimuGasPriceValidator());
        validatorSteps.add(new TxValidatorIntrinsicGasLimitValidator(constants, activationConfig));
    }

    public TransactionValidationResult isValid(Transaction tx, Block executionBlock, @Nullable AccountState state) {
        BigInteger blockGasLimit = BigIntegers.fromUnsignedByteArray(executionBlock.getGasLimit());
        Coin minimumGasPrice = executionBlock.getMinimumGasPrice();
        long bestBlockNumber = executionBlock.getNumber();
        long basicTxCost = tx.transactionCost(constants, activationConfig.forBlock(bestBlockNumber));

        if (state == null && basicTxCost != 0) {
            logger.trace("[tx={}, sender={}] account doesn't exist", tx.getHash(), tx.getSender());
            return TransactionValidationResult.withError("the sender account doesn't exist");
        }

        for (TxValidatorStep step : validatorSteps) {
            TransactionValidationResult validationResult = step.validate(tx, state, blockGasLimit, minimumGasPrice, bestBlockNumber, basicTxCost == 0);
            if (!validationResult.transactionIsValid()) {
                logger.info("[tx={}] validation failed with error: {}", tx.getHash(), validationResult.getErrorMessage());
                return validationResult;
            }
        }

        return TransactionValidationResult.ok();
    }
}

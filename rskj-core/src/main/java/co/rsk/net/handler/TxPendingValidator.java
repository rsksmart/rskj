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
import co.rsk.core.bc.BlockUtils;
import co.rsk.net.TransactionValidationResult;
import co.rsk.net.handler.txvalidator.*;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

/**
 * Validator for using in pending state.
 * <p>
 * Add/remove checks here.
 */
public class TxPendingValidator {
    private static final Logger logger = LoggerFactory.getLogger("txpendingvalidator");

    private static final long TX_MAX_SIZE = 128L * 1024; // 128KB

    private final List<TxValidatorStep> validatorSteps = new LinkedList<>();

    private final Constants constants;
    private final ActivationConfig activationConfig;

    private final SignatureCache signatureCache;

    public TxPendingValidator(Constants constants, ActivationConfig activationConfig, int accountSlots, SignatureCache signatureCache) {
        this.constants = constants;
        this.activationConfig = activationConfig;
        this.signatureCache = signatureCache;

        validatorSteps.add(new TxNotNullValidator());
        validatorSteps.add(new TxValidatorNotRemascTxValidator());
        validatorSteps.add(new TxValidatorGasLimitValidator());
        validatorSteps.add(new TxValidatorAccountStateValidator());
        validatorSteps.add(new TxValidatorNonceRangeValidator(accountSlots));
        validatorSteps.add(new TxValidatorAccountBalanceValidator());
        validatorSteps.add(new TxValidatorMinimuGasPriceValidator());
        validatorSteps.add(new TxValidatorIntrinsicGasLimitValidator(constants, activationConfig, signatureCache));
        validatorSteps.add(new TxValidatorMaximumGasPriceValidator(activationConfig));
    }

    public TransactionValidationResult isValid(Transaction tx, Block executionBlock, @Nullable AccountState state) {
        long executionBlockNumber = executionBlock.getNumber();
        ActivationConfig.ForBlock activations = activationConfig.forBlock(executionBlockNumber);
        BigInteger gasLimit = activations.isActive(ConsensusRule.RSKIP144)
                ? BigInteger.valueOf(Math.max(BlockUtils.getSublistGasLimit(executionBlock, true, constants.getMinSequentialSetGasLimit()), BlockUtils.getSublistGasLimit(executionBlock, false, constants.getMinSequentialSetGasLimit())))
                : BigIntegers.fromUnsignedByteArray(executionBlock.getGasLimit());
        Coin minimumGasPrice = executionBlock.getMinimumGasPrice();
        long basicTxCost = tx.transactionCost(constants, activations, signatureCache);

        if (state == null && basicTxCost != 0) {
            if (logger.isTraceEnabled()) {
                logger.trace("[tx={}, sender={}] account doesn't exist", tx.getHash(), tx.getSender(signatureCache));
            }
            return TransactionValidationResult.withError("the sender account doesn't exist");
        }

        if (tx.getSize() > TX_MAX_SIZE) {
            return TransactionValidationResult.withError(String.format("transaction's size is higher than defined maximum: %s > %s", tx.getSize(), TX_MAX_SIZE));
        }

        for (TxValidatorStep step : validatorSteps) {
            TransactionValidationResult validationResult = step.validate(tx, state, gasLimit, minimumGasPrice, executionBlockNumber, basicTxCost == 0);
            if (!validationResult.transactionIsValid()) {
                logger.info("[tx={}] validation failed with error: {}", tx.getHash(), validationResult.getErrorMessage());
                return validationResult;
            }
        }

        return TransactionValidationResult.ok();
    }
}

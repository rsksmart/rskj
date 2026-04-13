/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.bc.BlockUtils;
import co.rsk.net.TransactionValidationResult;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TxPendingValidatorTest {

    private long minSequentialSetGasLimit;

    @BeforeEach
    public void setup() {
        minSequentialSetGasLimit = Constants.regtest().getMinSequentialSetGasLimit();
    }

    @Test
    void isValid_ShouldBeValid_WhenRSKIP144IsNotActivated() {
        // before RSKIP144: block gas limit == 10 and tx gas limit == 10 - should be valid

        Block executionBlock = mock(Block.class);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(10L).toByteArray());
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        Transaction tx = mock(Transaction.class);
        when(tx.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(10L));
        when(tx.getNonce()).thenReturn(BigInteger.valueOf(1L).toByteArray());
        when(tx.getNonceAsInteger()).thenReturn(BigInteger.valueOf(1L));
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1L));
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.legacy());

        AccountState state = mock(AccountState.class);
        when(state.getNonce()).thenReturn(BigInteger.valueOf(1L));

        TestSystemProperties config = new TestSystemProperties();

        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(ConsensusRule.RSKIP144)).thenReturn(false);

        ActivationConfig activationConfig = spy(config.getActivationConfig());
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TxPendingValidator validator = new TxPendingValidator(config.getNetworkConstants(), activationConfig, config.getNumOfAccountSlots(), signatureCache);

        TransactionValidationResult result = validator.isValid(tx, executionBlock, state);

        assertTrue(result.transactionIsValid());
    }

    @Test
    void isValid_ShouldBeInvalid_WhenRSKIP144IsNotActivated() {
        // before RSKIP144: block gas limit == 10 and tx gas limit == 11 - should be invalid

        Block executionBlock = mock(Block.class);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(10L).toByteArray());
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        Transaction tx = mock(Transaction.class);
        when(tx.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(11L));
        when(tx.getNonce()).thenReturn(BigInteger.valueOf(1L).toByteArray());
        when(tx.getNonceAsInteger()).thenReturn(BigInteger.valueOf(1L));
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1L));
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.legacy());

        AccountState state = mock(AccountState.class);
        when(state.getNonce()).thenReturn(BigInteger.valueOf(1L));

        TestSystemProperties config = new TestSystemProperties();

        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(ConsensusRule.RSKIP144)).thenReturn(false);

        ActivationConfig activationConfig = spy(config.getActivationConfig());
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TxPendingValidator validator = new TxPendingValidator(config.getNetworkConstants(), activationConfig, config.getNumOfAccountSlots(), signatureCache);

        TransactionValidationResult result = validator.isValid(tx, executionBlock, state);

        assertFalse(result.transactionIsValid());
    }

    @Test
    void isValid_ShouldBeValid_WhenRSKIP144IsAlreadyActivated() {
        // after RSKIP144: block gas limit == 10 and tx gas limit == BlockUtils.getSublistGasLimit(executionBlock) - should be valid

        Block executionBlock = mock(Block.class);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(10L).toByteArray());
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        long sublistGasLimit = BlockUtils.getSublistGasLimit(executionBlock, true, minSequentialSetGasLimit);

        Transaction tx = mock(Transaction.class);
        when(tx.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(sublistGasLimit));
        when(tx.getNonce()).thenReturn(BigInteger.valueOf(1L).toByteArray());
        when(tx.getNonceAsInteger()).thenReturn(BigInteger.valueOf(1L));
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1L));
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.legacy());

        AccountState state = mock(AccountState.class);
        when(state.getNonce()).thenReturn(BigInteger.valueOf(1L));

        TestSystemProperties config = new TestSystemProperties();

        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(ConsensusRule.RSKIP144)).thenReturn(true);

        ActivationConfig activationConfig = spy(config.getActivationConfig());
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TxPendingValidator validator = new TxPendingValidator(config.getNetworkConstants(), activationConfig, config.getNumOfAccountSlots(), signatureCache);

        TransactionValidationResult result = validator.isValid(tx, executionBlock, state);

        assertTrue(result.transactionIsValid());
    }

    @Test
    void isValid_ShouldBeInvalid_WhenRSKIP144IsAlreadyActivated() {
        // after RSKIP144: block gas limit == 10 and tx gas limit == BlockUtils.getSublistGasLimit(executionBlock) + 1 - should be invalid

        Block executionBlock = mock(Block.class);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(10L).toByteArray());
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        long sublistGasLimit = BlockUtils.getSublistGasLimit(executionBlock, true, minSequentialSetGasLimit);

        Transaction tx = mock(Transaction.class);
        when(tx.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(sublistGasLimit + 1L));
        when(tx.getNonce()).thenReturn(BigInteger.valueOf(1L).toByteArray());
        when(tx.getNonceAsInteger()).thenReturn(BigInteger.valueOf(1L));
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1L));
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.legacy());

        AccountState state = mock(AccountState.class);
        when(state.getNonce()).thenReturn(BigInteger.valueOf(1L));

        TestSystemProperties config = new TestSystemProperties();

        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(ConsensusRule.RSKIP144)).thenReturn(true);

        ActivationConfig activationConfig = spy(config.getActivationConfig());
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TxPendingValidator validator = new TxPendingValidator(config.getNetworkConstants(), activationConfig, config.getNumOfAccountSlots(), signatureCache);

        TransactionValidationResult result = validator.isValid(tx, executionBlock, state);

        assertFalse(result.transactionIsValid());
    }

    @Test
    void isValid_ShouldBeInvalid_WhenTypedTransactionAndRSKIP543IsNotActivated() {
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(10L).toByteArray());
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        Transaction tx = mock(Transaction.class);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_1));
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(true);

        TestSystemProperties config = new TestSystemProperties();
        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(ConsensusRule.RSKIP144)).thenReturn(false);
        when(forBlock.isActive(ConsensusRule.RSKIP543)).thenReturn(false);

        ActivationConfig activationConfig = spy(config.getActivationConfig());
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TxPendingValidator validator = new TxPendingValidator(
                config.getNetworkConstants(), activationConfig, config.getNumOfAccountSlots(), signatureCache);

        TransactionValidationResult result = validator.isValid(tx, executionBlock, null);

        assertFalse(result.transactionIsValid());
        assertTrue(result.getErrorMessage().contains("is not supported before its activation"));
    }

    @Test
    void isValid_ShouldBeInvalid_WhenType1TransactionAndRSKIP546IsNotActivated() {
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(100_000L).toByteArray());
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        Transaction tx = mock(Transaction.class);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_1));
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(true);

        TestSystemProperties config = new TestSystemProperties();
        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(ConsensusRule.RSKIP144)).thenReturn(false);
        when(forBlock.isActive(ConsensusRule.RSKIP543)).thenReturn(true);
        when(forBlock.isActive(ConsensusRule.RSKIP546)).thenReturn(false);

        ActivationConfig activationConfig = spy(config.getActivationConfig());
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TxPendingValidator validator = new TxPendingValidator(
                config.getNetworkConstants(), activationConfig, config.getNumOfAccountSlots(), signatureCache);

        TransactionValidationResult result = validator.isValid(tx, executionBlock, null);

        assertFalse(result.transactionIsValid());
        assertTrue(result.getErrorMessage().contains("is not supported before its activation"),
                "Rejection message should indicate the activation requirement, got: " + result.getErrorMessage());
    }

    @Test
    void isValid_ShouldBeInvalid_WhenType2TransactionAndRSKIP546IsNotActivated() {
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(100_000L).toByteArray());
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        Transaction tx = mock(Transaction.class);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_2));
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(true);

        TestSystemProperties config = new TestSystemProperties();
        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(ConsensusRule.RSKIP144)).thenReturn(false);
        when(forBlock.isActive(ConsensusRule.RSKIP543)).thenReturn(true);
        when(forBlock.isActive(ConsensusRule.RSKIP546)).thenReturn(false);

        ActivationConfig activationConfig = spy(config.getActivationConfig());
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TxPendingValidator validator = new TxPendingValidator(
                config.getNetworkConstants(), activationConfig, config.getNumOfAccountSlots(), signatureCache);

        TransactionValidationResult result = validator.isValid(tx, executionBlock, null);

        assertFalse(result.transactionIsValid());
        assertTrue(result.getErrorMessage().contains("is not supported before its activation"),
                "Type 2 rejection message should indicate activation requirement");
    }

    @Test
    void isValid_ShouldBeValid_WhenType2TransactionAndBothRSKIP543AndRSKIP546AreActivated() {
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(100_000L).toByteArray());
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        Transaction tx = mock(Transaction.class);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_2));
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(21_000L));
        when(tx.getNonce()).thenReturn(BigInteger.ONE.toByteArray());
        when(tx.getNonceAsInteger()).thenReturn(BigInteger.ONE);
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1L));
        when(tx.transactionCost(any(), any(), any())).thenReturn(21_000L);

        AccountState state = mock(AccountState.class);
        when(state.getNonce()).thenReturn(BigInteger.ONE);
        when(state.getBalance()).thenReturn(Coin.valueOf(1_000_000L));

        TestSystemProperties config = new TestSystemProperties();
        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(ConsensusRule.RSKIP144)).thenReturn(false);
        when(forBlock.isActive(ConsensusRule.RSKIP543)).thenReturn(true);
        when(forBlock.isActive(ConsensusRule.RSKIP546)).thenReturn(true);

        ActivationConfig activationConfig = spy(config.getActivationConfig());
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TxPendingValidator validator = new TxPendingValidator(
                config.getNetworkConstants(), activationConfig, config.getNumOfAccountSlots(), signatureCache);

        TransactionValidationResult result = validator.isValid(tx, executionBlock, state);

        assertTrue(result.transactionIsValid(),
                "Type 2 tx should be valid when RSKIP543 and RSKIP546 are both active");
    }

    @Test
    void isValid_ShouldBeInvalid_WhenType2GasLimitIsBelowAccessListIntrinsicCost() {
        // The access list intrinsic gas adds to the base 21000. A Type 2 tx with a gas limit
        // below its transactionCost() (which includes access list gas) must be rejected.
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);
        when(executionBlock.getGasLimit()).thenReturn(BigInteger.valueOf(1_000_000L).toByteArray());
        when(executionBlock.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        // Simulate a transaction whose intrinsic cost (21000 + access list gas) exceeds its gas limit
        long accessListIntrinsicGas = 5_000L;
        long txGasLimit = 21_000L;
        long totalIntrinsicCost = 21_000L + accessListIntrinsicGas;

        Transaction tx = mock(Transaction.class);
        when(tx.getTypePrefix()).thenReturn(TransactionTypePrefix.typed(TransactionType.TYPE_2));
        when(tx.isTypedTransactionNotAllowed(any())).thenReturn(false);
        when(tx.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(txGasLimit));
        when(tx.getNonce()).thenReturn(BigInteger.ONE.toByteArray());
        when(tx.getNonceAsInteger()).thenReturn(BigInteger.ONE);
        when(tx.getGasPrice()).thenReturn(Coin.valueOf(1L));
        when(tx.transactionCost(any(), any(), any())).thenReturn(totalIntrinsicCost);

        AccountState state = mock(AccountState.class);
        when(state.getNonce()).thenReturn(BigInteger.ONE);
        when(state.getBalance()).thenReturn(Coin.valueOf(1_000_000L));

        TestSystemProperties config = new TestSystemProperties();
        ActivationConfig.ForBlock forBlock = mock(ActivationConfig.ForBlock.class);
        when(forBlock.isActive(any())).thenReturn(true);

        ActivationConfig activationConfig = spy(config.getActivationConfig());
        when(activationConfig.forBlock(anyLong())).thenReturn(forBlock);

        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        TxPendingValidator validator = new TxPendingValidator(
                config.getNetworkConstants(), activationConfig, config.getNumOfAccountSlots(), signatureCache);

        TransactionValidationResult result = validator.isValid(tx, executionBlock, state);

        assertFalse(result.transactionIsValid(),
                "Type 2 tx with gas limit below intrinsic cost (including access list gas) must be invalid");
        assertTrue(result.getErrorMessage().contains("basic cost") || result.getErrorMessage().contains("intrinsic"),
                "Error message should mention basic/intrinsic cost, got: " + result.getErrorMessage());
    }
}

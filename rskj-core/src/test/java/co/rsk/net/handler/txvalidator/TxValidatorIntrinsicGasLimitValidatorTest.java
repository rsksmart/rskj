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

import  static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.BtcECKey;

import java.util.Arrays;
import java.util.List;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.AccountState;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

class TxValidatorIntrinsicGasLimitValidatorTest {
    private static final List<BtcECKey> REGTEST_FEDERATION_PRIVATE_KEYS = Arrays.asList(
        BtcECKey.fromPrivate(Hex.decode("45c5b07fc1a6f58892615b7c31dca6c96db58c4bbc538a6b8a22999aaa860c32")),
        BtcECKey.fromPrivate(Hex.decode("505334c7745df2fc61486dffb900784505776a898377172ffa77384892749179")),
        BtcECKey.fromPrivate(Hex.decode("bed0af2ce8aa8cb2bc3f9416c9d518fdee15d1ff15b8ded28376fcb23db6db69"))
    );

    private Constants constants;
    private ActivationConfig activationConfig;

    @BeforeEach
    void setUp() {
        constants = Constants.regtestWithFederation(REGTEST_FEDERATION_PRIVATE_KEYS);
        activationConfig = ActivationConfigsForTest.allBut(ConsensusRule.ARE_BRIDGE_TXS_PAID);
    }

    @Test
    void validIntrinsicGasPrice() {
        Transaction tx1 = Transaction
            .builder()
            .nonce(BigInteger.ZERO)
            .gasPrice(BigInteger.ZERO)
            .gasLimit(BigInteger.valueOf(21000))
            .destination(new ECKey().getAddress())
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(BigInteger.ZERO)
            .build();
        tx1.sign(new ECKey().getPrivKeyBytes());

        Transaction tx2 = Transaction
            .builder()
            .nonce(BigInteger.ZERO)
            .gasPrice(BigInteger.ZERO)
            .gasLimit(BigInteger.valueOf(30000))
            .destination(new ECKey().getAddress())
            .data(Hex.decode("0001"))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(BigInteger.ZERO)
            .build();
        tx2.sign(new ECKey().getPrivKeyBytes());

        Transaction tx3 = Transaction
            .builder()
            .nonce(BigInteger.ZERO)
            .gasPrice(BigInteger.ZERO)
            .gasLimit(BigInteger.valueOf(21072))
            .destination(new ECKey().getAddress())
            .data(Hex.decode("0001"))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(BigInteger.ZERO)
            .build();
        tx3.sign(new ECKey().getPrivKeyBytes());

        Transaction tx4 = Transaction
            .builder()
            .nonce(BigInteger.ZERO)
            .gasPrice(BigInteger.ZERO)
            .gasLimit(BigInteger.ZERO)
            .destination(PrecompiledContracts.BRIDGE_ADDR.getBytes())
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(BigInteger.ZERO)
            .build();
        tx4.sign(REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());

        TxValidatorIntrinsicGasLimitValidator tvigpv = new TxValidatorIntrinsicGasLimitValidator(
            constants,
            activationConfig,
            new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        );

        assertTrue(tvigpv.validate(tx1, new AccountState(), null, null, Long.MAX_VALUE, false).transactionIsValid());
        assertTrue(tvigpv.validate(tx2, new AccountState(), null, null, Long.MAX_VALUE, false).transactionIsValid());
        assertTrue(tvigpv.validate(tx3, new AccountState(), null, null, Long.MAX_VALUE, false).transactionIsValid());
        assertTrue(tvigpv.validate(tx4, new AccountState(), null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

    @Test
    void invalidIntrinsicGasPrice() {
        Transaction tx1 = Transaction
            .builder()
            .nonce(BigInteger.ZERO)
            .gasPrice(BigInteger.ZERO)
            .gasLimit(BigInteger.valueOf(21019))
            .destination(new ECKey().getAddress())
            .data(Hex.decode("0001"))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(BigInteger.ZERO)
            .build();
        tx1.sign(new ECKey().getPrivKeyBytes());

        Transaction tx2 = Transaction
            .builder()
            .nonce(BigInteger.ZERO)
            .gasPrice(BigInteger.ZERO)
            .gasLimit(BigInteger.valueOf(20999))
            .destination(new ECKey().getAddress())
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(BigInteger.ZERO)
            .build();
        tx2.sign(new ECKey().getPrivKeyBytes());

        Transaction tx3 = Transaction
            .builder()
            .nonce(BigInteger.ZERO)
            .gasPrice(BigInteger.ZERO)
            .gasLimit(BigInteger.ZERO)
            .destination(new ECKey().getAddress())
            .data(Hex.decode("0001"))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(BigInteger.ZERO)
            .build();
        tx3.sign(new ECKey().getPrivKeyBytes());

        Transaction tx4 = Transaction
            .builder()
            .nonce(BigInteger.ZERO)
            .gasPrice(BigInteger.ZERO)
            .gasLimit(BigInteger.ZERO)
            .destination(new ECKey().getAddress())
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(BigInteger.ZERO)
            .build();
        tx4.sign(new ECKey().getPrivKeyBytes());

        TxValidatorIntrinsicGasLimitValidator tvigpv = new TxValidatorIntrinsicGasLimitValidator(
            constants,
            activationConfig,
            new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        );

        assertFalse(tvigpv.validate(tx1, new AccountState(), null, null, Long.MAX_VALUE, false).transactionIsValid());
        assertFalse(tvigpv.validate(tx2, new AccountState(), null, null, Long.MAX_VALUE, false).transactionIsValid());
        assertFalse(tvigpv.validate(tx3, new AccountState(), null, null, Long.MAX_VALUE, false).transactionIsValid());
        assertFalse(tvigpv.validate(tx4, new AccountState(), null, null, Long.MAX_VALUE, false).transactionIsValid());
    }

}

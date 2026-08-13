/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.core.bc.transactionexecutor;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Collections;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.core.bc.BlockExecutorTest.createAccount;

class InternalDelegationExecutionTest {

    private static final long PRE_ACTIVATION_BLOCK = 2L;
    private static final long POST_ACTIVATION_BLOCK = 1L;

    @Test
    void internalCall_beforeRskip545Activation_doesNotResolveDelegation() {
        TestSystemProperties config = configWithRskip545ActivationAt(PRE_ACTIVATION_BLOCK);

        Repository track = createRepository().startTracking();

        Account sender = createAccount("internalCallBeforeRskip545Sender", track, Coin.valueOf(6_000_000));
        Account caller = createAccount("internalCallBeforeRskip545Caller", track, Coin.ZERO);
        Account target = createAccount("internalCallBeforeRskip545Target", track, Coin.ZERO);
        Account delegated = createAccount("internalCallBeforeRskip545Delegate", track, Coin.ZERO);

        /*
         * caller.code:
         *
         *     CALL target
         *     return CALL result (0/1)
         */
        track.setupContract(caller.getAddress());
        track.saveCode(caller.getAddress(), buildInternalCallCode(target.getAddress()));


        // Historical state containing: target.code = EF0100 || delegated
        track.setupContract(target.getAddress());
        track.saveCode(target.getAddress(), DelegationCodeResolver.createDelegatedCode(delegated.getAddress()));

        track.setupContract(delegated.getAddress());
        track.saveCode(delegated.getAddress(), new byte[] { 0x00 });

        track.commit();

        Block block = createBlock(config, track, createLegacyCall(config, sender, caller.getAddress(), track.getNonce(sender.getAddress())));

        Assertions.assertEquals(1L, block.getNumber());

        // Sanity check: RSKIP545 activates at block 2, while this block is block 1.
        Assertions.assertFalse(config.getActivationConfig().isActive(ConsensusRule.RSKIP545, block.getNumber()));

        TransactionExecutorFactory factory = getTransactionExecutorFactory(config);
        Transaction tx = block.getTransactionsList().get(0);

        var executor = factory.newInstance(tx, 0, block.getCoinbase(), track, block, 0L);

        Assertions.assertTrue(executor.executeTransaction());

        /*
         * Parent execution itself succeeds.
         *
         * The internal CALL fails because EF is executed with legacy
         * semantics, but CALL converts that child failure into result = 0.
         */
        Assertions.assertNull(executor.getResult().getException());
        Assertions.assertFalse(executor.getResult().isRevert());

        byte[] expected = new byte[32];

        Assertions.assertArrayEquals(expected, executor.getResult().getHReturn());
    }

    @Test
    void internalCall_afterRskip545Activation_resolvesDelegation() {
        TestSystemProperties config = configWithRskip545ActivationAt(POST_ACTIVATION_BLOCK);

        Repository track = createRepository().startTracking();

        Account sender = createAccount("internalCallAfterRskip545Sender", track, Coin.valueOf(6_000_000));
        Account caller = createAccount("internalCallAfterRskip545Caller", track, Coin.ZERO);
        Account target = createAccount("internalCallAfterRskip545Target", track, Coin.ZERO);
        Account delegated = createAccount("internalCallAfterRskip545Delegate", track, Coin.ZERO);

        track.setupContract(caller.getAddress());
        track.saveCode(caller.getAddress(), buildInternalCallCode(target.getAddress()));

        track.setupContract(target.getAddress());
        track.saveCode(target.getAddress(), DelegationCodeResolver.createDelegatedCode(delegated.getAddress()));


        // Delegated runtime: STOP
        track.setupContract(delegated.getAddress());
        track.saveCode(delegated.getAddress(), new byte[] { 0x00 });
        track.commit();

        Block block = createBlock(config, track, createLegacyCall(config, sender, caller.getAddress(), track.getNonce(sender.getAddress())));
        Assertions.assertEquals(1L, block.getNumber());

        Assertions.assertTrue(config.getActivationConfig().isActive(org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP545, block.getNumber()));

        TransactionExecutorFactory factory = getTransactionExecutorFactory(config);
        Transaction tx = block.getTransactionsList().get(0);

        var executor = factory.newInstance(tx, 0, block.getCoinbase(), track, block, 0L);

        Assertions.assertTrue(executor.executeTransaction());
        Assertions.assertNull(executor.getResult().getException());
        Assertions.assertFalse(executor.getResult().isRevert());


        // CALL success is represented by 1. MSTORE serializes that value as a 32-byte word:
        byte[] expected = new byte[32];
        expected[31] = 0x01;

        Assertions.assertArrayEquals(expected, executor.getResult().getHReturn());
    }

    private static TestSystemProperties configWithRskip545ActivationAt(long activationBlock) {
        return new TestSystemProperties(
                rawConfig -> rawConfig.withValue(
                        "blockchain.config.consensusRules.rskip545",
                        ConfigValueFactory.fromAnyRef(activationBlock)
                )
        );
    }

    private static Transaction createLegacyCall(TestSystemProperties config, Account sender, RskAddress receiver, BigInteger nonce) {
        Transaction tx = Transaction.builder()
                .nonce(nonce)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(2_000_000))
                .receiveAddress(receiver)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .build();

        tx.sign(sender.getEcKey().getPrivKeyBytes());

        return tx;
    }

    private static Block createBlock(TestSystemProperties config, Repository track, Transaction tx) {
        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), config.getActivationConfig());

        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(track.getRoot());

        return blockGenerator.createChildBlock(genesis, Collections.singletonList(tx), Collections.emptyList(), 1, null);
    }

    private static TransactionExecutorFactory getTransactionExecutorFactory(TestSystemProperties config) {
        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        var btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams());

        var bridgeSupportFactory = new BridgeSupportFactory(btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), signatureCache);

        return new TransactionExecutorFactory(
                config,
                new BlockStoreDummy(),
                null,
                new BlockFactory(config.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(
                        config,
                        bridgeSupportFactory,
                        signatureCache
                ),
                signatureCache
        );
    }

    /**
     * Builds runtime code equivalent to:
     *
     *     bool success = target.call("");
     *     return success ? 1 : 0;
     *
     * EVM:
     *
     *     PUSH1  00       // outSize
     *     PUSH1  00       // outOffset
     *     PUSH1  00       // inSize
     *     PUSH1  00       // inOffset
     *     PUSH1  00       // value
     *     PUSH20 target
     *     PUSH3  0fffff   // gas
     *     CALL
     *
     *     PUSH1  00
     *     MSTORE
     *
     *     PUSH1  20       // size
     *     PUSH1  00       // offset
     *     RETURN
     */
    private static byte[] buildInternalCallCode(RskAddress target) {
        byte[] address = target.getBytes();

        byte[] code = new byte[
                2 +     // PUSH1 outSize
                        2 +     // PUSH1 outOffset
                        2 +     // PUSH1 inSize
                        2 +     // PUSH1 inOffset
                        2 +     // PUSH1 value
                        21 +    // PUSH20 target
                        4 +     // PUSH3 gas
                        1 +     // CALL
                        2 +     // PUSH1 0
                        1 +     // MSTORE
                        2 +     // PUSH1 32
                        2 +     // PUSH1 0
                        1       // RETURN
                ];

        int i = 0;

        // outSize = 0
        code[i++] = 0x60; // PUSH1
        code[i++] = 0x00;

        // outOffset = 0
        code[i++] = 0x60;
        code[i++] = 0x00;

        // inSize = 0
        code[i++] = 0x60;
        code[i++] = 0x00;

        // inOffset = 0
        code[i++] = 0x60;
        code[i++] = 0x00;

        // value = 0
        code[i++] = 0x60;
        code[i++] = 0x00;

        // target
        code[i++] = 0x73; // PUSH20

        System.arraycopy(
                address,
                0,
                code,
                i,
                address.length
        );

        i += address.length;

        /*
         * gas = 0x0fffff
         */
        code[i++] = 0x62; // PUSH3
        code[i++] = 0x0f;
        code[i++] = (byte) 0xff;
        code[i++] = (byte) 0xff;

        code[i++] = (byte) 0xf1; // CALL

        /*
         * Stack now contains CALL result:
         *
         * 0 = child execution failed
         * 1 = child execution succeeded
         */

        // MSTORE(0, callResult)
        code[i++] = 0x60; // PUSH1
        code[i++] = 0x00;
        code[i++] = 0x52; // MSTORE

        // RETURN(0, 32)
        code[i++] = 0x60; // PUSH1
        code[i++] = 0x20;

        code[i++] = 0x60; // PUSH1
        code[i++] = 0x00;

        code[i] = (byte) 0xf3; // RETURN

        return code;
    }
}

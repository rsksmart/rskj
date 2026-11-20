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
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.math.BigInteger;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DelegatedEoaTestSupport {

    static final long GAS_LIMIT = 2_000_000;

    private DelegatedEoaTestSupport() {
    }

    static TestSystemProperties rskip545Active() {
        return new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0)));
    }

    static void saveCode(Repository track, Account account, byte[] code) {
        track.initializeStorage(account.getAddress());
        track.saveCode(account.getAddress(), code);
    }


    static void executeLegacyCall(TestSystemProperties config, Repository track, Account sender, Account receiver) {
        TransactionExecutor executor = execute(config, track, legacyCall(config, sender, receiver, Coin.ZERO));

        assertNull(executor.getResult().getException(), "the tx itself must not fail, only what it calls");
        assertFalse(executor.getResult().isRevert());
    }


    static TransactionExecutor execute(TestSystemProperties config, Repository track, Transaction tx) {
        Block block = createBlock(config, track, tx);
        assertTrue(config.getActivationConfig().isActive(ConsensusRule.RSKIP545, block.getNumber()));

        TransactionExecutor executor = executorFactory(config).newInstance(block.getTransactionsList().get(0), 0, block.getCoinbase(), track, block, 0L);
        assertTrue(executor.executeTransaction(), "the tx must be valid");
        return executor;
    }

    static Transaction legacyCall(TestSystemProperties config, Account sender, Account receiver, Coin value) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(GAS_LIMIT))
                .receiveAddress(receiver.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
                .value(value)
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

    private static TransactionExecutorFactory executorFactory(TestSystemProperties config) {
        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        var btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams());
        var bridgeSupportFactory = new BridgeSupportFactory(btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), signatureCache);
        return new TransactionExecutorFactory(
                config,
                new BlockStoreDummy(),
                null,
                new BlockFactory(config.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory, signatureCache),
                signatureCache
        );
    }
}

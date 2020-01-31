/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.jsontestsuite.runners;

import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.TrieConverter;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.jsontestsuite.Env;
import org.ethereum.jsontestsuite.StateTestCase;
import org.ethereum.jsontestsuite.TestProgramInvokeFactory;
import org.ethereum.jsontestsuite.builder.EnvBuilder;
import org.ethereum.jsontestsuite.builder.LogBuilder;
import org.ethereum.jsontestsuite.builder.RepositoryBuilder;
import org.ethereum.jsontestsuite.builder.TransactionBuilder;
import org.ethereum.jsontestsuite.validators.LogsValidator;
import org.ethereum.jsontestsuite.validators.OutputValidator;
import org.ethereum.jsontestsuite.validators.RepositoryValidator;
import org.ethereum.jsontestsuite.validators.ValidationStats;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.byteArrayToLong;

public class StateTestRunner {
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    private static Logger logger = LoggerFactory.getLogger("TCK-Test");
    private final TestSystemProperties config = new TestSystemProperties();
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
    private final RepositoryBtcBlockStoreWithCache.Factory blockStoreWithCache = new RepositoryBtcBlockStoreWithCache.Factory(
            config.getNetworkConstants().bridgeConstants.getBtcParams()
    );

    private final BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
           blockStoreWithCache, BridgeRegTestConstants.getInstance(), config.getActivationConfig());

    public static List<String> run(StateTestCase stateTestCase2) {
        return new StateTestRunner(stateTestCase2).runImpl();
    }

    protected StateTestCase stateTestCase;
    private TrieStoreImpl trieStore;
    protected Repository repository;
    protected Transaction transaction;
    protected BlockChainImpl blockchain;
    protected Env env;
    protected ProgramInvokeFactory invokeFactory;
    protected Block block;
    protected ValidationStats vStats;
    protected PrecompiledContracts precompiledContracts;

    public StateTestRunner(StateTestCase stateTestCase) {
        this.stateTestCase = stateTestCase;
        setstateTestUSeREMASC(false);
        precompiledContracts = new PrecompiledContracts(config, bridgeSupportFactory);
    }

    public StateTestRunner setstateTestUSeREMASC(boolean v) {
        config.setRemascEnabled(v);
        return this;
    }

    protected ProgramResult executeTransaction(Transaction tx) {
        Repository track = repository.startTracking();

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                new BlockStoreDummy(),
                null,
                blockFactory,
                invokeFactory,
                precompiledContracts,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        TransactionExecutor executor = transactionExecutorFactory
                .newInstance(transaction, 0, new RskAddress(env.getCurrentCoinbase()), track, blockchain.getBestBlock(), 0);

        try{
            executor.executeTransaction();
        } catch (StackOverflowError soe){
            logger.error(" !!! StackOverflowError: update your java run command with -Xss32M !!!");
            System.exit(-1);
        }

        track.commit();
        return executor.getResult();
    }

    public List<String> runImpl() {
        vStats = new ValidationStats();
        logger.info("");
        trieStore = new TrieStoreImpl(new HashMapDB());
        repository = RepositoryBuilder.build(trieStore, stateTestCase.getPre());
        logger.info("loaded repository");

        transaction = TransactionBuilder.build(stateTestCase.getTransaction());
        logger.info("transaction: {}", transaction.toString());
        BlockStore blockStore = new IndexedBlockStore(blockFactory, new HashMapDB(), new HashMapBlocksIndex());
        StateRootHandler stateRootHandler = new StateRootHandler(config.getActivationConfig(), new TrieConverter(), new HashMapDB(), new HashMap<>());
        blockchain = new BlockChainImpl(
                blockStore,
                null,
                null,
                null,
                null,
                new BlockExecutor(
                        config.getActivationConfig(),
                        new RepositoryLocator(trieStore, stateRootHandler),
                        stateRootHandler,
                        new TransactionExecutorFactory(
                                config,
                                blockStore,
                                null,
                                blockFactory,
                                new ProgramInvokeFactoryImpl(),
                                precompiledContracts,
                                new BlockTxSignatureCache(new ReceivedTxSignatureCache())
                        )
                ),
                stateRootHandler
        );

        env = EnvBuilder.build(stateTestCase.getEnv());
        invokeFactory = new TestProgramInvokeFactory(env);

        block = build(env);
        block.setStateRoot(repository.getRoot());
        block.flushRLP();

        blockchain.setStatus(block, block.getCumulativeDifficulty());
        //blockchain.setProgramInvokeFactory(invokeFactory);
        //blockchain.startTracking();

        ProgramResult programResult = executeTransaction(transaction);

        trieStore.flush();

        List<LogInfo> origLogs = programResult.getLogInfoList();
        List<LogInfo> postLogs = LogBuilder.build(stateTestCase.getLogs());

        List<String> logsResult = LogsValidator.valid(origLogs, postLogs,vStats);

        Repository postRepository = RepositoryBuilder.build(stateTestCase.getPost());

        // Balances cannot be validated because has consumption for CALLs differ.
        List<String> repoResults = RepositoryValidator.valid(repository, postRepository,  false ,false,vStats);

        logger.info("--------- POST Validation---------");
        List<String> outputResults =
                OutputValidator.valid(Hex.toHexString(programResult.getHReturn()), stateTestCase.getOut(),vStats);

        List<String> results = new ArrayList<>();
        results.addAll(repoResults);
        results.addAll(logsResult);
        results.addAll(outputResults);

        for (String result : results) {
            logger.error(result);
        }

        if ((vStats.storageChecks==0) && (vStats.logChecks==0) &&
                (vStats.balancetChecks==0) && (vStats.outputChecks==0) &&
                (vStats.blockChecks==0)) {
            // This generally mean that the test didn't check anything
            // AccountChecks are considered not indicative of the result of the test
            logger.info("IRRELEVANT\n");
        }
        logger.info("\n\n");
        return results;
    }

    public static final byte[] ZERO32_BYTE_ARRAY = new byte[32];
    public Block build(Env env) {
        return blockFactory.newBlock(
                blockFactory.newHeader(
                        // Don't use the empty parent hash because it's used to log and
                        // when log entries are printed with empty parent hash it throws
                        // an exception.
                        ZERO32_BYTE_ARRAY , ByteUtil.EMPTY_BYTE_ARRAY, env.getCurrentCoinbase(),
                        EMPTY_TRIE_HASH, EMPTY_TRIE_HASH, EMPTY_TRIE_HASH,
                        ByteUtil.EMPTY_BYTE_ARRAY, env.getCurrentDifficulty(), byteArrayToLong(env.getCurrentNumber()),
                        env.getCurrentGasLimit(), 0L, byteArrayToLong(env.getCurrentTimestamp()),
                        new byte[32], Coin.ZERO, ZERO_BYTE_ARRAY, ZERO_BYTE_ARRAY, ZERO_BYTE_ARRAY, ZERO_BYTE_ARRAY,null, 0
                ),
                Collections.emptyList(),
                Collections.emptyList(),
                false
        );
    }
}

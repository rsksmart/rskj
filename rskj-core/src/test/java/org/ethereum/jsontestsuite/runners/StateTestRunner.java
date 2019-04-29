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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.StateRootHandler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.jsontestsuite.Env;
import org.ethereum.jsontestsuite.StateTestCase;
import org.ethereum.jsontestsuite.TestProgramInvokeFactory;
import org.ethereum.jsontestsuite.builder.*;
import org.ethereum.jsontestsuite.validators.LogsValidator;
import org.ethereum.jsontestsuite.validators.OutputValidator;
import org.ethereum.jsontestsuite.validators.RepositoryValidator;
import org.ethereum.listener.EthereumListenerAdapter;
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
    private final BlockFactory blockFactory = new BlockFactory(config.getBlockchainConfig());

    public static List<String> run(StateTestCase stateTestCase2) {
        return new StateTestRunner(stateTestCase2).runImpl();
    }

    protected StateTestCase stateTestCase;
    protected Repository repository;
    protected Transaction transaction;
    protected BlockChainImpl blockchain;
    protected Env env;
    protected ProgramInvokeFactory invokeFactory;
    protected Block block;

    // Enable when State tests are changed to support paying
    // the fees into REMASC instead that into the coinbase account
    boolean stateTestUseREMASC = false;

    public StateTestRunner(StateTestCase stateTestCase) {
        this.stateTestCase = stateTestCase;
    }

    public StateTestRunner setstateTestUSeREMASC(boolean v) {
        stateTestUseREMASC = v;
        return this;
    }

    protected ProgramResult executeTransaction(Transaction tx) {
        Repository track = repository.startTracking();

        TransactionExecutor executor = new TransactionExecutor(
                transaction,
                0,
                new RskAddress(env.getCurrentCoinbase()),
                track,
                new BlockStoreDummy(),
                null,
                blockFactory,
                invokeFactory,
                blockchain.getBestBlock(),
                new EthereumListenerAdapter(),
                0,
                config.getVmConfig(),
                config.getBlockchainConfig(),
                config.playVM(),
                config.isRemascEnabled() && stateTestUseREMASC,
                config.vmTrace(),
                new PrecompiledContracts(config),
                config.databaseDir(),
                config.vmTraceDir(),
                config.vmTraceCompressed()
        );
        try{
            executor.init();
            executor.execute();
            executor.go();
            executor.finalization();
        } catch (StackOverflowError soe){
            logger.error(" !!! StackOverflowError: update your java run command with -Xss32M !!!");
            System.exit(-1);
        }

        track.commit();
        return executor.getResult();
    }

    public List<String> runImpl() {

        logger.info("");
        repository = RepositoryBuilder.build(stateTestCase.getPre());
        logger.info("loaded repository");

        transaction = TransactionBuilder.build(stateTestCase.getTransaction());
        logger.info("transaction: {}", transaction.toString());
        BlockStore blockStore = new IndexedBlockStore(blockFactory, new HashMap<>(), new HashMapDB(), null);

        final ProgramInvokeFactoryImpl programInvokeFactory = new ProgramInvokeFactoryImpl();
        StateRootHandler stateRootHandler = new StateRootHandler(config, new HashMapDB(), new HashMap<>());
        blockchain = new BlockChainImpl(repository, blockStore, null, null, null, null, false, 1, new BlockExecutor(repository, (tx1, txindex1, coinbase, track1, block1, totalGasUsed1) -> new TransactionExecutor(
                tx1,
                txindex1,
                block1.getCoinbase(),
                track1,
                blockStore,
                null,
                blockFactory,
                programInvokeFactory,
                block1,
                null,
                totalGasUsed1,
                config.getVmConfig(),
                config.getBlockchainConfig(),
                config.playVM(),
                config.isRemascEnabled(),
                config.vmTrace(),
                new PrecompiledContracts(config),
                config.databaseDir(),
                config.vmTraceDir(),
                config.vmTraceCompressed()
        ), stateRootHandler), stateRootHandler);

        env = EnvBuilder.build(stateTestCase.getEnv());
        invokeFactory = new TestProgramInvokeFactory(env);

        block = build(env);
        block.setStateRoot(repository.getRoot());
        block.flushRLP();

        blockchain.setStatus(block, block.getCumulativeDifficulty());
        //blockchain.setProgramInvokeFactory(invokeFactory);
        //blockchain.startTracking();

        ProgramResult programResult = executeTransaction(transaction);

        repository.flushNoReconnect();

        List<LogInfo> origLogs = programResult.getLogInfoList();
        List<LogInfo> postLogs = LogBuilder.build(stateTestCase.getLogs());

        List<String> logsResult = LogsValidator.valid(origLogs, postLogs);

        Repository postRepository = RepositoryBuilder.build(stateTestCase.getPost());
        List<String> repoResults = RepositoryValidator.valid(repository, postRepository, false /*!blockchain.byTest*/);

        logger.info("--------- POST Validation---------");
        List<String> outputResults =
                OutputValidator.valid(Hex.toHexString(programResult.getHReturn()), stateTestCase.getOut());

        List<String> results = new ArrayList<>();
        results.addAll(repoResults);
        results.addAll(logsResult);
        results.addAll(outputResults);

        for (String result : results) {
            logger.error(result);
        }

        logger.info("\n\n");
        return results;
    }

    public Block build(Env env) {
        return new Block(
                blockFactory.newHeader(
                        ByteUtil.EMPTY_BYTE_ARRAY, ByteUtil.EMPTY_BYTE_ARRAY, env.getCurrentCoinbase(),
                        EMPTY_TRIE_HASH, EMPTY_TRIE_HASH, EMPTY_TRIE_HASH,
                        ByteUtil.EMPTY_BYTE_ARRAY, env.getCurrentDifficulty(), byteArrayToLong(env.getCurrentNumber()),
                        env.getCurrentGasLimit(), 0L, byteArrayToLong(env.getCurrentTimestamp()),
                        new byte[32], Coin.ZERO, ZERO_BYTE_ARRAY, ZERO_BYTE_ARRAY, ZERO_BYTE_ARRAY, null, 0
                ),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }
}

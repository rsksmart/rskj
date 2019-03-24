/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk;

import co.rsk.cli.CliArgs;
import co.rsk.config.NodeCliFlags;
import co.rsk.config.NodeCliOptions;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.RskFactory;
import co.rsk.core.bc.BlockValidatorImpl;
import co.rsk.db.StateRootHandler;
import co.rsk.validators.BlockParentDependantValidationRule;
import co.rsk.validators.BlockValidationRule;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.core.Repository;
import org.ethereum.core.TransactionPool;
import org.ethereum.core.genesis.BlockChainLoader;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

/**
 * Creates the initial object graph without a DI framework.
 */
public class RskContext {
    private final RskFactory factory;
    private final CliArgs<NodeCliOptions, NodeCliFlags> cliArgs;

    private RskSystemProperties rskSystemProperties;
    private Blockchain blockchain;
    private BlockChainLoader blockChainLoader;
    private BlockStore blockStore;
    private Repository repository;
    private Genesis genesis;
    private CompositeEthereumListener compositeEthereumListener;
    private DifficultyCalculator difficultyCalculator;
    private ProofOfWorkRule proofOfWorkRule;
    private BlockParentDependantValidationRule blockParentDependantValidationRule;
    private BlockValidationRule blockValidationRule;
    private BlockValidatorImpl blockValidator;
    private ReceiptStore receiptStore;
    private ProgramInvokeFactoryImpl programInvokeFactory;
    private TransactionPool transactionPool;
    private StateRootHandler stateRootHandler;

    public RskContext(String[] args) {
        this(new CliArgs.Parser<>(
                NodeCliOptions.class,
                NodeCliFlags.class
        ).parse(args));
    }

    private RskContext(CliArgs<NodeCliOptions, NodeCliFlags> cliArgs) {
        this.factory = new RskFactory();
        this.cliArgs = cliArgs;
    }

    public Blockchain getBlockchain() {
        if (blockchain == null) {
            blockchain = factory.getBlockchain(getBlockChainLoader());
        }

        return blockchain;
    }

    private BlockChainLoader getBlockChainLoader() {
        if (blockChainLoader == null) {
            blockChainLoader = new BlockChainLoader(
                    getRskSystemProperties(),
                    getRepository(),
                    getBlockStore(),
                    getReceiptStore(),
                    getTransactionPool(),
                    getCompositeEthereumListener(),
                    getBlockValidator(),
                    getGenesis(),
                    getStateRootHandler()
            );
        }

        return blockChainLoader;
    }

    public StateRootHandler getStateRootHandler() {
        if (stateRootHandler == null) {
            stateRootHandler = factory.getStateRootHandler(getRskSystemProperties());
        }

        return stateRootHandler;
    }

    public TransactionPool getTransactionPool() {
        if (transactionPool == null) {
            transactionPool = factory.getTransactionPool(
                    getBlockStore(),
                    getReceiptStore(),
                    getRepository(),
                    getRskSystemProperties(),
                    getProgramInvokeFactory(),
                    getCompositeEthereumListener()
            );
        }

        return transactionPool;
    }

    public ProgramInvokeFactoryImpl getProgramInvokeFactory() {
        if (programInvokeFactory == null) {
            programInvokeFactory = new ProgramInvokeFactoryImpl();
        }

        return programInvokeFactory;
    }

    public ReceiptStore getReceiptStore() {
        if (receiptStore == null) {
            receiptStore = factory.receiptStore(getRskSystemProperties());
        }

        return receiptStore;
    }

    public BlockValidator getBlockValidator() {
        if (blockValidator == null) {
            blockValidator = new BlockValidatorImpl(
                    getBlockStore(),
                    getBlockParentDependantValidationRule(),
                    getBlockValidationRule()
            );
        }

        return blockValidator;
    }

    private BlockValidationRule getBlockValidationRule() {
        if (blockValidationRule == null) {
            blockValidationRule = factory.blockValidationRule(
                    getBlockStore(),
                    getRskSystemProperties(),
                    getDifficultyCalculator(),
                    getProofOfWorkRule()
            );
        }

        return blockValidationRule;
    }

    private BlockParentDependantValidationRule getBlockParentDependantValidationRule() {
        if (blockParentDependantValidationRule == null) {
            blockParentDependantValidationRule = factory.blockParentDependantValidationRule(
                    getRepository(),
                    getRskSystemProperties(),
                    getDifficultyCalculator(),
                    getStateRootHandler()
            );
        }

        return blockParentDependantValidationRule;
    }

    private ProofOfWorkRule getProofOfWorkRule() {
        if (proofOfWorkRule == null) {
            proofOfWorkRule = new ProofOfWorkRule(getRskSystemProperties());
        }

        return proofOfWorkRule;
    }

    private DifficultyCalculator getDifficultyCalculator() {
        if (difficultyCalculator == null) {
            difficultyCalculator = new DifficultyCalculator(getRskSystemProperties());
        }

        return difficultyCalculator;
    }

    public CompositeEthereumListener getCompositeEthereumListener() {
        if (compositeEthereumListener == null) {
            compositeEthereumListener = factory.getCompositeEthereumListener();
        }

        return compositeEthereumListener;
    }

    public Genesis getGenesis() {
        if (genesis == null) {
            genesis = factory.getGenesis(getRskSystemProperties());
        }

        return genesis;
    }

    public Repository getRepository() {
        if (repository == null) {
            repository = factory.repository(getRskSystemProperties());
        }

        return repository;
    }

    public BlockStore getBlockStore() {
        if (blockStore == null) {
            blockStore = factory.blockStore(getRskSystemProperties());
        }

        return blockStore;
    }

    public RskSystemProperties getRskSystemProperties() {
        if (rskSystemProperties == null) {
            rskSystemProperties = factory.rskSystemProperties(cliArgs);
        }

        return rskSystemProperties;
    }
}

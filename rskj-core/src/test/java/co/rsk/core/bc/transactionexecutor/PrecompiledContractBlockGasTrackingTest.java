package co.rsk.core.bc.transactionexecutor;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;

import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Verifies block gas tracking when a precompiled contract call fails.
 * Before RSKIP-560, underreported gas lets the next transaction execute.
 * After activation, full gas consumption prevents the next transaction from fitting.
 */
class PrecompiledContractBlockGasTrackingTest {

    @ParameterizedTest(name = "RSKIP-560 active={0}, expected executed transactions={1}")
    @CsvSource({"false, 2", "true,  1"})
    void failedPrecompileGasAffectsWhetherNextTransactionFitsInBlock(boolean rskip560Active, int expectedExecutedTransactions) {
        TestSystemProperties baseConfig = new TestSystemProperties();

        ActivationConfig activationConfig = buildActivationConfig(baseConfig.getActivationConfig(), rskip560Active);

        TestSystemProperties config = spy(baseConfig);
        doReturn(activationConfig).when(config).getActivationConfig();

        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
        Repository track = repository.startTracking();

        Account sender = createAccount("acctest1", track, Coin.valueOf(10_000_000L));
        Account receiver = createAccount("acctest2", track, Coin.ZERO);
        track.commit();

        long blockGasLimit = 250_000L;
        long firstTransactionGasLimit = 200_000L;
        long secondTransactionGasLimit = 100_000L;

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), activationConfig);
        Block genesis = blockGenerator.getGenesisBlock(blockGasLimit);
        genesis.setStateRoot(repository.getRoot());

        byte[] invalidBridgeCall = Hex.decode("e674f5e8" + "0000000000000000000000000000000000000000000000000000000001000006");

        Transaction tx1 = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(firstTransactionGasLimit))
                .receiveAddress(PrecompiledContracts.BRIDGE_ADDR)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(invalidBridgeCall)
                .build();

        tx1.sign(sender.getEcKey().getPrivKeyBytes());

        Transaction tx2 = Transaction.builder()
                .nonce(BigInteger.ONE)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(secondTransactionGasLimit))
                .receiveAddress(receiver.getAddress())
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.valueOf(1L))
                .build();

        tx2.sign(sender.getEcKey().getPrivKeyBytes());

        List<Transaction> transactions = Arrays.asList(tx1, tx2);

        Block block = blockGenerator.createChildBlock(genesis, transactions, new ArrayList<>(), 1, null);

        assertEquals(blockGasLimit, new BigInteger(1, block.getGasLimit()).longValueExact());

        BlockExecutor blockExecutor = buildBlockExecutor(config, trieStore);
        BlockResult result = blockExecutor.executeAndFill(block, genesis.getHeader());

        assertEquals(expectedExecutedTransactions, result.getExecutedTransactions().size());

        if (rskip560Active) {
            assertEquals(firstTransactionGasLimit, result.getGasUsed());
        } else {
            assertTrue(result.getGasUsed() < blockGasLimit, "pre-activation gas underreporting should make tx2 appear to fit"
            );
        }
    }

    private static ActivationConfig buildActivationConfig(
            ActivationConfig defaults,
            boolean rskip560Active
    ) {
        Map<ConsensusRule, Long> heights =
                new EnumMap<>(ConsensusRule.class);

        for (ConsensusRule rule : ConsensusRule.values()) {
            heights.put(
                    rule,
                    defaults.isActive(rule, 0L) ? 0L : -1L
            );
        }

        // Keep execution sequential for this test.
        heights.put(ConsensusRule.RSKIP144, -1L);

        // Explicitly select the behavior under test.
        heights.put(
                ConsensusRule.RSKIP560,
                rskip560Active ? 0L : -1L
        );

        return new ActivationConfig(heights, new HashMap<>());
    }

    private static BlockExecutor buildBlockExecutor(TestSystemProperties config, TrieStore trieStore) {
        StateRootHandler stateRootHandler = new StateRootHandler(config.getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));

        RepositoryLocator repositoryLocator = new RepositoryLocator(trieStore, stateRootHandler);

        BlockTxSignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams());

        BridgeSupportFactory bridgeSupportFactory =
                new BridgeSupportFactory(
                        btcBlockStoreFactory,
                        config.getNetworkConstants().getBridgeConstants(),
                        config.getActivationConfig(),
                        signatureCache
                );

        TransactionExecutorFactory transactionExecutorFactory =
                new TransactionExecutorFactory(
                        config,
                        null,
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

        return new BlockExecutor(
                repositoryLocator,
                transactionExecutorFactory,
                config
        );
    }
}

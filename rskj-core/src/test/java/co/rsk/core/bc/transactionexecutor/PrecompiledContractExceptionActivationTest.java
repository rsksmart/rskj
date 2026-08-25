package co.rsk.core.bc.transactionexecutor;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class PrecompiledContractExceptionActivationTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bridgeCallThatThrowsBehavesPerActivation(boolean rskip560Active) {
        TestSystemProperties baseConfig = new TestSystemProperties();
        ActivationConfig activationConfig = withRskip560(baseConfig.getActivationConfig(), rskip560Active);

        TestSystemProperties config = spy(baseConfig);
        doReturn(activationConfig).when(config).getActivationConfig();

        BlockTxSignatureCache blockTxSignatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(btcBlockStoreFactory, config.getNetworkConstants().getBridgeConstants(), config.getActivationConfig(), blockTxSignatureCache);

        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                new BlockStoreDummy(),
                null,
                new BlockFactory(config.getActivationConfig()),
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory, blockTxSignatureCache),
                blockTxSignatureCache
        );

        Repository track = createRepository().startTracking();
        Account sender = createAccount("acctest1", track, Coin.valueOf(6_000_000L));
        track.commit();

        byte[] dataThatThrowsAnException = Hex.decode("e674f5e80000000000000000000000000000000000000000000000000000000001000006");

        long gasPrice = 1L;
        BigInteger gasLimit = BigInteger.valueOf(200_000L);

        Transaction tx = Transaction.builder()
                .nonce(track.getNonce(sender.getAddress()))
                .gasPrice(BigInteger.valueOf(gasPrice))
                .gasLimit(gasLimit)
                .receiveAddress(PrecompiledContracts.BRIDGE_ADDR)
                .chainId(config.getNetworkConstants().getChainId())
                .value(Coin.ZERO)
                .data(dataThatThrowsAnException)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), config.getActivationConfig());
        Block genesis = blockGenerator.getGenesisBlock();
        genesis.setStateRoot(track.getRoot());
        Block block = blockGenerator.createChildBlock(genesis, Collections.singletonList(tx), new ArrayList<>(), 1, null);

        TransactionExecutor executor = transactionExecutorFactory.newInstance(tx, 0, block.getCoinbase(), track, block, 0L);

        Assertions.assertTrue(executor.executeTransaction());
        Assertions.assertNotNull(executor.getResult().getException());

        TransactionReceipt receipt = executor.getReceipt();
        BigInteger reportedGasUsed = new BigInteger(1, receipt.getGasUsed());

        if (rskip560Active) {
            Coin expectedFullFee = Coin.valueOf(gasLimit.longValueExact() * gasPrice);
            Coin expectedReportedFee = Coin.valueOf(reportedGasUsed.longValueExact() * gasPrice);

            Assertions.assertEquals(expectedFullFee, executor.getPaidFees());
            Assertions.assertFalse(receipt.isSuccessful(), "post-activation, receipt must correctly report FAILED status");
            Assertions.assertEquals(gasLimit, reportedGasUsed, "post-activation, receipt.gasUsed must equal the full gasLimit charged");
            Assertions.assertEquals(expectedReportedFee,  executor.getPaidFees(), "paidFees must equal receipt.gasUsed * gasPrice");
        } else {
            Coin expectedLegacyFee = Coin.valueOf(reportedGasUsed.longValueExact() * gasPrice);
            Assertions.assertEquals(expectedLegacyFee, executor.getPaidFees());
            Assertions.assertTrue(receipt.isSuccessful(), "pre-activation, legacy (buggy) SUCCESS status must be preserved");
            Assertions.assertTrue(reportedGasUsed.compareTo(gasLimit) < 0, "pre-activation, receipt.gasUsed must keep under-reporting, for backward compatibility");
        }
    }

    private static ActivationConfig withRskip560(ActivationConfig defaults, boolean active) {
        Map<ConsensusRule, Long> heights = new EnumMap<>(ConsensusRule.class);
        for (ConsensusRule rule : ConsensusRule.values()) {
            heights.put(rule, defaults.isActive(rule, 0L) ? 0L : -1L);
        }
        heights.put(ConsensusRule.RSKIP560, active ? 0L : -1L);
        return new ActivationConfig(heights, new HashMap<>());
    }
}

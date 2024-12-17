package co.rsk.peg;

import static co.rsk.peg.BridgeSupportTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbStorageProvider;
import co.rsk.peg.feeperkb.FeePerKbStorageProviderImpl;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;

class RegisterBtcTransactionIT {
    private final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcParams = bridgeConstants.getBtcParams();
    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
    private Repository track;
    private Repository repository;
    private Block rskExecutionBlock;
    private FederationSupport federationSupport;
    private BridgeStorageProvider bridgeStorageProvider;
    private BtcTransaction bitcoinTransaction;
    private PartialMerkleTree pmtWithTransactions;
    private int btcBlockWithPmtHeight;
    private Transaction rskTx;
    private RskAddress rskReceiver;
    private BridgeSupport bridgeSupport;
    private BridgeEventLoggerImpl bridgeEventLogger;
    private Coin btcTransferred;

    @BeforeEach
    void setUp() throws Exception{
        rskTx = TransactionUtils.createTransaction();
        repository = BridgeSupportTestUtil.createRepository();
        track = repository.startTracking();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
        rskExecutionBlock = getRskExecutionBlock();

        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
        FeePerKbSupport feePerKbSupport = getFeePerKbSupport(repository, bridgeConstants);

        Federation federation = P2shErpFederationBuilder.builder().build();
        FederationStorageProvider federationStorageProvider = getFederationStorageProvider(track, federation);
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(federationConstants)
                .withFederationStorageProvider(federationStorageProvider)
                .withActivations(activations)
                .build();

        bridgeStorageProvider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants.getBtcParams(), activations);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams(), 100, 100);
        BtcBlockStoreWithCache btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(track, bridgeConstants, bridgeStorageProvider, activations);

        BtcECKey btcPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed("seed");
        rskReceiver = getRskReceiver(btcPublicKey);
        btcTransferred = bridgeConstants.getMinimumPeginTxValue(activations);
        bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), btcTransferred, btcPublicKey);

        pmtWithTransactions = createValidPmtForTransactions(List.of(bitcoinTransaction.getHash()), bridgeConstants.getBtcParams());
        btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();

        bridgeEventLogger = spy(new BridgeEventLoggerImpl(bridgeConstants, activations, new ArrayList<>()));

        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, bridgeConstants.getBtcParams());
        bridgeStorageProvider.save();
        bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeConstants)
                .withProvider(bridgeStorageProvider)
                .withActivations(activations)
                .withEventLogger(bridgeEventLogger)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withRepository(track)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .build();
    }

    @Test
    void whenRegisterALegacyBtcTransaction_shouldRegisterTheNewUtxoAndTransferTheRbtcBalance() throws Exception {
        // Arrange
        TransactionOutput output = bitcoinTransaction.getOutput(0);
        List<UTXO> expectedFederationUtxos = Collections.singletonList(utxoOf(bitcoinTransaction, output));

        co.rsk.core.Coin receiverBalance = track.getBalance(rskReceiver);
        co.rsk.core.Coin expectedReceiverBalance = receiverBalance.add(co.rsk.core.Coin.fromBitcoin(btcTransferred));

        // Act
        registerBtcTransactionAndCommit();

        // Assert
        Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash());
        assertTrue(heightIfBtcTxHashIsAlreadyProcessed.isPresent());
        assertEquals(rskExecutionBlock.getNumber(), heightIfBtcTxHashIsAlreadyProcessed.get());
        assertEquals(expectedFederationUtxos, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        verify(bridgeEventLogger, times(1)).logPeginBtc(rskReceiver, bitcoinTransaction, btcTransferred, 0);

    }

    @Test
    void whenRegisterARepeatedLegacyBtcTransaction_shouldNotPerformAnyChange() throws Exception {
        // Arrange
        registerBtcTransactionAndCommit();

        co.rsk.core.Coin expectedReceiverBalance = track.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = federationSupport.getActiveFederationBtcUTXOs();

        // Act
        registerBtcTransactionAndCommit();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));
    }

    private void registerBtcTransactionAndCommit() throws Exception {
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();
        track.commit();
    }

    private static UTXO utxoOf(BtcTransaction bitcoinTransaction, TransactionOutput output) {
        return new UTXO(
                bitcoinTransaction.getHash(),
                output.getIndex(),
                output.getValue(),
                0,
                bitcoinTransaction.isCoinBase(),
                output.getScriptPubKey()
        );
    }

    private RskAddress getRskReceiver(BtcECKey btcPublicKey) {
        ECKey ecKey = ECKey.fromPublicOnly(btcPublicKey.getPubKey());
        return new RskAddress(ecKey.getAddress());
    }

    private FederationStorageProvider getFederationStorageProvider(Repository track, Federation federation) {
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        federationStorageProvider.setNewFederation(federation);
        return federationStorageProvider;
    }

    private static FeePerKbSupport getFeePerKbSupport(Repository repository, BridgeConstants bridgeConstants) {
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);
        return  new FeePerKbSupportImpl(
                bridgeConstants.getFeePerKbConstants(),
                feePerKbStorageProvider
        );
    }

    private BtcTransaction createPegInTransaction(Address federationAddress, Coin coin, BtcECKey pubKey) {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(BitcoinTestUtils.createHash(0), 0, ScriptBuilder.createInputScript(null, pubKey));
        btcTx.addOutput(new TransactionOutput(btcParams, btcTx, coin, federationAddress));

        return btcTx;
    }
}

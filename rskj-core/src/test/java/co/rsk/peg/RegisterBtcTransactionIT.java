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
    private final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    private final Transaction rskTx = TransactionUtils.createTransaction();
    private final Coin minimumPeginValue =  bridgeConstants.getMinimumPeginTxValue(activations);
    private final Block rskExecutionBlock = getRskExecutionBlock();
    private Repository repository;
    private FederationSupport federationSupport;
    private BridgeStorageProvider bridgeStorageProvider;
    private BtcTransaction bitcoinTransaction;
    private PartialMerkleTree pmtWithTransactions;
    private int btcBlockWithPmtHeight;
    private RskAddress rskReceiver;
    private BridgeSupport bridgeSupport;
    private BridgeEventLoggerImpl bridgeEventLogger;


    @BeforeEach
    void setUp() throws Exception{
        repository = BridgeSupportTestUtil.createRepository().startTracking();

        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);

        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);
        FeePerKbSupport feePerKbSupport = new FeePerKbSupportImpl(
                bridgeConstants.getFeePerKbConstants(),
                feePerKbStorageProvider
        );

        Federation federation = P2shErpFederationBuilder.builder().build();
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation);
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(federationConstants)
                .withFederationStorageProvider(federationStorageProvider)
                .withActivations(activations)
                .build();

        bridgeStorageProvider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants.getBtcParams(), activations);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams(), 100, 100);
        BtcBlockStoreWithCache btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(repository, bridgeConstants, bridgeStorageProvider, activations);

        BtcECKey btcPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed("seed");
        ECKey ecKey = ECKey.fromPublicOnly(btcPublicKey.getPubKey());
        rskReceiver = new RskAddress(ecKey.getAddress());
        bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), minimumPeginValue, btcPublicKey);

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
                .withRepository(repository)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .build();
    }

    @Test
    void whenRegisterALegacyBtcTransaction_shouldRegisterTheNewUtxoAndTransferTheRbtcBalance() throws Exception {
        // Arrange
        TransactionOutput output = bitcoinTransaction.getOutput(0);
        List<UTXO> expectedFederationUtxos = Collections.singletonList(utxoOf(bitcoinTransaction, output));

        co.rsk.core.Coin receiverBalance = repository.getBalance(rskReceiver);
        co.rsk.core.Coin expectedReceiverBalance = receiverBalance.add(co.rsk.core.Coin.fromBitcoin(minimumPeginValue));

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash());
        assertTrue(heightIfBtcTxHashIsAlreadyProcessed.isPresent());
        assertEquals(rskExecutionBlock.getNumber(), heightIfBtcTxHashIsAlreadyProcessed.get());
        assertEquals(expectedFederationUtxos, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

        verify(bridgeEventLogger, times(1)).logPeginBtc(rskReceiver, bitcoinTransaction, minimumPeginValue, 0);

    }

    @Test
    void whenRegisterARepeatedLegacyBtcTransaction_shouldNotPerformAnyChange() throws Exception {
        // Arrange
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        co.rsk.core.Coin expectedReceiverBalance = repository.getBalance(rskReceiver);
        List<UTXO> expectedFederationUTXOs = federationSupport.getActiveFederationBtcUTXOs();

        // Act
        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        bridgeSupport.save();

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));
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

    private BtcTransaction createPegInTransaction(Address federationAddress, Coin coin, BtcECKey pubKey) {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(BitcoinTestUtils.createHash(0), 0, ScriptBuilder.createInputScript(null, pubKey));
        btcTx.addOutput(new TransactionOutput(btcParams, btcTx, coin, federationAddress));

        return btcTx;
    }
}

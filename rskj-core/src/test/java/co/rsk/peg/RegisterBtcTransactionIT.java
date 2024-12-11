package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;


public class RegisterBtcTransactionIT {

    public static final Coin BTC_TRANSFERRED = Coin.COIN;
    private final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcParams = bridgeConstants.getBtcParams();
    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
    private ActivationConfig.ForBlock activationConfig;
    private Repository track;
    private Repository repository;
    private Block rskExecutionBlock;
    private BtcLockSenderProvider btcLockSenderProvider;
    private FeePerKbSupport feePerKbSupport;
    private FederationSupport federationSupport;
    private BridgeStorageProvider bridgeStorageProvider;
    private BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BtcBlockStoreWithCache btcBlockStoreWithCache;
    private BtcTransaction bitcoinTransaction;
    private PartialMerkleTree pmtWithTransactions;
    private int btcBlockWithPmtHeight;
    private int chainHeight;
    private Transaction rskTx;
    private ECKey ecKey;
    private RskAddress rskReceiver;
    private BridgeSupport bridgeSupport;

    @BeforeEach
    void setUp() {
        rskTx = TransactionUtils.createTransaction();
        repository = BridgeSupportTestUtil.createRepository();
        track = repository.startTracking();

        activationConfig = ActivationConfigsForTest.all().forBlock(0);
        rskExecutionBlock = getRskExecutionBlock();

        btcLockSenderProvider = new BtcLockSenderProvider();
        feePerKbSupport = getFeePerKbSupport();

        Federation federation = P2shErpFederationBuilder.builder().build();
        FederationStorageProvider federationStorageProvider = getFederationStorageProvider(track, federation);
        federationSupport = getFederationSupport(federationStorageProvider, activationConfig, bridgeConstants.getFederationConstants());

        bridgeStorageProvider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants.getBtcParams(), activationConfig);
        btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams(), 100, 100);
        btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(track, bridgeConstants, bridgeStorageProvider, activationConfig);

        BtcECKey btcPublicKey = new BtcECKey();
        ecKey = ECKey.fromPublicOnly(btcPublicKey.getPubKey());
        rskReceiver = new RskAddress(ecKey.getAddress());
        bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), BTC_TRANSFERRED, btcPublicKey);

        pmtWithTransactions = createValidPmtForTransactions(Collections.singletonList(bitcoinTransaction.getHash()), bridgeConstants.getBtcParams());
        btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();

        recreateChainFromPmtOrFail();
        bridgeStorageProvider.save();
        bridgeSupport = getBridgeSupport(bridgeStorageProvider, activationConfig, federationSupport, feePerKbSupport, rskExecutionBlock, btcBlockStoreFactory, track, btcLockSenderProvider);
    }

    @Test
    void whenRegisterALegacyBtcTransaction_shouldRegisterTheNewUtxoAndTransferTheRbtcBalance() {
        // Arrange
        TransactionOutput output = bitcoinTransaction.getOutput(0);
        UTXO utxo = getUtxo(bitcoinTransaction, output);
        List<UTXO> expectedFederationUtxos = Collections.singletonList(utxo);

        co.rsk.core.Coin receiverBalance = track.getBalance(rskReceiver);
        co.rsk.core.Coin expectedReceiverBalance = receiverBalance.add(co.rsk.core.Coin.fromBitcoin(BTC_TRANSFERRED));

        // Act
        registerBtcTransactionOrFail(btcBlockWithPmtHeight);

        // Assert
        try {
            Optional<Long> heightIfBtcTxHashIsAlreadyProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash());
            assertTrue(heightIfBtcTxHashIsAlreadyProcessed.isPresent());
            assertEquals(rskExecutionBlock.getNumber(), heightIfBtcTxHashIsAlreadyProcessed.get());
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertEquals(expectedFederationUtxos, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(rskReceiver));

    }

    @Test
    void whenRegisterARepeatedLegacyBtcTransaction_shouldNotPerformAnyChange() {
        // Arrange
        registerBtcTransactionOrFail(btcBlockWithPmtHeight);

        RskAddress receiverBalance = new RskAddress(ecKey.getAddress());
        co.rsk.core.Coin expectedReceiverBalance = track.getBalance(receiverBalance);
        List<UTXO> expectedFederationUTXOs = federationSupport.getActiveFederationBtcUTXOs();

        // Act
        registerBtcTransactionOrFail(btcBlockWithPmtHeight);

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(receiverBalance));
    }

    @Test
    void whenRegisterALegacyBtcTransactionWithNegativeHeight_shouldNotPerformAnyChange() {
        // Arrange
        RskAddress receiverBalance = new RskAddress(ecKey.getAddress());
        co.rsk.core.Coin expectedReceiverBalance = track.getBalance(receiverBalance);
        List<UTXO> expectedFederationUTXOs = federationSupport.getActiveFederationBtcUTXOs();

        // Act
        registerBtcTransactionOrFail(-1);

        // Assert
        assertEquals(expectedFederationUTXOs, federationSupport.getActiveFederationBtcUTXOs());
        assertEquals(expectedReceiverBalance, repository.getBalance(receiverBalance));
    }

    private void registerBtcTransactionOrFail(int blockWithPmtHeight) {
        try {
            bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), blockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());
        } catch (Exception e) {
            fail(e.getMessage());
        }

        bridgeSupport.save();
        track.commit();
    }

    private void recreateChainFromPmtOrFail() {
        try {
            recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, bridgeConstants.getBtcParams());
        } catch (BlockStoreException e) {
            fail(e.getMessage());
        }
    }

    private static UTXO getUtxo(BtcTransaction bitcoinTransaction, TransactionOutput output) {
        return new UTXO(
                bitcoinTransaction.getHash(),
                output.getIndex(),
                output.getValue(),
                0,
                bitcoinTransaction.isCoinBase(),
                output.getScriptPubKey()
        );
    }

    private static FederationSupport getFederationSupport(FederationStorageProvider federationStorageProvider, ActivationConfig.ForBlock activationsBeforeForks, FederationConstants federationConstants) {
        return FederationSupportBuilder.builder()
                .withFederationConstants(federationConstants)
                .withFederationStorageProvider(federationStorageProvider)
                .withActivations(activationsBeforeForks)
                .build();
    }

    private FederationStorageProvider getFederationStorageProvider(Repository track, Federation federation) {
        FederationStorageProvider federationStorageProvider = createFederationStorageProvider(track);
        federationStorageProvider.setNewFederation(federation);
        return federationStorageProvider;
    }

    private static FeePerKbSupport getFeePerKbSupport() {
        FeePerKbSupport feePerKbSupport = mock(FeePerKbSupport.class);
        Coin feePerKb = Coin.valueOf(1000L);
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);
        return feePerKbSupport;
    }

    private static Block getRskExecutionBlock() {
        long rskExecutionBlockNumber = 1000L;
        long rskExecutionBlockTimestamp = 10L;
        BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
                .setNumber(rskExecutionBlockNumber)
                .setTimestamp(rskExecutionBlockTimestamp)
                .build();
        return Block.createBlockFromHeader(blockHeader, true);
    }

    private BridgeSupport getBridgeSupport(BridgeStorageProvider bridgeStorageProvider, ActivationConfig.ForBlock activationsBeforeForks, FederationSupport federationSupport, FeePerKbSupport feePerKbSupport, Block rskExecutionBlock, BtcBlockStoreWithCache.Factory btcBlockStoreFactory, Repository repository, BtcLockSenderProvider btcLockSenderProvider) {
        return bridgeSupportBuilder
                .withBridgeConstants(bridgeConstants)
                .withProvider(bridgeStorageProvider)
                .withActivations(activationsBeforeForks)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withRepository(repository)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .build();
    }


    private FederationStorageProvider createFederationStorageProvider(Repository repository) {
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
        return new FederationStorageProviderImpl(bridgeStorageAccessor);
    }

    private BtcTransaction createPegInTransaction(Address federationAddress, Coin coin, BtcECKey pubKey) {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(BitcoinTestUtils.createHash(0), 0, ScriptBuilder.createInputScript(null, pubKey));
        btcTx.addOutput(new TransactionOutput(btcParams, btcTx, coin, federationAddress));

        return btcTx;
    }

    private static PartialMerkleTree createValidPmtForTransactions(List<Sha256Hash> hashesToAdd, NetworkParameters networkParameters) {
        byte[] relevantNodesBits = new byte[(int)Math.ceil(hashesToAdd.size() / 8.0)];
        for (int i = 0; i < hashesToAdd.size(); i++) {
            Utils.setBitLE(relevantNodesBits, i);
        }

        return PartialMerkleTree.buildFromLeaves(networkParameters, relevantNodesBits, hashesToAdd);
    }

    private static void recreateChainFromPmt(
            BtcBlockStoreWithCache btcBlockStoreWithCache,
            int chainHeight,
            PartialMerkleTree partialMerkleTree,
            int btcBlockWithPmtHeight,
            NetworkParameters networkParameters
    ) throws BlockStoreException {

        // first create a block that has the wanted partial merkle tree
        BtcBlock btcBlockWithPmt = createBtcBlockWithPmt(partialMerkleTree, networkParameters);
        // store it on the chain at wanted height
        StoredBlock storedBtcBlockWithPmt = new StoredBlock(btcBlockWithPmt, BigInteger.ONE, btcBlockWithPmtHeight);
        btcBlockStoreWithCache.put(storedBtcBlockWithPmt);
        btcBlockStoreWithCache.setMainChainBlock(btcBlockWithPmtHeight, btcBlockWithPmt.getHash());

        // create and store a new chainHead at wanted chain height
        Sha256Hash otherTransactionHash = Sha256Hash.of(Hex.decode("aa"));
        PartialMerkleTree pmt = createValidPmtForTransactions(Collections.singletonList(otherTransactionHash), networkParameters);
        BtcBlock chainHeadBlock = createBtcBlockWithPmt(pmt, networkParameters);
        StoredBlock storedChainHeadBlock = new StoredBlock(chainHeadBlock, BigInteger.TEN, chainHeight);
        btcBlockStoreWithCache.put(storedChainHeadBlock);
        btcBlockStoreWithCache.setChainHead(storedChainHeadBlock);
    }

    private static BtcBlock createBtcBlockWithPmt(PartialMerkleTree pmt, NetworkParameters networkParameters) {
        Sha256Hash prevBlockHash = BitcoinTestUtils.createHash(1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());

        return new co.rsk.bitcoinj.core.BtcBlock(
                networkParameters,
                1,
                prevBlockHash,
                merkleRoot,
                1,
                1,
                1,
                new ArrayList<>()
        );
    }
}

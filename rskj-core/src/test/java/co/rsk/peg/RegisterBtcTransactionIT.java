package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

import static org.mockito.Mockito.*;

public class RegisterBtcTransactionIT {

    public static final Coin BTC_TRANSFERRED = Coin.COIN;
    private static final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcParams = bridgeConstants.getBtcParams();
    private final BtcECKey btcPublicKey = new BtcECKey();
    private BridgeSupportBuilder bridgeSupportBuilder;
    private BridgeSupport bridgeSupport;
    private Repository track;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationSupport federationSupport;
    private Repository repository;
    private int btcBlockWithPmtHeight;
    private PartialMerkleTree pmtWithTransactions;
    private Block rskExecutionBlock;
    private Transaction rskTransaction;
    private RskAddress receiverRskAccount;
    private BtcBlockStoreWithCache btcBlockStoreWithCache;

    @BeforeEach
    public void setup() throws BlockStoreException {
        ActivationConfig.ForBlock activationConfig = ActivationConfigsForTest.all().forBlock(0);
        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();

        bridgeSupportBuilder = BridgeSupportBuilder.builder();
        repository = BridgeSupportTestUtil.createRepository();
        track = repository.startTracking();

        org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(btcPublicKey.getPubKey());
        rskExecutionBlock = getRskExecutionBlock();
        rskTransaction = getRskTransaction();
        receiverRskAccount = new RskAddress(key.getAddress());

        Federation federation = P2shErpFederationBuilder.builder().build();
        FederationStorageProvider federationStorageProvider = getFederationStorageProvider(track, federation);
        federationSupport = getFederationSupport(federationStorageProvider, activationConfig);

        bridgeStorageProvider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants.getBtcParams(), activationConfig);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams(), 100, 100);
        btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(track, bridgeConstants, bridgeStorageProvider, activationConfig);

        bridgeSupport = getBridgeSupport(bridgeStorageProvider, activationConfig, federationSupport, rskExecutionBlock, btcBlockStoreFactory, track, btcLockSenderProvider);
    }

    @Test
    void whenRegisterALegacyBtcTransactionTheBridgeShouldRegisterTheNewUtxoAndTransferTheRbtcBalance() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        BtcTransaction bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), BTC_TRANSFERRED, btcPublicKey);
        pmtWithTransactions = createValidPmtForTransactions(Collections.singletonList(bitcoinTransaction.getHash()), bridgeConstants.getBtcParams());
        btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, bridgeConstants.getBtcParams());
        bridgeStorageProvider.save();

        int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
        co.rsk.core.Coin receiverBalance = track.getBalance(receiverRskAccount);
        TransactionOutput output = bitcoinTransaction.getOutput(0);
        UTXO utxo = getUtxo(bitcoinTransaction, output);

        bridgeSupport.registerBtcTransaction(rskTransaction, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());

        bridgeSupport.save();
        track.commit();

        List<UTXO> activeFederationUtxosSizeAfterRegisteringTx = federationSupport.getActiveFederationBtcUTXOs();
        List<UTXO> expectedUtxos = Collections.singletonList(utxo);

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + 1, activeFederationUtxosSizeAfterRegisteringTx.size());
        assertEquals(expectedUtxos, activeFederationUtxosSizeAfterRegisteringTx);
        assertEquals(receiverBalance.add(co.rsk.core.Coin.fromBitcoin(BTC_TRANSFERRED)), repository.getBalance(receiverRskAccount));
        assertTrue(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash()).isPresent());
        assertEquals(rskExecutionBlock.getNumber(), bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash()).get());
    }
    @Test
    void whenRegisterALegacyBtcTransactionAlreadyProcessedNoChangesShouldBePerformed() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        BtcTransaction bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), BTC_TRANSFERRED, btcPublicKey);
        pmtWithTransactions = createValidPmtForTransactions(Collections.singletonList(bitcoinTransaction.getHash()), bridgeConstants.getBtcParams());
        btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, bridgeConstants.getBtcParams());
        bridgeStorageProvider.save();

        bridgeSupport.registerBtcTransaction(rskTransaction, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());

        bridgeSupport.save();
        track.commit();

        List<UTXO> activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs();
        co.rsk.core.Coin receiverBalanceBeforeRegisteringTx = track.getBalance(receiverRskAccount);

        bridgeSupport.registerBtcTransaction(rskTransaction, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());

        bridgeSupport.save();
        track.commit();

        List<UTXO> activeFederationUtxosSizeAfterRegisteringTx = federationSupport.getActiveFederationBtcUTXOs();
        co.rsk.core.Coin receiverBalanceAfterRegisteringTx = track.getBalance(receiverRskAccount);

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx.size(), activeFederationUtxosSizeAfterRegisteringTx.size());
        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx, activeFederationUtxosSizeAfterRegisteringTx);
        assertEquals(receiverBalanceBeforeRegisteringTx, receiverBalanceAfterRegisteringTx);
    }

    @Test
    void whenRegisterATransactionWithNegativeHeightItShouldThrowARegisterBtcTransactionException() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        BtcTransaction bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), BTC_TRANSFERRED, btcPublicKey);
        pmtWithTransactions = createValidPmtForTransactions(Collections.singletonList(bitcoinTransaction.getHash()), bridgeConstants.getBtcParams());
        btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, bridgeConstants.getBtcParams());
        bridgeStorageProvider.save();

        List<UTXO> activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs();
        co.rsk.core.Coin receiverBalanceBeforeRegisteringTx = track.getBalance(receiverRskAccount);

        bridgeSupport.registerBtcTransaction(rskTransaction, bitcoinTransaction.bitcoinSerialize(), -1, pmtWithTransactions.bitcoinSerialize());

        bridgeSupport.save();
        track.commit();

        List<UTXO> activeFederationUtxosSizeAfterRegisteringTx = federationSupport.getActiveFederationBtcUTXOs();
        co.rsk.core.Coin receiverBalanceAfterRegisteringTx = track.getBalance(receiverRskAccount);

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx.size(), activeFederationUtxosSizeAfterRegisteringTx.size());
        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx, activeFederationUtxosSizeAfterRegisteringTx);
        assertEquals(receiverBalanceBeforeRegisteringTx, receiverBalanceAfterRegisteringTx);
    }

    @Test
    void whenRegisterALegacyBtcTransactionTheBridgeShouldRegisterTheNewUtxoAndTransferTheRbtcBalanceP2SHMultiSig() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        BtcTransaction btcTxP2SHMultiSig = new BtcTransaction(btcParams);
        btcTxP2SHMultiSig.addInput(BitcoinTestUtils.createHash(0), 0, ScriptBuilder.createP2SHMultiSigInputScript(null, federationSupport.getActiveFederationRedeemScript().get()));
        btcTxP2SHMultiSig.addOutput(new TransactionOutput(btcParams, btcTxP2SHMultiSig, BTC_TRANSFERRED, federationSupport.getActiveFederationAddress()));

        pmtWithTransactions = createValidPmtForTransactions(Collections.singletonList(btcTxP2SHMultiSig.getHash()), bridgeConstants.getBtcParams());
        btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, bridgeConstants.getBtcParams());
        bridgeStorageProvider.save();

        int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
        co.rsk.core.Coin receiverBalance = track.getBalance(receiverRskAccount);
        TransactionOutput output = btcTxP2SHMultiSig.getOutput(0);
        UTXO utxo = getUtxo(btcTxP2SHMultiSig, output);

        bridgeSupport.registerBtcTransaction(rskTransaction, btcTxP2SHMultiSig.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());

        bridgeSupport.save();
        track.commit();

        List<UTXO> activeFederationUtxosSizeAfterRegisteringTx = federationSupport.getActiveFederationBtcUTXOs();
        List<UTXO> expectedUtxos = Collections.singletonList(utxo);

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + 1, activeFederationUtxosSizeAfterRegisteringTx.size());
        assertEquals(expectedUtxos, activeFederationUtxosSizeAfterRegisteringTx);
        assertEquals(receiverBalance.add(co.rsk.core.Coin.fromBitcoin(BTC_TRANSFERRED)), repository.getBalance(receiverRskAccount));
        assertTrue(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTxP2SHMultiSig.getHash()).isPresent());
        assertEquals(rskExecutionBlock.getNumber(), bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTxP2SHMultiSig.getHash()).get());
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

    private static Transaction getRskTransaction() {
        Keccak256 rskTxHash = PegTestUtils.createHash3(1);
        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(rskTxHash);
        return rskTx;
    }

    private static FederationSupport getFederationSupport(FederationStorageProvider federationStorageProvider, ActivationConfig.ForBlock activationsBeforeForks) {
        return FederationSupportBuilder.builder()
                .withFederationConstants(bridgeConstants.getFederationConstants())
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

    private BridgeSupport getBridgeSupport(BridgeStorageProvider bridgeStorageProvider, ActivationConfig.ForBlock activationsBeforeForks, FederationSupport federationSupport, Block rskExecutionBlock, BtcBlockStoreWithCache.Factory btcBlockStoreFactory, Repository repository, BtcLockSenderProvider btcLockSenderProvider) {
        return bridgeSupportBuilder
                .withBridgeConstants(bridgeConstants)
                .withProvider(bridgeStorageProvider)
                .withActivations(activationsBeforeForks)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(getFeePerKbSupport())
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

package co.rsk.peg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;


public class RegisterBtcTransactionIT {

    private static final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcParams = bridgeConstants.getBtcParams();
    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();

    @Test
    void whenRegisterALegacyBtcTransactionTheBridgeShouldRegisterTheNewUtxoAndTransferTheRbtcBalance() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activationConfig = ActivationConfigsForTest.all().forBlock(0);
        Repository repository = BridgeSupportTestUtil.createRepository();
        Repository track = repository.startTracking();
        Block rskExecutionBlock = getRskExecutionBlock();

        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
        FeePerKbSupport feePerKbSupport = getFeePerKbSupport();

        Federation federation = P2shErpFederationBuilder.builder().build();
        FederationStorageProvider federationStorageProvider = getFederationStorageProvider(track, federation);
        FederationSupport federationSupport = getFederationSupport(federationStorageProvider, activationConfig);

        BtcECKey btcPublicKey = new BtcECKey();
        Coin btcTransferred = Coin.COIN;
        BtcTransaction bitcoinTransaction = createPegInTransaction(federationSupport.getActiveFederation().getAddress(), btcTransferred, btcPublicKey);

        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants.getBtcParams(), activationConfig);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstants.getBtcParams(), 100, 100);
        BtcBlockStoreWithCache btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(track, bridgeConstants, bridgeStorageProvider, activationConfig);

        PartialMerkleTree pmtWithTransactions = createValidPmtForTransactions(Collections.singletonList(bitcoinTransaction.getHash()), bridgeConstants.getBtcParams());
        int btcBlockWithPmtHeight = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int chainHeight = btcBlockWithPmtHeight + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, bridgeConstants.getBtcParams());

        bridgeStorageProvider.save();

        BridgeSupport bridgeSupport = getBridgeSupport(bridgeStorageProvider, activationConfig, federationSupport, feePerKbSupport, rskExecutionBlock, btcBlockStoreFactory, track, btcLockSenderProvider);

        Transaction rskTx = getRskTransaction();
        int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
        org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(btcPublicKey.getPubKey());

        RskAddress receiver = new RskAddress(key.getAddress());
        co.rsk.core.Coin receiverBalance = track.getBalance(receiver);

        bridgeSupport.registerBtcTransaction(rskTx, bitcoinTransaction.bitcoinSerialize(), btcBlockWithPmtHeight, pmtWithTransactions.bitcoinSerialize());

        bridgeSupport.save();
        track.commit();

        TransactionOutput output = bitcoinTransaction.getOutput(0);
        UTXO utxo = getUtxo(bitcoinTransaction, output);
        List<UTXO> activeFederationUtxosSizeAfterRegisteringTx = federationSupport.getActiveFederationBtcUTXOs();

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + 1, activeFederationUtxosSizeAfterRegisteringTx.size());
        assertEquals(Collections.singletonList(utxo), activeFederationUtxosSizeAfterRegisteringTx);
        assertEquals(receiverBalance.add(co.rsk.core.Coin.fromBitcoin(btcTransferred)), repository.getBalance(receiver));
        assertTrue(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash()).isPresent());
        assertEquals(rskExecutionBlock.getNumber(), bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(bitcoinTransaction.getHash()).get());
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

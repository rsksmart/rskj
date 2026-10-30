package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.assertFederationsPendingBtcUTXOsWereSaved;
import static co.rsk.peg.BridgeSupportTestUtil.assertTransactionWasNotProcessed;
import static co.rsk.peg.BridgeSupportTestUtil.buildPMTAndRecreateChainForTxIdRegistration;
import static co.rsk.peg.BridgeSupportTestUtil.buildPegoutRequestTransaction;
import static co.rsk.peg.BridgeSupportTestUtil.getReleaseFromPegoutsWFC;
import static co.rsk.peg.bitcoin.BitcoinTestAssertions.assertUtxosAreEqual;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbStorageProviderImpl;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeBuilder;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.test.builders.UTXOBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegisterTransactionIT {
    private static final ActivationConfig ALL_ACTIVATIONS_CONFIG = ActivationConfigsForTest.all();
    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ALL_ACTIVATIONS_CONFIG.forBlock(0L);
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMETERS = BRIDGE_CONSTANTS.getBtcParams();

    private static final List<BtcECKey> ACTIVE_FEDERATION_KEYS = BitcoinTestUtils.getBtcEcKeys(20, "active");
    private static final Federation ACTIVE_FEDERATION = P2shP2wshErpFederationBuilder.builder()
        .withMembersBtcPublicKeys(ACTIVE_FEDERATION_KEYS)
        .build();
    // the builder uses each member's btc key as its rsk key, so this key signs as a member of the active federation
    private static final ECKey ACTIVE_FEDERATION_MEMBER_RSK_KEY = ECKey.fromPrivate(ACTIVE_FEDERATION_KEYS.get(0).getPrivKeyBytes());
    private static final ECKey USER_RSK_KEY = RskTestUtils.getEcKeyFromSeed("user");

    private static final Coin FEDERATION_UTXO_VALUE = Coin.COIN;
    private static final Coin PEGOUT_CHANGE_VALUE = Coin.COIN.div(4);
    private static final Coin PEGOUT_REQUEST_VALUE = FEDERATION_UTXO_VALUE.subtract(PEGOUT_CHANGE_VALUE);
    private static final int BTC_BLOCK_WITH_PEGOUT_HEIGHT = 1_000;

    // the active federation must be active for the pegout to be created
    private static final long PEGOUT_CREATION_BLOCK_NUMBER = ACTIVE_FEDERATION.getCreationBlockNumber()
        + FEDERATION_CONSTANTS.getFederationActivationAge(ALL_ACTIVATIONS);
    private static final long PEGOUT_REGISTRATION_BLOCK_NUMBER = PEGOUT_CREATION_BLOCK_NUMBER + 1;

    private Repository repository;
    // federation and fee per kb storage live outside the repository, as in BridgeTest
    private StorageAccessor bridgeStorageAccessor;
    private RepositoryBtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private List<UTXO> expectedActiveFederationUtxos;
    private long userNonce;
    private long federatorNonce;

    private Block rskExecutionBlock;
    private BridgeStorageProvider bridgeStorageProvider;
    private FederationStorageProvider federationStorageProvider;
    private FederationSupport federationSupport;
    private BridgeSupport bridgeSupport;

    @BeforeEach
    void setUp() {
        repository = createRepository();
        bridgeStorageAccessor = new InMemoryStorage();
        btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(NETWORK_PARAMETERS, 100, 100);
        userNonce = 0;
        federatorNonce = 0;
    }

    @Nested
    class RegisterPegoutTransactionFailures {
        private BtcTransaction pegout;
        private PartialMerkleTree pmt;

        @BeforeEach
        void setUp() throws IOException, BlockStoreException, VMException {
            fundActiveFederationWithOneUtxo();
            pegout = createPegout();
            // the pegout spent the only federation utxo, and its change is still pending
            expectedActiveFederationUtxos = new ArrayList<>();
            pmt = includeOnBtcAndGetPmt(pegout);

            assertPegoutIsPending(pegout);
        }

        @Test
        void registerPegoutTransaction_withPmtOfUnexpectedSize_shouldRevert() throws IOException {
            // arrange
            byte[] pmtSerialized = pmt.bitcoinSerialize();
            // one byte more than the size declared by the pmt hashes and flag bits counts
            byte[] pmtWithUnexpectedSize = Arrays.copyOf(pmtSerialized, pmtSerialized.length + 1);
            Transaction registerPegoutTransaction = buildRegisterPegoutTransaction(pegout, pmtWithUnexpectedSize);

            // act & assert
            assertThrows(VMException.class, () -> executeBridgeTransaction(registerPegoutTransaction, PEGOUT_REGISTRATION_BLOCK_NUMBER));

            // assert
            // fresh storage providers only see what was saved
            setUpBridgeSupport(PEGOUT_REGISTRATION_BLOCK_NUMBER);
            assertPegoutIsPending(pegout);
        }

        @Test
        void registerPegoutTransaction_withMalformedPmt_shouldRevert() throws IOException {
            // arrange
            byte[] malformedPmt = pmt.bitcoinSerialize();
            // zeroing the leading 4 bytes transaction count keeps the expected size,
            // but a pmt without transactions can't be parsed into a merkle root
            int pmtTransactionCountLength = 4;
            Arrays.fill(malformedPmt, 0, pmtTransactionCountLength, (byte) 0);
            Transaction registerPegoutTransaction = buildRegisterPegoutTransaction(pegout, malformedPmt);

            // act & assert
            assertThrows(VMException.class, () -> executeBridgeTransaction(registerPegoutTransaction, PEGOUT_REGISTRATION_BLOCK_NUMBER));

            // assert
            // fresh storage providers only see what was saved
            setUpBridgeSupport(PEGOUT_REGISTRATION_BLOCK_NUMBER);
            assertPegoutIsPending(pegout);
        }
    }

    private void fundActiveFederationWithOneUtxo() {
        setUpBridgeSupport(ACTIVE_FEDERATION.getCreationBlockNumber());
        federationStorageProvider.setNewFederation(ACTIVE_FEDERATION);
        UTXO federationUtxo = UTXOBuilder.builder()
            .withTransactionHash(BitcoinTestUtils.createHash(1))
            .withValue(FEDERATION_UTXO_VALUE)
            .withScriptPubKey(ACTIVE_FEDERATION.getP2SHScript())
            .build();
        federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMETERS, ALL_ACTIVATIONS).add(federationUtxo);
        federationStorageProvider.save(NETWORK_PARAMETERS, ALL_ACTIVATIONS);
    }

    private BtcTransaction createPegout() throws IOException, VMException {
        Transaction pegoutRequestTransaction = buildPegoutRequestTransaction(
            co.rsk.core.Coin.fromBitcoin(PEGOUT_REQUEST_VALUE),
            userNonce++,
            USER_RSK_KEY
        );
        executeBridgeTransaction(pegoutRequestTransaction, PEGOUT_CREATION_BLOCK_NUMBER);

        Transaction updateCollectionsTransaction = buildBridgeTransaction(
            federatorNonce++,
            Bridge.UPDATE_COLLECTIONS.encode()
        );
        executeBridgeTransaction(updateCollectionsTransaction, PEGOUT_CREATION_BLOCK_NUMBER);

        setUpBridgeSupport(PEGOUT_CREATION_BLOCK_NUMBER);
        return getReleaseFromPegoutsWFC(bridgeStorageProvider);
    }

    private PartialMerkleTree includeOnBtcAndGetPmt(BtcTransaction btcTransaction) throws BlockStoreException {
        setUpBridgeSupport(PEGOUT_REGISTRATION_BLOCK_NUMBER);
        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(
            repository,
            BRIDGE_CONSTANTS,
            bridgeStorageProvider,
            ALL_ACTIVATIONS
        );
        return buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            BRIDGE_CONSTANTS,
            BTC_BLOCK_WITH_PEGOUT_HEIGHT,
            btcTransaction,
            btcBlockStore
        );
    }

    private Transaction buildRegisterPegoutTransaction(BtcTransaction pegoutToRegister, byte[] pmtSerialized) {
        byte[] data = Bridge.REGISTER_PEGOUT_TRANSACTION.encode(
            pegoutToRegister.getHash().getBytes(),
            BTC_BLOCK_WITH_PEGOUT_HEIGHT,
            pmtSerialized
        );
        return buildBridgeTransaction(
            federatorNonce++,
            data
        );
    }

    private Transaction buildBridgeTransaction(long nonce, byte[] data) {
        Transaction transaction = Transaction.builder()
            .nonce(BigInteger.valueOf(nonce))
            .destination(PrecompiledContracts.BRIDGE_ADDR)
            .data(data)
            .chainId(Constants.MAINNET_CHAIN_ID)
            .build();
        transaction.sign(ACTIVE_FEDERATION_MEMBER_RSK_KEY.getPrivKeyBytes());
        return transaction;
    }

    /*
     * Runs a transaction against the bridge the way the node does: with storage providers and supports built
     * fresh for it, so it only sees what previous transactions saved. Bridge#execute saves on success; a revert
     * throws before saving.
     */
    private void executeBridgeTransaction(Transaction transaction, long executionBlockNumber) throws VMException {
        setUpBridgeSupport(executionBlockNumber);
        Bridge bridge = new BridgeBuilder()
            .activationConfig(ALL_ACTIVATIONS_CONFIG)
            .transaction(transaction)
            .bridgeSupport(bridgeSupport)
            .executionBlock(rskExecutionBlock)
            .build();

        bridge.execute(transaction.getData());
    }

    private void setUpBridgeSupport(long executionBlockNumber) {
        rskExecutionBlock = createRskBlock(executionBlockNumber);
        bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            NETWORK_PARAMETERS,
            ALL_ACTIVATIONS
        );
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskExecutionBlock)
            .withActivations(ALL_ACTIVATIONS)
            .build();

        bridgeSupport = BridgeSupportBuilder.builder()
            .withActivations(ALL_ACTIVATIONS)
            .withExecutionBlock(rskExecutionBlock)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, ALL_ACTIVATIONS, new ArrayList<>()))
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(new FeePerKbSupportImpl(
                BRIDGE_CONSTANTS.getFeePerKbConstants(),
                new FeePerKbStorageProviderImpl(bridgeStorageAccessor)
            ))
            .build();
    }

    private void assertPegoutIsPending(BtcTransaction pendingPegout) throws IOException {
        assertFederationsPendingBtcUTXOsWereSaved(
            federationStorageProvider,
            federationSupport,
            pendingPegout
        );
        assertTransactionWasNotProcessed(bridgeStorageProvider, pendingPegout.getHash());
        assertUtxosAreEqual(expectedActiveFederationUtxos, getActiveFederationUtxos());
    }

    private List<UTXO> getActiveFederationUtxos() {
        return federationSupport.getActiveFederationBtcUTXOs();
    }
}

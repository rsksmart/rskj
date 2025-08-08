package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeSupportRegisterBtcTransactionExceptionTest {
    private final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
    private final ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0);
    private final RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private final Script nonParseableScriptPubKey = ScriptBuilder.createInputScript(null, BitcoinTestUtils.getBtcEcKeyFromSeed("abc"));
    private final Script parseableScriptPubKey = ScriptBuilder.createInputScript(null, BtcECKey.fromPublicOnly(
        Hex.decode("0377a6c71c43d9fac4343f87538cd2880cf5ebefd3dd1d9aabdbbf454bca162de9")
    ));
    private final RskAddress destinationRskAddress = PegTestUtils.createRandomRskAddress();
    private final Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(
        1,
        destinationRskAddress,
        Optional.empty()
    );
    private final List<BtcECKey> activeFedKeys = List.of(
        BtcECKey.fromPublicOnly(Hex.decode("02099fd69cf6a350679a05593c3ff814bfaa281eb6dde505c953cf2875979b1209")),
        BtcECKey.fromPublicOnly(Hex.decode("0222caa9b1436ebf8cdf0c97233a8ca6713ed37b5105bcbbc674fd91353f43d9f7")),
        BtcECKey.fromPublicOnly(Hex.decode("022a159227df514c7b7808ee182ae07d71770b67eda1e5ee668272761eefb2c24c")),
        BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da")),
        BtcECKey.fromPublicOnly(Hex.decode("02b1645d3f0cff938e3b3382b93d2d5c082880b86cbb70b6600f5276f235c28392")),
        BtcECKey.fromPublicOnly(Hex.decode("030297f45c6041e322ecaee62eb633e84825da984009c731cba980286f532b8d96")),
        BtcECKey.fromPublicOnly(Hex.decode("039ee63f1e22ed0eb772fe0a03f6c34820ce8542f10e148bc3315078996cb81b25")),
        BtcECKey.fromPublicOnly(Hex.decode("03e2fbfd55959660c94169320ed0a778507f8e4c7a248a71c6599a4ce8a3d956ac")),
        BtcECKey.fromPublicOnly(Hex.decode("03eae17ad1d0094a5bf33c037e722eaf3056d96851450fb7f514a9ed3af1dbb570"))
    );

    private final byte[] testnetRealPeginSerialized = Hex.decode("0200000002d5dfff21c0e1f0b02dcbcda77a56ffd6412b79d2081bc4bc0466cf3f0913b297010000006a47304402201dc13fabe4b29d0a596a84f6f15b3d2d636625a8aa02acb8a4635038322040e10220421d22271ca64b7c02b496dc916f092ea84a74e811f81a328f2f9d888aaee59b01210342e7b7961475e1fcb0e604ed34fc554f14ce7f931373646e98e463ae52a4b564fdffffffd5dfff21c0e1f0b02dcbcda77a56ffd6412b79d2081bc4bc0466cf3f0913b297020000006a47304402207e0add6292ac318db6657aeeb717818136f0f4d6e4efef35302ce72a79731fba0220297c3259eed72cd15fae7e0b9a63d2321e415eb6bd36c023bd179455f236147301210342e7b7961475e1fcb0e604ed34fc554f14ce7f931373646e98e463ae52a4b564fdffffff030000000000000000446a4252534b540147bc43b214c418c101b976f8bbb5101ed262a069011ae302de6607907116810e598b83897b00f764d5c0eff2d4911d78c411bf873de759e10d3b2eeaba04bc0300000000001976a914dfc505d84d81d346563fe9726a76c28e9ea8454588ac20a107000000000017a91405804450706addc3c6df3a400a22397ecaafe2d687cfba3d00");
    private final BtcTransaction testnetRealPegin = new BtcTransaction(BridgeTestNetConstants.getInstance().getBtcParams(), testnetRealPeginSerialized);
    private final Keccak256 rskTxHash = PegTestUtils.createHash3(1);

    private BridgeConstants bridgeConstants;
    private Transaction rskTx;
    private BridgeStorageProvider bridgeStorageProvider;
    private BtcBlockStoreWithCache btcBlockStore;
    private BridgeSupport bridgeSupport;
    private FederationSupport federationSupport;
    private long rskExecutionBlockNumber;

    private NetworkParameters networkParameters;
    private Federation activeFederation;

    void setUp(ActivationConfig.ForBlock activations) {
        networkParameters = bridgeConstants.getBtcParams();
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();

        List<BtcECKey> erpFedPubKeys = federationConstants.getErpFedPubKeysList();
        Federation retiringFederation = P2shErpFederationBuilder.builder()
            .withNetworkParameters(networkParameters)
            .withErpPublicKeys(erpFedPubKeys)
            .build();

        int activeFedCreationBlockNumber = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates()
            + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        activeFederation = P2shErpFederationBuilder.builder()
            .withCreationBlockNumber(activeFedCreationBlockNumber)
            .withMembersBtcPublicKeys(activeFedKeys)
            .withNetworkParameters(networkParameters)
            .withErpPublicKeys(erpFedPubKeys)
            .build();

        FeePerKbSupport feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
            bridgeConstants,
            activations,
            logs
        );

        StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        federationStorageProvider.setOldFederation(retiringFederation);
        federationStorageProvider.setNewFederation(activeFederation);

        // Move the required blocks ahead for the new powpeg to become active
        rskExecutionBlockNumber = activeFederation.getCreationBlockNumber()
            + federationConstants.getFederationActivationAge(activations);
        Block rskExecutionBlock = createRskBlock(rskExecutionBlockNumber);

        FederationSupportBuilder federationSupportBuilder = FederationSupportBuilder.builder();
        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskExecutionBlock)
            .withActivations(activations)
            .build();

        Repository repository = createRepository();
        bridgeStorageProvider = new BridgeStorageProvider(repository, bridgeContractAddress, networkParameters, activations);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(networkParameters, 100, 100);
        btcBlockStore = btcBlockStoreFactory.newInstance(repository, bridgeConstants, bridgeStorageProvider, activations);
        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();

        BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
        bridgeSupport = bridgeSupportBuilder
            .withActivations(activations)
            .withExecutionBlock(rskExecutionBlock)
            .withBridgeConstants(bridgeConstants)
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(rskTxHash);
    }

    private void registerPegin(BtcTransaction pegin) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        PartialMerkleTree pmtWithTransactions = buildPMTAndRecreateChainForTransactionRegistration(
            bridgeStorageProvider,
            bridgeConstants,
            (int) rskExecutionBlockNumber,
            pegin,
            btcBlockStore
        );

        bridgeSupport.registerBtcTransaction(
            rskTx,
            pegin.bitcoinSerialize(),
            (int) rskExecutionBlockNumber,
            pmtWithTransactions.bitcoinSerialize()
        );
        bridgeSupport.save();
    }

    @Test
    void registerBtcTx_legacyPeginWithParseableScriptPubKey_withoutSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(allActivations);
        BtcTransaction pegin = buildLegacyPeginWithParseableScriptPubKey();

        // act
        registerPegin(pegin);

        // assert
        assertPeginWasRegisteredSuccessfully(pegin.getHash());
    }

    @Test
    void registerBtcTx_legacyPeginWithParseableScriptPubKey_withSVPOnGoing_shouldRegisterPegIn() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(allActivations);
        BtcTransaction pegin = buildLegacyPeginWithParseableScriptPubKey();

        // save svp spend tx hash, so it enters the flow that throws exception
        bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

        // act
        registerPegin(pegin);

        // assert
        assertPeginWasRegisteredSuccessfully(pegin.getHash());
    }

    @Test
    void registerBtcTx_legacyPeginWithParseableScriptPubKey_beforeRSKIP305_withSVPOnGoing_shouldThrowISE() throws IOException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(lovellActivations);
        BtcTransaction pegin = buildLegacyPeginWithParseableScriptPubKey();

        // save svp spend tx hash, so it enters the flow that throws exception
        bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

        // act & assert
        assertThrows(IllegalStateException.class, () -> registerPegin(pegin));
        assertPeginWasNotProcessed(pegin.getHash());
    }

    @Test
    void registerBtcTx_legacyPeginWithNonParseableScriptPubKey_withoutSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(allActivations);
        BtcTransaction pegin = buildLegacyPeginWithNonParseableScriptPubKey();

        // act
        registerPegin(pegin);

        // assert
        assertPeginWasRegisteredSuccessfully(pegin.getHash());
    }

    @Test
    void registerBtcTx_legacyPeginWithNonParseableScriptPubKey_withSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(allActivations);
        BtcTransaction pegin = buildLegacyPeginWithNonParseableScriptPubKey();
        // save svp spend tx hash
        bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

        // act
        registerPegin(pegin);

        // assert
        assertPeginWasRegisteredSuccessfully(pegin.getHash());
    }

    @Test
    void registerBtcTx_peginV1WithNonParseableScriptPubKey_withoutSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(allActivations);
        BtcTransaction pegin = buildPeginV1WithNonParseableScriptPubKey();

        // act
        registerPegin(pegin);

        // assert
        assertPeginWasRegisteredSuccessfully(pegin.getHash());
    }

    @Test
    void registerBtcTx_peginV1WithNonParseableScriptPubKey_withSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(allActivations);
        BtcTransaction pegin = buildPeginV1WithNonParseableScriptPubKey();
        // save svp spend tx hash
        bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

        // act
        registerPegin(pegin);

        // assert
        assertPeginWasRegisteredSuccessfully(pegin.getHash());
    }

    @Test
    void registerBtcTx_peginV1WithParseableScriptPubKey_withSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(allActivations);
        BtcTransaction pegin = buildPeginV1WithParseableScriptPubKey();
        // save svp spend tx hash
        bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

        // act
        registerPegin(pegin);

        //assert
        assertPeginWasRegisteredSuccessfully(pegin.getHash());
    }

    @Test
    void registerBtcTx_peginV1WithParseableScriptPubKey_beforeRSKIP305_withSVPOnGoing_shouldThrowISE() throws IOException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(lovellActivations);
        BtcTransaction pegin = buildPeginV1WithParseableScriptPubKey();
        // save svp spend tx hash
        bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

        // act & assert
        assertThrows(IllegalStateException.class, () -> registerPegin(pegin));
        assertPeginWasNotProcessed(pegin.getHash());
    }

    @Test
    void registerBtcTx_peginV1WithParseableScriptPubKey_withoutSVPOnGoing_shouldRegisterPegin() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        bridgeConstants = BridgeMainNetConstants.getInstance();
        setUp(allActivations);
        BtcTransaction pegin = buildPeginV1WithParseableScriptPubKey();

        // act
        registerPegin(pegin);

        // assert
        assertPeginWasRegisteredSuccessfully(pegin.getHash());
    }

    // testnet real pegin v1 that had a parseable script pub key
    // and was malformed (with an incorrect op return)
    @Test
    void registerBtcTx_testnetRealPeginV1_withSVPOnGoing_beforeRSKIP305_shouldThrowISE() throws IOException {
        // arrange
        bridgeConstants = BridgeTestNetConstants.getInstance();
        setUp(lovellActivations);
        // save svp spend tx hash, so it enters the flow that throws exception as it happens in reality
        bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

        // act & assert
        assertThrows(IllegalStateException.class, () -> registerPegin(testnetRealPegin));
        assertPeginWasNotProcessed(testnetRealPegin.getHash());
    }

    @Test
    void registerBtcTx_testnetRealPeginV1_withoutSVPOnGoing_shouldRegisterAndRefundPegin() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        bridgeConstants = BridgeTestNetConstants.getInstance();
        setUp(allActivations);

        // act
        registerPegin(testnetRealPegin);

        // assert
        assertTransactionWasProcessed(testnetRealPegin.getHash());
        assertRefundWasCreated();
        assertUtxosSize(0);
    }

    private BtcTransaction buildLegacyPeginWithParseableScriptPubKey() {
        BtcTransaction pegin = new BtcTransaction(networkParameters);
        pegin.addInput(BitcoinTestUtils.createHash(1), 0, parseableScriptPubKey);

        pegin.addOutput(Coin.COIN, activeFederation.getAddress());

        return pegin;
    }

    private BtcTransaction buildLegacyPeginWithNonParseableScriptPubKey() {
        BtcTransaction pegin = new BtcTransaction(networkParameters);
        pegin.addInput(BitcoinTestUtils.createHash(1), 0, nonParseableScriptPubKey);

        pegin.addOutput(Coin.COIN, activeFederation.getAddress());

        return pegin;
    }

    private BtcTransaction buildPeginV1WithParseableScriptPubKey() {
        BtcTransaction pegin = new BtcTransaction(networkParameters);
        pegin.addInput(BitcoinTestUtils.createHash(1), 0, parseableScriptPubKey);

        pegin.addOutput(Coin.ZERO, opReturnScript);
        pegin.addOutput(Coin.COIN, activeFederation.getAddress());

        return pegin;
    }

    private BtcTransaction buildPeginV1WithNonParseableScriptPubKey() {
        BtcTransaction pegin = new BtcTransaction(networkParameters);
        pegin.addInput(BitcoinTestUtils.createHash(1), 0, nonParseableScriptPubKey);

        pegin.addOutput(Coin.ZERO, opReturnScript);
        pegin.addOutput(Coin.COIN, activeFederation.getAddress());

        return pegin;
    }

    private void assertPeginWasRegisteredSuccessfully(Sha256Hash peginTxHash) throws IOException {
        assertTransactionWasProcessed(peginTxHash);
        assertRefundWasNotCreated();
        assertUtxosSize(1);
    }

    private void assertTransactionWasProcessed(Sha256Hash transactionHash) throws IOException {
        Optional<Long> rskBlockHeightAtWhichBtcTxWasProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(transactionHash);
        assertTrue(rskBlockHeightAtWhichBtcTxWasProcessed.isPresent());

        assertEquals(rskExecutionBlockNumber, rskBlockHeightAtWhichBtcTxWasProcessed.get());
    }

    private void assertRefundWasCreated() throws IOException {
        assertEquals(1, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    private void assertPeginWasNotProcessed(Sha256Hash peginTxHash) throws IOException {
        assertTransactionWasNotProcessed(peginTxHash);
        assertRefundWasNotCreated();
        assertUtxosSize(0);
    }

    private void assertRefundWasNotCreated() throws IOException {
        assertEquals(0, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    private void assertTransactionWasNotProcessed(Sha256Hash transactionHash) throws IOException {
        Optional<Long> rskBlockHeightAtWhichBtcTxWasProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(transactionHash);
        assertFalse(rskBlockHeightAtWhichBtcTxWasProcessed.isPresent());
    }

    private void assertUtxosSize(int expectedSize) {
        assertEquals(expectedSize, federationSupport.getActiveFederationBtcUTXOs().size());
    }
}

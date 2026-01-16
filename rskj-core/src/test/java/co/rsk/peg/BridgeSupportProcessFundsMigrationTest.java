package co.rsk.peg;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static co.rsk.peg.PegTestUtils.BTC_TX_LEGACY_VERSION;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createUTXO;
import static co.rsk.peg.federation.FederationStorageIndexKey.OLD_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BridgeSupportProcessFundsMigrationTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final FederationConstants federationConstants = bridgeMainnetConstants.getFederationConstants();
    private static final NetworkParameters networkParams = bridgeMainnetConstants.getBtcParams();
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    private static final StandardMultiSigFederationBuilder standardMultiSigFederationBuilder = StandardMultiSigFederationBuilder.builder();
    public static final ECKey arbitrarySenderKey = RskTestUtils.getEcKeyFromSeed("sender");
    private StorageAccessor bridgeStorageAccessor;
    private FeePerKbSupport feePerKbSupport;
    private FederationStorageProviderImpl federationStorageProvider;
    private BridgeStorageProvider bridgeStorageProvider;
    private BridgeEventLogger bridgeEventLogger;

    private static Stream<Arguments> processFundMigrationArgsProvider() {
        BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        BridgeTestNetConstants bridgeTestNetConstants = BridgeTestNetConstants.getInstance();

        ActivationConfig.ForBlock wasabiActivations = ActivationConfigsForTest.wasabi100().forBlock(0);
        Stream<Arguments> wasabiTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, wasabiActivations, false),
            Arguments.of(bridgeTestNetConstants, wasabiActivations, true),
            Arguments.of(bridgeMainNetConstants, wasabiActivations, false),
            Arguments.of(bridgeMainNetConstants, wasabiActivations, true)
        );

        ActivationConfig.ForBlock papyrusActivations = ActivationConfigsForTest.papyrus200().forBlock(0);
        Stream<Arguments> papyrusTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, papyrusActivations, false),
            Arguments.of(bridgeTestNetConstants, papyrusActivations, true),
            Arguments.of(bridgeMainNetConstants, papyrusActivations, false),
            Arguments.of(bridgeMainNetConstants, papyrusActivations, true)
        );

        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0);
        Stream<Arguments> irisTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, irisActivations, false),
            Arguments.of(bridgeTestNetConstants, irisActivations, true),
            Arguments.of(bridgeMainNetConstants, irisActivations, false),
            Arguments.of(bridgeMainNetConstants, irisActivations, true)
        );

        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0);
        Stream<Arguments> hopTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, hopActivations, false),
            Arguments.of(bridgeTestNetConstants, hopActivations, true),
            Arguments.of(bridgeMainNetConstants, hopActivations, false),
            Arguments.of(bridgeMainNetConstants, hopActivations, true)
        );

        ActivationConfig.ForBlock hop401Activations = ActivationConfigsForTest.hop401().forBlock(0);
        Stream<Arguments> hop401Tests = Stream.of(
            Arguments.of(bridgeTestNetConstants, hop401Activations, false),
            Arguments.of(bridgeTestNetConstants, hop401Activations, true),
            Arguments.of(bridgeMainNetConstants, hop401Activations, false),
            Arguments.of(bridgeMainNetConstants, hop401Activations, true)
        );

        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        Stream<Arguments> fingerrootTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, fingerrootActivations, false),
            Arguments.of(bridgeTestNetConstants, fingerrootActivations, true),
            Arguments.of(bridgeMainNetConstants, fingerrootActivations, false),
            Arguments.of(bridgeMainNetConstants, fingerrootActivations, true)
        );

        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);
        Stream<Arguments> arrowheadTests = Stream.of(
            Arguments.of(bridgeTestNetConstants, tbdActivations, false),
            Arguments.of(bridgeTestNetConstants, tbdActivations, true),
            Arguments.of(bridgeMainNetConstants, tbdActivations, false),
            Arguments.of(bridgeMainNetConstants, tbdActivations, true)
        );

        return Stream.of(
            wasabiTests,
            papyrusTests,
            irisTests,
            hopTests,
            hop401Tests,
            fingerrootTests,
            arrowheadTests
        ).flatMap(Function.identity());
    }

    @BeforeEach
    void setUp() {
        feePerKbSupport = mock(FeePerKbSupport.class);

        bridgeEventLogger = mock(BridgeEventLogger.class);
        bridgeStorageProvider = new BridgeStorageProvider(
            createRepository(),
            PrecompiledContracts.BRIDGE_ADDR,
            networkParams,
            activations
        );
        bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
    }

    @Test
    void processFundsMigration_withRetiringGenesisFederationAndNewMultisigFederation_inMigrationAge_shouldMigrateFunds() throws IOException {
        // Arrange
        long newFederationCreationBlockNumber = 5L;
        Federation newFederation = standardMultiSigFederationBuilder
            .withCreationTime(Instant.EPOCH)
            .withCreationBlockNumber(newFederationCreationBlockNumber)
            .withNetworkParameters(networkParams)
            .build();

        Federation retiringFederation = FederationTestUtils.getGenesisFederation(federationConstants);
        int numberOfUtxos = 1;
        int valuePerOutput = 150_000;
        List<UTXO> retiringFederationUtxos = generateRetiringFederationUTXOs(retiringFederation.getAddress(), numberOfUtxos, valuePerOutput);

        federationStorageProvider.setOldFederation(retiringFederation);
        federationStorageProvider.setNewFederation(newFederation);

        Block rskCurrentBlock = getBlockInMigrationAge(newFederationCreationBlockNumber);
        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);
        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(bridgeStorageProvider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFeePerKbSupport(feePerKbSupport)
            .withFederationSupport(federationSupport)
            .build();

        // Act
        callUpdateCollections(bridgeSupport);

        // Assert
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingConfirmation = bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntriesWithHash();
        assertEquals(1, pegoutsWaitingConfirmation.size());

        PegoutsWaitingForConfirmations.Entry pegoutWaitingForConfirmation = pegoutsWaitingConfirmation.iterator().next();
        BtcTransaction btcTransaction = pegoutWaitingForConfirmation.getBtcTransaction();
        assertEquals(1, btcTransaction.getOutputs().size());

        TransactionOutput output = btcTransaction.getOutput(0);
        Coin fees = btcTransaction.getFee();
        Coin outputValue = output.getValue();
        Coin totalValueSent = outputValue.add(fees);
        Coin oldFederationUtxosValue = calculateUtxosTotalValue(retiringFederationUtxos);
        assertEquals(totalValueSent, oldFederationUtxosValue);

        Address actualMigrationTransactionDestination = output.getAddressFromP2SH(networkParams);
        Address expectedMigrationTransactionDestination = newFederation.getAddress();
        assertEquals(actualMigrationTransactionDestination, expectedMigrationTransactionDestination);

        assertTheReleaseOutpointValuesAreCorrect(btcTransaction, numberOfUtxos, retiringFederationUtxos);

        // TODO(juli): // provider.setPegoutTxSigHash(pegoutTxSigHash.get()); <- idem
    }

    private Coin calculateUtxosTotalValue(List<UTXO> retiringFederationUtxos) {
        return retiringFederationUtxos
            .stream()
            .map(UTXO::getValue)
            .reduce(Coin.ZERO, Coin::add);
    }

    private void assertTheReleaseOutpointValuesAreCorrect(BtcTransaction btcTransaction, int numberOfUtxos, List<UTXO> retiringFederationUtxos) {
        Optional<List<Coin>> releaseOutpointsValues = bridgeStorageProvider.getReleaseOutpointsValues(btcTransaction.getHash());
        assertTrue(releaseOutpointsValues.isPresent());
        List<Coin> outpointsValues = releaseOutpointsValues.get();
        assertEquals(numberOfUtxos, outpointsValues.size());
        for (int i = 0; i < numberOfUtxos; i++) {
            assertEquals(retiringFederationUtxos.get(i).getValue(), outpointsValues.get(i));
        }
    }

    private List<UTXO> generateRetiringFederationUTXOs(Address retiringFederationAddress, int numberOfUtxos, int valuePerOutput) {
        List<UTXO> retiringFederationUtxos = getOldFederationUtxos(numberOfUtxos, valuePerOutput, retiringFederationAddress);
        byte[] serializeUTXOList = BridgeSerializationUtils.serializeUTXOList(retiringFederationUtxos);
        bridgeStorageAccessor.saveToRepository(OLD_FEDERATION_BTC_UTXOS_KEY.getKey(), serializeUTXOList);
        return retiringFederationUtxos;
    }

    private Block getBlockInMigrationAge(long newFederationCreationBlockNumber) {
        Block block = mock(Block.class);
        long federationActivationAge = federationConstants.getFederationActivationAge(activations);
        long federationInMigrationAgeHeight = newFederationCreationBlockNumber + federationActivationAge +
            federationConstants.getFundsMigrationAgeSinceActivationBegin() + 1;
        when(block.getNumber()).thenReturn(federationInMigrationAgeHeight);
        return block;
    }

    private void callUpdateCollections(BridgeSupport bridgeSupport) throws IOException {
        Transaction updateCollectionsTx = buildUpdateCollectionsTransaction();
        updateCollectionsTx.sign(arbitrarySenderKey.getPrivKeyBytes());
        bridgeSupport.updateCollections(updateCollectionsTx);
    }

    private static List<UTXO> getOldFederationUtxos(int numberOfUtxos, int valuePerOutput, Address oldFederationAddress) {
        int index = 0;
        List<UTXO> oldFederationUtxos = new ArrayList<>();
        for (int i = 0; i < numberOfUtxos; i++) {
            int nHash = i + 1;
            Coin value = Coin.valueOf(valuePerOutput);
            oldFederationUtxos.add(createUTXO(nHash, index, value, oldFederationAddress));
        }
        return oldFederationUtxos;
    }

    public static Repository createRepository() {
        return new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));
    }

    @ParameterizedTest
    @MethodSource("processFundMigrationArgsProvider")
    void test_processFundsMigration(
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations,
        boolean inMigrationAge
    ) throws IOException {
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);
        Federation oldFederation = FederationTestUtils.getGenesisFederation(bridgeConstants.getFederationConstants());

        long federationActivationAge = federationConstants.getFederationActivationAge(activations); // cambia si es FINGERROOT, yo lo dejaria activo total lo que cambia es el activation age
        long federationCreationBlockNumber = 5L;
        long federationInMigrationAgeHeight = federationCreationBlockNumber + federationActivationAge +
            federationConstants.getFundsMigrationAgeSinceActivationBegin() + 1;
        long federationPastMigrationAgeHeight = federationCreationBlockNumber +
            federationActivationAge +
            federationConstants.getFundsMigrationAgeSinceActivationEnd(activations) + 1;

        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            federationCreationBlockNumber,
            bridgeConstants.getBtcParams()
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getOldFederation(federationConstants, activations)).thenReturn(oldFederation);
        when(federationStorageProvider.getNewFederation(federationConstants, activations)).thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();

        long updateCollectionsCallHeight = inMigrationAge ? federationInMigrationAgeHeight : federationPastMigrationAgeHeight;
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(updateCollectionsCallHeight, 1);

        FeePerKbSupport feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activations) // solo para decodear non-standard
            .build();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFeePerKbSupport(feePerKbSupport)
            .withFederationSupport(federationSupport)
            .build();

        List<UTXO> sufficientUTXOsForMigration = new ArrayList<>();
        sufficientUTXOsForMigration.add(PegTestUtils.createUTXO(
            0,
            0,
            Coin.COIN,
            oldFederation.getAddress()
        ));
        when(federationStorageProvider.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration);

        Transaction updateCollectionsTx = buildUpdateCollectionsTransaction();
        ECKey senderKey = arbitrarySenderKey;
        updateCollectionsTx.sign(senderKey.getPrivKeyBytes());
        bridgeSupport.updateCollections(updateCollectionsTx);

        assertEquals(activations.isActive(ConsensusRule.RSKIP146)? 0 : 1, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        assertEquals(activations.isActive(ConsensusRule.RSKIP146)? 1 : 0, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        assertTrue(sufficientUTXOsForMigration.isEmpty());
        if (inMigrationAge){
            verify(federationStorageProvider, never()).setOldFederation(null);
        } else {
            verify(federationStorageProvider, times(1)).setOldFederation(null);
        }

        if (activations.isActive(ConsensusRule.RSKIP146)) { // Papyrus
            // Should have been logged with the migrated UTXO
            PegoutsWaitingForConfirmations.Entry entry = (PegoutsWaitingForConfirmations.Entry) provider.getPegoutsWaitingForConfirmations()
                .getEntriesWithHash()
                .toArray()[0];
            verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(
                updateCollectionsTx.getHash().getBytes(),
                entry.getBtcTransaction(),
                Coin.COIN
            );

            if (activations.isActive(ConsensusRule.RSKIP376)){ // Arrowhead
                assertEquals(BTC_TX_VERSION_2, entry.getBtcTransaction().getVersion());
            } else {
                assertEquals(BTC_TX_LEGACY_VERSION, entry.getBtcTransaction().getVersion());
            }
        } else {
            verify(bridgeEventLogger, never()).logReleaseBtcRequested(
                any(byte[].class),
                any(BtcTransaction.class),
                any(Coin.class)
            );
        }
    }

    private Transaction buildUpdateCollectionsTransaction() {
        final String TO_ADDRESS = "0000000000000000000000000000000000000006";
        final BigInteger dustAmount = new BigInteger("1");
        final BigInteger nonce = new BigInteger("0");
        final BigInteger gasPrice = new BigInteger("100");
        final BigInteger gasLimit = new BigInteger("1000");
        final String DATA = "80af2871";

        return Transaction
            .builder()
            .nonce(nonce)
            .gasPrice(gasPrice)
            .gasLimit(gasLimit)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(dustAmount)
            .build();
    }
}

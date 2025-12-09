package co.rsk.peg;

import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeStorageIndexKey.PEGOUTS_WAITING_FOR_SIGNATURES;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.PegTestUtils.*;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.*;
import static co.rsk.peg.federation.FederationTestUtils.REGTEST_FEDERATION_PRIVATE_KEYS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.*;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.UnionBridgeSupport;
import co.rsk.peg.utils.*;
import co.rsk.peg.whitelist.WhitelistSupport;
import co.rsk.test.builders.BridgeSupportBuilder;

import co.rsk.test.builders.FederationSupportBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import co.rsk.test.builders.MigrationTransactionBuilder;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BridgeSupportAddSignatureTest {
    private static final RskAddress bridgeAddress = PrecompiledContracts.BRIDGE_ADDR;

    private final BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
    private final NetworkParameters btcRegTestParams = bridgeRegTestConstants.getBtcParams();

    private final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private final Instant creationTime = Instant.ofEpochMilli(1000L);
    private final long creationBlockNumber = 0L;

    private final ActivationConfig.ForBlock activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
    private final ActivationConfig.ForBlock activationsAfterForks = ActivationConfigsForTest.all().forBlock(0);
    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
    private WhitelistSupport whitelistSupport;
    private LockingCapSupport lockingCapSupport;
    private UnionBridgeSupport unionBridgeSupport;

    @BeforeEach
    void setUpOnEachTest() {
        whitelistSupport = mock(WhitelistSupport.class);
        lockingCapSupport = mock(LockingCapSupport.class);
        unionBridgeSupport = mock(UnionBridgeSupport.class);
    }

    @Test
    void addSignature_fedPubKey_belongs_to_active_federation() throws Exception {
        //Setup
        FederationSupport mockFederationSupport = mock(FederationSupport.class);

        // Creates new federation
        List<BtcECKey> activeFedKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        activeFedKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<FederationMember> activeFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(activeFedKeys);
        FederationArgs activeFedArgs = new FederationArgs(
            activeFedMembers,
            creationTime,
            creationBlockNumber,
            btcRegTestParams
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(activeFedArgs);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FeePerKbSupport feePerKbSupport = mock(FeePerKbSupport.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeRegTestConstants,
            provider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            new Context(bridgeRegTestConstants.getBtcParams()),
            feePerKbSupport,
            whitelistSupport,
            mockFederationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            null,
            null,
            null
        );

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        bridgeSupport.addSignature(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            null,
            createHash3(1)
        );

        verify(provider, never()).getPegoutsWaitingForSignatures();
    }

    @Test
    void addSignature_fedPubKey_belongs_to_retiring_federation() throws Exception {
        //Setup
        FederationSupport mockFederationSupport = mock(FederationSupport.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FeePerKbSupport feePerKbSupport = mock(FeePerKbSupport.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeRegTestConstants,
            provider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            new Context(bridgeRegTestConstants.getBtcParams()),
            feePerKbSupport,
            whitelistSupport,
            mockFederationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            null,
            null,
            null
        );

        // Creates retiring federation
        List<BtcECKey> retiringFedKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")));
        retiringFedKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<FederationMember> retiringFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(retiringFedKeys);
        FederationArgs retiringFedArgs = new FederationArgs(
            retiringFedMembers,
            creationTime,
            creationBlockNumber,
            btcRegTestParams
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(retiringFedArgs);
        Optional<Federation> retiringFederation = Optional.of(federation);

        // Creates active federation
        List<BtcECKey> activeFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        activeFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        List<FederationMember> activeFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys);
        FederationArgs activeFedArgs = new FederationArgs(
            activeFedMembers,
            creationTime,
            creationBlockNumber,
            btcRegTestParams
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(activeFedArgs);

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(mockFederationSupport.getRetiringFederation()).thenReturn(retiringFederation);
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        bridgeSupport.addSignature(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            null,
            createHash3(1)
        );

        verify(provider, never()).getPegoutsWaitingForSignatures();
    }

    @Test
    void addSignature_fedPubKey_no_belong_to_retiring_or_active_federation() throws Exception {
        //Setup
        FederationSupport mockFederationSupport = mock(FederationSupport.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FeePerKbSupport feePerKbSupport = mock(FeePerKbSupport.class);
        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeRegTestConstants,
            provider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            new Context(bridgeRegTestConstants.getBtcParams()),
            feePerKbSupport,
            whitelistSupport,
            mockFederationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            null,
            null,
            null
        );

        // Creates retiring federation
        List<BtcECKey> retiringFedKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiringFedKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        List<FederationMember> retiringFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(retiringFedKeys);
        FederationArgs retiringFedArgs = new FederationArgs(
            retiringFedMembers,
            creationTime,
            creationBlockNumber,
            btcRegTestParams
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(retiringFedArgs);
        Optional<Federation> retiringFederation = Optional.of(federation);

        // Creates active federation
        List<BtcECKey> activeFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        activeFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<FederationMember> activeFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys);
        FederationArgs activeFedArgs = new FederationArgs(
            activeFedMembers,
            creationTime,
            creationBlockNumber,
            btcRegTestParams
        );
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(activeFedArgs);

        when(mockFederationSupport.getActiveFederation()).thenReturn(activeFederation);
        when(mockFederationSupport.getRetiringFederation()).thenReturn(retiringFederation);

        bridgeSupport.addSignature(
            BtcECKey.fromPrivate(Hex.decode("fa05")),
            null,
            createHash3(1)
        );

        verify(provider, never()).getPegoutsWaitingForSignatures();
    }

    @Test
    void addSignature_fedPubKey_no_belong_to_active_federation_no_existing_retiring_fed() throws Exception {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationSupport federationSupport = createDefaultFederationSupport(bridgeRegTestConstants.getFederationConstants());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withFederationSupport(federationSupport)
            .withEventLogger(mock(BridgeEventLogger.class))
            .build();

        bridgeSupport.addSignature(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            null,
            createHash3(1)
        );

        verify(provider, never()).getPegoutsWaitingForSignatures();
    }

    @Test
    void addSignatureToMissingTransaction() throws Exception {
        // Federation is the genesis federation ATM
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeRegTestConstants.getFederationConstants());
        Repository repository = createRepository();

        BridgeStorageProvider providerForSupport = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            btcRegTestParams,
            activationsBeforeForks
        );
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(new BridgeStorageAccessorImpl(repository));
        FederationSupport federationSupport = createFederationSupport(bridgeRegTestConstants.getFederationConstants(), federationStorageProvider, mock(Block.class), activationsBeforeForks);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(providerForSupport)
            .withFederationSupport(federationSupport)
            .withRepository(repository)
            .build();

        bridgeSupport.addSignature(
            genesisFederation.getBtcPublicKeys().get(0),
            null,
            createHash3(1)
        );
        bridgeSupport.save();

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeAddress,
            btcRegTestParams,
            activationsBeforeForks
        );

        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
    }

    @Test
    void addSignatureFromInvalidFederator() throws Exception {

        Repository repository = createRepository();
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(repository, bridgeAddress, btcRegTestParams, activationsBeforeForks);
        FederationSupport federationSupport = createDefaultFederationSupport(bridgeRegTestConstants.getFederationConstants());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(bridgeStorageProvider)
            .withFederationSupport(federationSupport)
            .withRepository(repository)
            .build();

        bridgeSupport.addSignature(
            new BtcECKey(),
            null,
            createHash3(1)
        );
        bridgeSupport.save();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeAddress,
            btcRegTestParams, activationsBeforeForks);

        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
    }

    @Test
    void addSignatureWithInvalidSignature() throws Exception {
        List<BtcECKey> list = new ArrayList<>();
        list.add(new BtcECKey());
        addSignatureFromValidFederator(list, 1, true, false, "InvalidParameters");
    }

    @Test
    void addSignatureWithLessSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 0, true, false, "InvalidParameters");
    }

    @Test
    void addSignatureWithMoreSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 2, true, false, "InvalidParameters");
    }

    @Test
    void addSignatureNonCanonicalSignature() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 1, false, false, "InvalidParameters");
    }

    private void test_addSignature_EventEmitted(boolean rskip326Active, boolean useValidSignature, int wantedNumberOfInvocations, boolean shouldSignTwice) throws Exception {
        // Setup
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeRegTestConstants.getFederationConstants());
        Repository track = createRepository().startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, bridgeAddress, btcRegTestParams, activationsBeforeForks);

        // Build prev btc tx
        BtcTransaction prevTx = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut = new TransactionOutput(btcRegTestParams, prevTx, Coin.FIFTY_COINS, genesisFederation.getAddress());
        prevTx.addOutput(prevOut);

        // Build btc tx to be signed
        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addInput(prevOut).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(genesisFederation));
        TransactionOutput output = new TransactionOutput(btcRegTestParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcRegTestParams));
        btcTx.addOutput(output);

        // Save btc tx to be signed
        final Keccak256 rskTxHash = createHash3(1);
        provider.getPegoutsWaitingForSignatures().put(rskTxHash, btcTx);
        provider.save();
        track.commit();

        // Setup BridgeSupport
        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(rskip326Active);

        FederationSupport federationSupport = createDefaultFederationSupport(bridgeRegTestConstants.getFederationConstants());
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withFederationSupport(federationSupport)
            .withRepository(track)
            .withEventLogger(eventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withBtcBlockStoreFactory(null)
            .withActivations(activations)
            .build();

        int indexOfKeyToSignWith = 0;

        BtcECKey privateKeyToSignWith = REGTEST_FEDERATION_PRIVATE_KEYS.get(indexOfKeyToSignWith);

        List<byte[]> derEncodedSigs;

        if (useValidSignature) {
            Script inputScript = btcTx.getInputs().get(0).getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            Sha256Hash sigHash = btcTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
            BtcECKey.ECDSASignature sig = privateKeyToSignWith.sign(sigHash);
            derEncodedSigs = Collections.singletonList(sig.encodeToDER());
        } else {
            byte[] malformedSignature = new byte[70];
            for (int i = 0; i < malformedSignature.length; i++) {
                malformedSignature[i] = (byte) i;
            }
            derEncodedSigs = Collections.singletonList(malformedSignature);
        }

        BtcECKey federatorPubKey = bridgeRegTestConstants.getFederationConstants().getGenesisFederationPublicKeys().get(indexOfKeyToSignWith);
        FederationMember federationMember = FederationTestUtils.getFederationMemberWithKey(federatorPubKey);
        bridgeSupport.addSignature(federatorPubKey, derEncodedSigs, rskTxHash);
        if (shouldSignTwice) {
            bridgeSupport.addSignature(federatorPubKey, derEncodedSigs, rskTxHash);
        }

        verify(eventLogger, times(wantedNumberOfInvocations)).logAddSignature(federationMember, btcTx, rskTxHash.getBytes());
    }

    @Test
    void addSignature_calledTwiceWithSameFederatorPreRSKIP326_emitEventTwice() throws Exception {
        test_addSignature_EventEmitted(false, true, 2, true);
    }

    @Test
    void addSignature_calledTwiceWithSameFederatorPostRSKIP326_emitEventOnlyOnce() throws Exception {
        test_addSignature_EventEmitted(true, true, 1, true);
    }

    @Test
    void addSignatureCreateEventLog_preRSKIP326() throws Exception {
        test_addSignature_EventEmitted(false, true, 1, false);
    }

    @Test
    void addSignature_invalidSignatureBeforeRSKIP326_eventStillEmitted() throws Exception {
        test_addSignature_EventEmitted(false, false, 1, false);
    }

    @Test
    void addSignature_afterRSKIP326_eventEmitted() throws Exception {
        test_addSignature_EventEmitted(true, true, 1, false);
    }

    @Test
    void addSignature_invalidSignatureAfterRSKIP326_noEventEmitted() throws Exception {
        test_addSignature_EventEmitted(true, false, 0, false);
    }

    @Test
    void addSignatureTwice() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 1, true, true, "PartiallySigned");
    }

    @Test
    void addSignatureOneSignature() throws Exception {
        List<BtcECKey> keys = Collections.singletonList(REGTEST_FEDERATION_PRIVATE_KEYS.get(0));
        addSignatureFromValidFederator(keys, 1, true, false, "PartiallySigned");
    }

    @Test
    void addSignatureTwoSignatures() throws Exception {
        List<BtcECKey> federatorPrivateKeys = REGTEST_FEDERATION_PRIVATE_KEYS;
        List<BtcECKey> keys = Arrays.asList(federatorPrivateKeys.get(0), federatorPrivateKeys.get(1));
        addSignatureFromValidFederator(keys, 1, true, false, "FullySigned");
    }

    @Test
    void addSignatureMultipleInputsPartiallyValid() throws Exception {
        // Federation is the genesis federation ATM
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeRegTestConstants.getFederationConstants());
        Repository repository = createRepository();

        final Keccak256 keccak256 = createHash3(1);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeAddress,
            btcRegTestParams, activationsBeforeForks);

        BtcTransaction prevTx1 = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut1 = new TransactionOutput(btcRegTestParams, prevTx1, Coin.FIFTY_COINS, genesisFederation.getAddress());
        prevTx1.addOutput(prevOut1);
        BtcTransaction prevTx2 = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut2 = new TransactionOutput(btcRegTestParams, prevTx1, Coin.FIFTY_COINS, genesisFederation.getAddress());
        prevTx2.addOutput(prevOut2);
        BtcTransaction prevTx3 = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut3 = new TransactionOutput(btcRegTestParams, prevTx1, Coin.FIFTY_COINS, genesisFederation.getAddress());
        prevTx3.addOutput(prevOut3);

        BtcTransaction t = new BtcTransaction(btcRegTestParams);
        TransactionOutput output = new TransactionOutput(btcRegTestParams, t, Coin.COIN, new BtcECKey().toAddress(btcRegTestParams));
        t.addOutput(output);
        t.addInput(prevOut1).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(genesisFederation));
        t.addInput(prevOut2).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(genesisFederation));
        t.addInput(prevOut3).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(genesisFederation));
        provider.getPegoutsWaitingForSignatures().put(keccak256, t);
        provider.save();

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BrigeEventLoggerLegacyImpl(bridgeRegTestConstants, activations, logs);

        FederationSupport federationSupport = createDefaultFederationSupport(bridgeRegTestConstants.getFederationConstants());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withFederationSupport(federationSupport)
            .withRepository(repository)
            .withEventLogger(eventLogger)
            .build();

        // Generate valid signatures for inputs
        List<byte[]> derEncodedSigsFirstFed = new ArrayList<>();
        List<byte[]> derEncodedSigsSecondFed = new ArrayList<>();
        BtcECKey privateKeyOfFirstFed = REGTEST_FEDERATION_PRIVATE_KEYS.get(0);
        BtcECKey privateKeyOfSecondFed = REGTEST_FEDERATION_PRIVATE_KEYS.get(1);

        BtcECKey.ECDSASignature lastSig = null;
        for (int i = 0; i < 3; i++) {
            Script inputScript = t.getInput(i).getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            Sha256Hash sighash = t.hashForSignature(i, redeemScript, BtcTransaction.SigHash.ALL, false);

            // Sign the last input with a random key
            // but keep the good signature for a subsequent call
            BtcECKey.ECDSASignature sig = privateKeyOfFirstFed.sign(sighash);
            if (i == 2) {
                lastSig = sig;
                sig = new BtcECKey().sign(sighash);
            }
            derEncodedSigsFirstFed.add(sig.encodeToDER());
            derEncodedSigsSecondFed.add(privateKeyOfSecondFed.sign(sighash).encodeToDER());
        }

        // Sign with two valid signatures and one invalid signature
        bridgeSupport.addSignature(findPublicKeySignedBy(genesisFederation.getBtcPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256);
        bridgeSupport.save();

        // Sign with two valid signatures and one malformed signature
        byte[] malformedSignature = new byte[lastSig.encodeToDER().length];
        for (int i = 0; i < malformedSignature.length; i++) {
            malformedSignature[i] = (byte) i;
        }
        derEncodedSigsFirstFed.set(2, malformedSignature);
        bridgeSupport.addSignature(findPublicKeySignedBy(genesisFederation.getBtcPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256);
        bridgeSupport.save();

        // Sign with fully valid signatures for same federator
        derEncodedSigsFirstFed.set(2, lastSig.encodeToDER());
        bridgeSupport.addSignature(findPublicKeySignedBy(genesisFederation.getBtcPublicKeys(), privateKeyOfFirstFed), derEncodedSigsFirstFed, keccak256);
        bridgeSupport.save();

        // Sign with second federation
        bridgeSupport.addSignature(findPublicKeySignedBy(genesisFederation.getBtcPublicKeys(), privateKeyOfSecondFed), derEncodedSigsSecondFed, keccak256);
        bridgeSupport.save();

        provider = new BridgeStorageProvider(repository, bridgeAddress, btcRegTestParams, activationsBeforeForks);

        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertThat(logs, is(not(empty())));
        assertThat(logs, hasSize(5));
        LogInfo releaseTxEvent = logs.get(4);
        assertThat(releaseTxEvent.getTopics(), hasSize(1));
        assertThat(releaseTxEvent.getTopics(), hasItem(Bridge.RELEASE_BTC_TOPIC));
        BtcTransaction releaseTx = new BtcTransaction(btcRegTestParams, ((RLPList) RLP.decode2(releaseTxEvent.getData()).get(0)).get(1).getRLPData());
        // Verify all inputs fully signed
        for (int i = 0; i < releaseTx.getInputs().size(); i++) {
            Script retrievedScriptSig = releaseTx.getInput(i).getScriptSig();
            assertEquals(4, retrievedScriptSig.getChunks().size());
            assertTrue(Objects.requireNonNull(retrievedScriptSig.getChunks().get(1).data).length > 0);
            assertTrue(Objects.requireNonNull(retrievedScriptSig.getChunks().get(2).data).length > 0);
        }
    }

    private static Stream<Arguments> addSignatureArgProvider() {
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock arrowheadActivations = ActivationConfigsForTest.arrowhead600().forBlock(0);

        BtcECKey btcKey = BtcECKey.fromPrivate(Hex.decode("000000000000000000000000000000000000000000000000000000000000015e")); // 350
        ECKey rskKey = ECKey.fromPrivate(Hex.decode("000000000000000000000000000000000000000000000000000000000000015f")); // 351
        ECKey mstKey = ECKey.fromPrivate(Hex.decode("0000000000000000000000000000000000000000000000000000000000000160")); // 352

        String addressDerivedFromBtcKey = "dbc29273d4de3d5645e308c7e629d28d4499b3d3";
        String addressDerivedFromRskKey = "74891a05ad4d7ec87c1cffe9bd00bb4e1382b586";

        FederationMember singleKeyFedMember = new FederationMember(
            btcKey,
            ECKey.fromPublicOnly(btcKey.getPubKey()),
            ECKey.fromPublicOnly(btcKey.getPubKey())
        );

        FederationMember multiKeyFedMember = new FederationMember(
            btcKey,
            rskKey,
            mstKey
        );

        return Stream.of(
            Arguments.of(fingerrootActivations, singleKeyFedMember, btcKey, addressDerivedFromBtcKey),
            Arguments.of(fingerrootActivations, multiKeyFedMember, btcKey, addressDerivedFromBtcKey),
            Arguments.of(arrowheadActivations, singleKeyFedMember, btcKey, addressDerivedFromBtcKey), // Given this is a single key fed member, the rsk address is equal to the one obtained from btc key
            Arguments.of(arrowheadActivations, multiKeyFedMember, btcKey, addressDerivedFromRskKey)
        );
    }

    @ParameterizedTest
    @MethodSource("addSignatureArgProvider")
    void addSignature(ActivationConfig.ForBlock activations, FederationMember federationMemberToSignWith, BtcECKey privateKeyToSignWith, String expectedRskAddress) throws Exception {
        // Arrange
        BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        NetworkParameters btcParams = bridgeMainNetConstants.getBtcParams();

        List<FederationMember> federationMembers = FederationTestUtils.getFederationMembersFromPks(150, 250);
        federationMembers.add(federationMemberToSignWith);

        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            Instant.EPOCH,
            0,
            btcParams
        );
        Federation federation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(bridgeMainNetConstants.getFederationConstants(), activations))
            .thenReturn(federation);
        FederationSupport federationSupport = createFederationSupport(bridgeMainNetConstants.getFederationConstants(), federationStorageProvider, mock(Block.class), activations);

        LinkedList<LogInfo> eventLogs = new LinkedList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(bridgeMainNetConstants, activations, eventLogs);

        // Build prev btc tx
        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);

        // Build btc tx to be signed
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(prevOut).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        TransactionOutput output = new TransactionOutput(btcParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcParams));
        btcTx.addOutput(output);

        // Save btc tx to be signed
        SortedMap<Keccak256, BtcTransaction> pegoutWaitingForSignatures = new TreeMap<>();

        final Keccak256 rskTxHash = createHash3(1);
        pegoutWaitingForSignatures.put(rskTxHash, btcTx);
        BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getPegoutsWaitingForSignatures())
            .thenReturn(pegoutWaitingForSignatures);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(bridgeStorageProvider)
            .withFederationSupport(federationSupport)
            .withEventLogger(eventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withActivations(activations)
            .build();

        BtcECKey federatorBtcPubKey = federationMemberToSignWith.getBtcPublicKey();

        Script inputScript = btcTx.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        Sha256Hash sigHash = btcTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = privateKeyToSignWith.sign(sigHash);
        List<byte[]> derEncodedSigs = Collections.singletonList(sig.encodeToDER());

        // Act
        bridgeSupport.addSignature(federationMemberToSignWith.getBtcPublicKey(), derEncodedSigs, rskTxHash);

        // Assert
        commonAssertLogs(eventLogs);
        assertTopics(3, eventLogs);

        assertEvent(
            eventLogs,
            0,
            BridgeEvents.ADD_SIGNATURE.getEvent(),
            new Object[]{rskTxHash.getBytes(), expectedRskAddress},
            new Object[]{federatorBtcPubKey.getPubKey()}
        );
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("Add signature tests")
    class AddSignature {
        private final List<BtcECKey> legacyFederationKeys = getBtcEcKeysFromSeeds(
            new String[] {"signer1", "signer2", "signer3", "signer4", "signer5", "signer6", "signer7", "signer8", "signer9"},
            true
        );
        private final Federation legacyFederation = P2shErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(legacyFederationKeys)
            .build();

        private final List<BtcECKey> segwitKeys = getBtcEcKeysFromSeeds(
            new String[] {"signer1", "signer2", "signer3", "signer4", "signer5", "signer6", "signer7", "signer8", "signer9", "signer10",
                "signer11", "signer12", "signer13", "signer14", "signer15", "signer16", "signer17", "signer18", "signer19", "signer20"
            },
            true
        );
        private final Federation segwitFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(segwitKeys)
            .build();

        private final Keccak256 rskTxHash = createHash3(1);

        private Repository repository;
        private BridgeStorageProvider bridgeStorageProvider;
        private BridgeSupport bridgeSupport;
        private List<LogInfo> logs;
        private BtcTransaction pegout;
        private List<Sha256Hash> sigHashes;


        private void setUp(Federation activeFederation) {
            StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
            FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
            FederationSupportBuilder federationSupportBuilder = FederationSupportBuilder.builder();
            FederationSupport federationSupport = federationSupportBuilder
                .withFederationConstants(bridgeMainnetConstants.getFederationConstants())
                .withFederationStorageProvider(federationStorageProvider)
                .withActivations(activationsAfterForks)
                .build();

            repository = createRepository();
            bridgeStorageProvider = new BridgeStorageProvider(repository, bridgeAddress, btcMainnetParams, activationsAfterForks);
            logs = new ArrayList<>();
            BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeMainnetConstants,
                activationsAfterForks,
                logs
            );

            bridgeSupport = bridgeSupportBuilder
                .withActivations(activationsAfterForks)
                .withBridgeConstants(bridgeMainnetConstants)
                .withRepository(repository)
                .withEventLogger(bridgeEventLogger)
                .withProvider(bridgeStorageProvider)
                .withFederationSupport(federationSupport)
                .build();

            federationStorageProvider.setNewFederation(activeFederation);
            Script activeFederationRedeemScript = activeFederation.getRedeemScript();

            // create prev tx
            BtcTransaction prevTx = new BtcTransaction(btcMainnetParams);
            Coin prevValue = Coin.COIN;
            prevTx.addOutput(prevValue, activeFederation.getAddress());

            // create pegout to be signed
            pegout = new BtcTransaction(btcMainnetParams);
            pegout.setVersion(BTC_TX_VERSION_2);
            // add input
            pegout.addInput(prevTx.getOutput(0));
            // add outputs
            Coin valueToSend = bridgeMainnetConstants.getMinimumPegoutTxValue();
            // add user output
            pegout.addOutput(valueToSend, BitcoinTestUtils.getBtcEcKeyFromSeed("receiver"));
            // add change output
            pegout.addOutput(prevValue.subtract(valueToSend), activeFederation.getAddress());
            // sign inputs
            int inputSentToActiveFederationIndex = 0;
            BitcoinUtils.addSpendingFederationBaseScript(
                pegout,
                inputSentToActiveFederationIndex,
                activeFederationRedeemScript,
                activeFederation.getFormatVersion()
            );

            // add pegout wfs to repo
            SortedMap<Keccak256, BtcTransaction> pegouts = new TreeMap<>();
            pegouts.put(rskTxHash, pegout);
            repository.addStorageBytes(bridgeAddress,
                PEGOUTS_WAITING_FOR_SIGNATURES.getKey(),
                BridgeSerializationUtils.serializeRskTxsWaitingForSignatures(pegouts)
            );

            sigHashes = generateTransactionInputsSigHashes(pegout);
        }

        private void signWithFederators(List<BtcECKey> federatorSignersKeys) throws IOException {
            for (BtcECKey federatorSignerKey : federatorSignersKeys) {
                List<byte[]> signatures = generateSignerEncodedSignatures(federatorSignerKey, sigHashes);
                bridgeSupport.addSignature(federatorSignerKey, signatures, rskTxHash);
            }
        }

        @Test
        void addSignature_correctFederator_whenFedIsSegwit_shouldSign() throws IOException {
            // arrange
            setUp(segwitFederation);

            // save outpoints values
            List<Coin> outpointsValues = new ArrayList<>();
            for (TransactionInput input : pegout.getInputs()) {
                outpointsValues.add(input.getValue());
            }
            repository.addStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(pegout.getHash()),
                BridgeSerializationUtils.serializeOutpointsValues(outpointsValues)
            );

            SortedMap<Keccak256, BtcTransaction> pegoutsWFS = bridgeStorageProvider.getPegoutsWaitingForSignatures();
            assertEquals(1, pegoutsWFS.size());
            BtcTransaction pegoutWFS = pegoutsWFS.get(rskTxHash);

            // act
            int neededSignatures = segwitFederation.getNumberOfSignaturesRequired();
            List<BtcECKey> federatorSignersKeys = segwitKeys.subList(0, neededSignatures);
            signWithFederators(federatorSignersKeys);

            // assert
            assertTxFullySignedAndReleased(segwitFederation, federatorSignersKeys, pegoutWFS);
        }

        @Test
        void addSignature_correctFederator_whenFedIsSegwit_txFullySigned_shouldNotAddExtraSignature() throws IOException {
            // arrange
            setUp(segwitFederation);

            // save outpoints values
            List<Coin> outpointsValues = new ArrayList<>();
            for (TransactionInput input : pegout.getInputs()) {
                outpointsValues.add(input.getValue());
            }
            repository.addStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(pegout.getHash()),
                BridgeSerializationUtils.serializeOutpointsValues(outpointsValues)
            );

            SortedMap<Keccak256, BtcTransaction> pegoutsWFS = bridgeStorageProvider.getPegoutsWaitingForSignatures();
            assertEquals(1, pegoutsWFS.size());
            BtcTransaction pegoutWFS = pegoutsWFS.get(rskTxHash);

            // act
            int neededSignatures = segwitFederation.getNumberOfSignaturesRequired();
            List<BtcECKey> federatorSignersKeys = segwitKeys.subList(0, neededSignatures);
            signWithFederators(federatorSignersKeys);

            BtcTransaction pegoutBeforeAddingExtraSig = new BtcTransaction(btcMainnetParams, pegoutWFS.bitcoinSerialize());
            // extra signature should not be added to pegout
            // since it should have been already removed from wfs map
            BtcECKey extraSigner = segwitKeys.get(neededSignatures + 1);
            signWithFederators(List.of(extraSigner));

            // assert
            assertEquals(pegoutBeforeAddingExtraSig, pegoutWFS);
            assertTxFullySignedAndReleased(segwitFederation, federatorSignersKeys, pegoutWFS);
            assertFederatorDidNotSignInputs(
                pegoutWFS,
                sigHashes,
                extraSigner
            );
        }

        @Test
        void addSignature_correctFederator_whenFedIsLegacy_txFullySigned_shouldNotAddExtraSignature() throws IOException {
            setUp(legacyFederation);

            SortedMap<Keccak256, BtcTransaction> pegoutsWFS = bridgeStorageProvider.getPegoutsWaitingForSignatures();
            assertEquals(1, pegoutsWFS.size());
            BtcTransaction pegoutWFS = pegoutsWFS.get(rskTxHash);

            int neededSignatures = legacyFederation.getNumberOfSignaturesRequired();
            List<BtcECKey> federatorSignersKeys = legacyFederationKeys.subList(0, neededSignatures);
            signWithFederators(federatorSignersKeys);

            BtcTransaction pegoutBeforeAddingExtraSig = new BtcTransaction(btcMainnetParams, pegoutWFS.bitcoinSerialize());
            // extra signature should not be added to pegout
            // since it should have been already removed from wfs map
            BtcECKey extraSigner = legacyFederationKeys.get(legacyFederation.getNumberOfSignaturesRequired() + 1);
            signWithFederators(List.of(extraSigner));

            // assert
            assertEquals(pegoutBeforeAddingExtraSig, pegoutWFS);
            assertTxFullySignedAndReleased(legacyFederation, federatorSignersKeys, pegoutWFS);
            assertFederatorDidNotSignInputs(
                pegoutWFS,
                sigHashes,
                extraSigner
            );
        }

        private void assertTxFullySignedAndReleased(Federation federation, List<BtcECKey> federatorSignersKeys, BtcTransaction pegoutWFS) throws IOException {
            for (BtcECKey federatorSignerKey : federatorSignersKeys) {
                assertFederatorSigning(
                    rskTxHash.getBytes(),
                    pegoutWFS,
                    sigHashes,
                    federation,
                    federatorSignerKey,
                    logs
                );
            }
            assertLogReleaseBtc(logs, pegoutWFS, rskTxHash);

            // assert pegout was removed from wfs structure
            assertEquals(0, bridgeStorageProvider.getPegoutsWaitingForSignatures().size());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("test migration tx signing")
    class MigrationTxSigning {
        private final String sharedFederatorSeed = "sharedFederator";
        private final String notSharedRetiringFederatorSeed = "retiringFederator";

        private final List<BtcECKey> retiringLegacyFederationKeys = getBtcEcKeysFromSeeds(
            new String[] {sharedFederatorSeed, notSharedRetiringFederatorSeed, "signer3", "signer4", "signer5", "signer6", "signer7", "signer8", "signer9"},
            true
        );
        private final Federation retiringLegacyFederation = P2shErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(retiringLegacyFederationKeys)
            .build();

        private final List<BtcECKey> activeLegacyFederationKeys = getBtcEcKeysFromSeeds(
            new String[] {sharedFederatorSeed, "activeFederator", "signer3", "signer4", "signer5", "signer6", "signer7", "signer8", "signer9"},
            true
        );
        private final Federation activeLegacyFederation = P2shErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(activeLegacyFederationKeys)
            .build();

        private final List<BtcECKey> activeSegwitFederationKeys = getBtcEcKeysFromSeeds(
            new String[] {
                sharedFederatorSeed, "activeFederator", "signer3", "signer4", "signer5", "signer6", "signer7", "signer8", "signer9", "signer10",
                "signer11", "signer12", "signer13", "signer14", "signer15", "signer16", "signer17", "signer18", "signer19", "signer20"
            },
            true
        );
        private final Federation activeSegwitFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(activeSegwitFederationKeys)
            .build();

        private final List<BtcECKey> retiringSegwitFederationKeys = getBtcEcKeysFromSeeds(
            new String[] {
                sharedFederatorSeed, notSharedRetiringFederatorSeed, "signer3", "signer4", "signer5", "signer6", "signer7", "signer8", "signer9", "signer10",
                "signer11", "signer12", "signer13", "signer14", "signer15", "signer16", "signer17", "signer18", "signer19", "signer20"
            },
            true
        );
        private final Federation retiringSegwitFederation = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(retiringSegwitFederationKeys)
            .build();
        private final Keccak256 rskTxHash = createHash3(1);

        private final BtcECKey sharedFederatorKey = BitcoinTestUtils.getBtcEcKeyFromSeed(sharedFederatorSeed);
        private final BtcECKey notSharedRetiringFederatorKey = BitcoinTestUtils.getBtcEcKeyFromSeed(notSharedRetiringFederatorSeed);

        private Repository repository;
        private BridgeStorageProvider bridgeStorageProvider;
        private List<LogInfo> logs = new ArrayList<>();
        private BridgeSupport bridgeSupport;

        private BtcTransaction migrationTx;

        private void setUp(Federation retiringFederation, Federation activeFederation) {
            // arrange
            StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
            FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
            repository = createRepository();
            bridgeStorageProvider = new BridgeStorageProvider(repository, bridgeAddress, btcMainnetParams, activationsAfterForks);
            logs = new ArrayList<>();
            BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeMainnetConstants,
                activationsAfterForks,
                logs
            );

            federationStorageProvider.setOldFederation(retiringFederation);
            federationStorageProvider.setNewFederation(activeFederation);
            // Move the required blocks ahead for the new powpeg to become active
            var blockNumber =
                retiringFederation.getCreationBlockNumber() + bridgeMainnetConstants.getFederationConstants().getFederationActivationAge(activationsAfterForks);
            Block currentBlock = createRskBlock(blockNumber);

            FederationConstants federationConstants = bridgeMainnetConstants.getFederationConstants();
            FederationSupport federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(federationConstants)
                .withFederationStorageProvider(federationStorageProvider)
                .withRskExecutionBlock(currentBlock)
                .withActivations(activationsAfterForks)
                .build();
            bridgeSupport = BridgeSupportBuilder.builder()
                .withProvider(bridgeStorageProvider)
                .withRepository(repository)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(currentBlock)
                .withActivations(activationsAfterForks)
                .withBridgeConstants(bridgeMainnetConstants)
                .withFederationSupport(federationSupport)
                .withLockingCapSupport(lockingCapSupport)
                .build();

            // create migration to be signed
            migrationTx = MigrationTransactionBuilder.builder()
                .withRetiringFederation(retiringFederation)
                .withActiveFederation(activeFederation)
                .build();

            // add migration wfs to repo
            SortedMap<Keccak256, BtcTransaction> pegoutsWFS = new TreeMap<>();
            pegoutsWFS.put(rskTxHash, migrationTx);
            repository.addStorageBytes(bridgeAddress,
                PEGOUTS_WAITING_FOR_SIGNATURES.getKey(),
                BridgeSerializationUtils.serializeRskTxsWaitingForSignatures(pegoutsWFS)
            );
        }

        private void assertKeys(Federation retiringFederation, Federation activeFederation) {
            assertTrue(retiringFederation.hasBtcPublicKey(sharedFederatorKey));
            assertTrue(activeFederation.hasBtcPublicKey(sharedFederatorKey));
            assertTrue(retiringFederation.hasBtcPublicKey(notSharedRetiringFederatorKey));
            assertFalse(activeFederation.hasBtcPublicKey(notSharedRetiringFederatorKey));
        }

        @Test
        void addSignature_migrationTx_legacyRetiringAndLegacyActiveFeds_withOneSharedFederatorAndOneOnlyPartOfRetiringFed() throws IOException {
            setUp(retiringLegacyFederation, activeLegacyFederation);
            assertKeys(retiringLegacyFederation, activeLegacyFederation);

            BtcTransaction migrationTxReference = bridgeStorageProvider.getPegoutsWaitingForSignatures().get(rskTxHash);

            // act
            List<Sha256Hash> sigHashes = generateTransactionInputsSigHashes(migrationTx);

            // sign with shared federator
            List<byte[]> sharedFederatorSignatures = generateSignerEncodedSignatures(sharedFederatorKey, sigHashes);
            bridgeSupport.addSignature(sharedFederatorKey, sharedFederatorSignatures, rskTxHash);

            assertFederatorSigning(
                rskTxHash.getBytes(),
                migrationTxReference,
                sigHashes,
                retiringLegacyFederation,
                sharedFederatorKey,
                logs
            );

            // sign with different federator
            List<byte[]> notSharedFederatorSignatures = generateSignerEncodedSignatures(notSharedRetiringFederatorKey, sigHashes);
            bridgeSupport.addSignature(notSharedRetiringFederatorKey, notSharedFederatorSignatures, rskTxHash);
            assertFederatorSigning(
                rskTxHash.getBytes(),
                migrationTxReference,
                sigHashes,
                retiringLegacyFederation,
                notSharedRetiringFederatorKey,
                logs
            );
        }

        @Test
        void addSignature_migrationTx_legacyRetiringAndSegwitActiveFeds_withOneSharedFederatorAndOneOnlyPartOfRetiringFed() throws IOException {
            setUp(retiringLegacyFederation, activeSegwitFederation);
            assertKeys(retiringLegacyFederation, activeSegwitFederation);

            BtcTransaction migrationTxReference = bridgeStorageProvider.getPegoutsWaitingForSignatures().get(rskTxHash);

            // act
            List<Sha256Hash> sigHashes = generateTransactionInputsSigHashes(migrationTx);

            // sign with shared federator
            List<byte[]> sharedFederatorSignatures = generateSignerEncodedSignatures(sharedFederatorKey, sigHashes);
            bridgeSupport.addSignature(sharedFederatorKey, sharedFederatorSignatures, rskTxHash);

            assertFederatorSigning(
                rskTxHash.getBytes(),
                migrationTxReference,
                sigHashes,
                retiringLegacyFederation,
                sharedFederatorKey,
                logs
            );

            // sign with different federator
            List<byte[]> notSharedFederatorSignatures = generateSignerEncodedSignatures(notSharedRetiringFederatorKey, sigHashes);
            bridgeSupport.addSignature(notSharedRetiringFederatorKey, notSharedFederatorSignatures, rskTxHash);
            assertFederatorSigning(
                rskTxHash.getBytes(),
                migrationTxReference,
                sigHashes,
                retiringLegacyFederation,
                notSharedRetiringFederatorKey,
                logs
            );
        }

        @Test
        void addSignature_migrationTx_segwitRetiringAndSegwitActiveFeds_withOneSharedFederatorAndOneOnlyPartOfRetiringFed() throws IOException {
            setUp(retiringSegwitFederation, activeSegwitFederation);
            assertKeys(retiringSegwitFederation, activeSegwitFederation);

            List<Coin> outpointsValues = new ArrayList<>();
            for (int i = 0; i < migrationTx.getInputs().size(); i++) {
                Coin amountReceived = migrationTx.getInput(i).getValue();
                outpointsValues.add(amountReceived);
            }
            repository.addStorageBytes(
                bridgeAddress,
                getStorageKeyForReleaseOutpointsValues(migrationTx.getHash()),
                BridgeSerializationUtils.serializeOutpointsValues(outpointsValues)
            );

            // act
            List<Sha256Hash> sigHashes = generateTransactionInputsSigHashes(migrationTx);

            // sign with shared federator
            List<byte[]> sharedFederatorSignatures = generateSignerEncodedSignatures(sharedFederatorKey, sigHashes);
            bridgeSupport.addSignature(sharedFederatorKey, sharedFederatorSignatures, rskTxHash);

            BtcTransaction migrationTxReference = bridgeStorageProvider.getPegoutsWaitingForSignatures().get(rskTxHash);
            assertFederatorSigning(
                rskTxHash.getBytes(),
                migrationTxReference,
                sigHashes,
                retiringSegwitFederation,
                sharedFederatorKey,
                logs
            );

            // sign with different federator
            List<byte[]> notSharedFederatorSignatures = generateSignerEncodedSignatures(notSharedRetiringFederatorKey, sigHashes);
            bridgeSupport.addSignature(notSharedRetiringFederatorKey, notSharedFederatorSignatures, rskTxHash);
            assertFederatorSigning(
                rskTxHash.getBytes(),
                migrationTxReference,
                sigHashes,
                retiringSegwitFederation,
                notSharedRetiringFederatorKey,
                logs
            );
        }
    }

    private static void assertEvent(List<LogInfo> logs, int index, CallTransaction.Function event, Object[] topics, Object[] params) {
        final LogInfo log = logs.get(index);
        assertEquals(LogInfo.byteArrayToList(event.encodeEventTopics(topics)), log.getTopics());
        assertArrayEquals(event.encodeEventData(params), log.getData());
    }

    private void assertTopics(int topics, List<LogInfo> logs) {
        assertEquals(topics, logs.get(0).getTopics().size());
    }

    private void commonAssertLogs(List<LogInfo> logs) {
        assertEquals(1, logs.size());
        LogInfo entry = logs.get(0);

        // Assert address that made the log
        assertEquals(bridgeAddress, new RskAddress(entry.getAddress()));
        assertArrayEquals(bridgeAddress.getBytes(), entry.getAddress());
    }

    /**
     * Helper method to test addSignature() with a valid federatorPublicKey parameter and both valid/invalid signatures
     *
     * @param privateKeysToSignWith keys used to sign the tx. Federator key when we want to produce a valid signature, a random key when we want to produce an invalid signature
     * @param numberOfInputsToSign  There is just 1 input. 1 when testing the happy case, other values to test attacks/bugs.
     * @param signatureCanonical    Signature should be canonical. true when testing the happy case, false to test attacks/bugs.
     * @param signTwice             Sign again with the same key
     * @param expectedResult        "InvalidParameters", "PartiallySigned" or "FullySigned"
     */
    private void addSignatureFromValidFederator(List<BtcECKey> privateKeysToSignWith, int numberOfInputsToSign, boolean signatureCanonical, boolean signTwice, String expectedResult) throws Exception {
        // Federation is the genesis federation ATM
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeRegTestConstants.getFederationConstants());
        Repository repository = createRepository();

        final Keccak256 keccak256 = RskTestUtils.createHash(1);

        Repository track = repository.startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, bridgeAddress, btcRegTestParams, activationsBeforeForks);

        BtcTransaction prevTx = new BtcTransaction(btcRegTestParams);
        TransactionOutput prevOut = new TransactionOutput(btcRegTestParams, prevTx, Coin.FIFTY_COINS, genesisFederation.getAddress());
        prevTx.addOutput(prevOut);

        BtcTransaction t = new BtcTransaction(btcRegTestParams);
        TransactionOutput output = new TransactionOutput(btcRegTestParams, t, Coin.COIN, new BtcECKey().toAddress(btcRegTestParams));
        t.addOutput(output);
        t.addInput(prevOut).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(genesisFederation));
        provider.getPegoutsWaitingForSignatures().put(keccak256, t);
        provider.save();
        track.commit();

        track = repository.startTracking();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLogger eventLogger = new BrigeEventLoggerLegacyImpl(bridgeRegTestConstants, activations, logs);
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            track,
            bridgeAddress,
            btcRegTestParams,
            activationsAfterForks
        );
        FederationSupport federationSupport = createDefaultFederationSupport(bridgeRegTestConstants.getFederationConstants());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(bridgeStorageProvider)
            .withFederationSupport(federationSupport)
            .withRepository(track)
            .withEventLogger(eventLogger)
            .build();

        Script inputScript = t.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        Sha256Hash sighash = t.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        BtcECKey.ECDSASignature sig = privateKeysToSignWith.get(0).sign(sighash);
        if (!signatureCanonical) {
            sig = new BtcECKey.ECDSASignature(sig.r, BtcECKey.CURVE.getN().subtract(sig.s));
        }
        byte[] derEncodedSig = sig.encodeToDER();

        List<byte[]> derEncodedSigs = new ArrayList<>();
        for (int i = 0; i < numberOfInputsToSign; i++) {
            derEncodedSigs.add(derEncodedSig);
        }
        bridgeSupport.addSignature(findPublicKeySignedBy(genesisFederation.getBtcPublicKeys(), privateKeysToSignWith.get(0)), derEncodedSigs, keccak256);
        if (signTwice) {
            // Create another valid signature with the same private key
            ECDSASigner signer = new ECDSASigner();
            X9ECParameters curveParams = CustomNamedCurves.getByName("secp256k1");
            ECDomainParameters curve = new ECDomainParameters(curveParams.getCurve(), curveParams.getG(), curveParams.getN(), curveParams.getH());
            ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(privateKeysToSignWith.get(0).getPrivKey(), curve);
            signer.init(true, privKey);
            BigInteger[] components = signer.generateSignature(sighash.getBytes());
            BtcECKey.ECDSASignature sig2 = new BtcECKey.ECDSASignature(components[0], components[1]).toCanonicalised();
            List<byte[]> list = new ArrayList<>();
            list.add(sig2.encodeToDER());
            bridgeSupport.addSignature(findPublicKeySignedBy(genesisFederation.getBtcPublicKeys(), privateKeysToSignWith.get(0)), list, keccak256);
        }
        if (privateKeysToSignWith.size() > 1) {
            BtcECKey.ECDSASignature sig2 = privateKeysToSignWith.get(1).sign(sighash);
            byte[] derEncodedSig2 = sig2.encodeToDER();
            List<byte[]> derEncodedSigs2 = new ArrayList<>();
            for (int i = 0; i < numberOfInputsToSign; i++) {
                derEncodedSigs2.add(derEncodedSig2);
            }
            bridgeSupport.addSignature(
                findPublicKeySignedBy(genesisFederation.getBtcPublicKeys(), privateKeysToSignWith.get(1)),
                derEncodedSigs2,
                keccak256
            );
        }
        bridgeSupport.save();
        track.commit();

        provider = new BridgeStorageProvider(repository, bridgeAddress, btcRegTestParams, activationsBeforeForks);

        if ("FullySigned".equals(expectedResult)) {
            assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
            assertThat(logs, is(not(empty())));
            assertThat(logs, hasSize(3));
            LogInfo releaseTxEvent = logs.get(2);
            assertThat(releaseTxEvent.getTopics(), hasSize(1));
            assertThat(releaseTxEvent.getTopics(), hasItem(Bridge.RELEASE_BTC_TOPIC));
            BtcTransaction releaseTx = new BtcTransaction(btcRegTestParams, ((RLPList) RLP.decode2(releaseTxEvent.getData()).get(0)).get(1).getRLPData());
            Script retrievedScriptSig = releaseTx.getInput(0).getScriptSig();
            assertEquals(4, retrievedScriptSig.getChunks().size());
            assertTrue(retrievedScriptSig.getChunks().get(1).data.length > 0);
            assertTrue(retrievedScriptSig.getChunks().get(2).data.length > 0);
        } else {
            Script retrievedScriptSig = provider.getPegoutsWaitingForSignatures().get(keccak256).getInput(0).getScriptSig();
            assertEquals(4, retrievedScriptSig.getChunks().size());
            boolean expectSignatureToBePersisted = "PartiallySigned".equals(expectedResult); // for "InvalidParameters"
            assertEquals(expectSignatureToBePersisted, retrievedScriptSig.getChunks().get(1).data.length > 0);
            assertFalse(retrievedScriptSig.getChunks().get(2).data.length > 0);
        }
    }

    private BtcECKey findPublicKeySignedBy(List<BtcECKey> pubs, BtcECKey pk) {
        for (BtcECKey pub : pubs) {
            if (Arrays.equals(pk.getPubKey(), pub.getPubKey())) {
                return pub;
            }
        }
        return pk;
    }

    private FederationSupport createFederationSupport(FederationConstants federationConstants, FederationStorageProvider federationStorageProvider, Block executionBlock, ActivationConfig.ForBlock activations) {
        return new FederationSupportImpl(federationConstants, federationStorageProvider, executionBlock, activations);
    }

    // most of the time we just need the federation constants, to have access to the genesis fed
    private FederationSupport createDefaultFederationSupport(FederationConstants federationConstants) {
        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        Block block = mock(Block.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        return new FederationSupportImpl(federationConstants, federationStorageProvider, block, activations);
    }
}

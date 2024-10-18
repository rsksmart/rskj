package co.rsk.peg;

import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.PegTestUtils.*;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static co.rsk.peg.pegin.RejectedPeginReason.*;
import static co.rsk.peg.utils.UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.*;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.UnrefundablePeginReason;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.WhitelistStorageProvider;
import co.rsk.peg.whitelist.WhitelistSupportImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BridgeSupportRegisterBtcTransactionTest {
    private static final RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final FederationConstants federationMainnetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
    private static final ActivationConfig.ForBlock arrowhead600Activations = ActivationConfigsForTest.arrowhead600().forBlock(0);
    private static final ActivationConfig.ForBlock lovell700Activations = ActivationConfigsForTest.lovell700().forBlock(0);
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
    private WhitelistStorageProvider whitelistStorageProvider;

    private static final Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(ActivationConfigsForTest.all().forBlock(0));
    private static final Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);

    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;

    private BridgeStorageProvider provider;
    private FederationStorageProvider federationStorageProvider;
    private Address userAddress;

    private List<BtcECKey> retiredFedSigners;
    private Federation retiredFed;

    private List<BtcECKey> retiringFedSigners;
    private Federation retiringFederation;

    private List<BtcECKey> activeFedSigners;
    private Federation activeFederation;

    private BtcBlockStoreWithCache.Factory mockFactory;
    private SignatureCache signatureCache;
    private BridgeEventLogger bridgeEventLogger;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;

    private final List<UTXO> retiringFederationUtxos = new ArrayList<>();
    private final List<UTXO> activeFederationUtxos = new ArrayList<>();
    private PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations;
    private Block rskExecutionBlock;
    private Transaction rskTx;

    private int heightAtWhichToStartUsingPegoutIndex;

    private co.rsk.bitcoinj.core.BtcBlock registerHeader;

    // Before peg-out tx index gets in use
    private void assertInvalidPeginIsIgnored() throws IOException {
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    // After peg-out tx index gets in use
    private void assertInvalidPeginIsRejectedWithInvalidAmountReason(BtcTransaction btcTransaction) throws IOException {
        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, INVALID_AMOUNT);
        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(btcTransaction, UnrefundablePeginReason.INVALID_AMOUNT);
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    // fingerroot
    private void assertUnknownTxIsProcessedAsPegin(RskAddress expectedRskAddressToBeLogged, BtcTransaction btcTransaction, int protocolVersion) throws IOException {
        verify(bridgeEventLogger, times(1)).logPeginBtc(expectedRskAddressToBeLogged, btcTransaction, Coin.ZERO, protocolVersion);
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    // After arrowhead600Activations but before grace period
    private void assertUnknownTxIsRejectedWithInvalidAmountReason(BtcTransaction btcTransaction) throws IOException {
        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, INVALID_AMOUNT);
        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(btcTransaction, UnrefundablePeginReason.INVALID_AMOUNT);
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    // After arrowhead600Activations and grace period
    private void assertUnknownTxIsIgnored() throws IOException {
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    private void assertPeginIsRejectedAndRefunded(ActivationConfig.ForBlock activations, BtcTransaction btcTransaction, Coin sentAmount, RejectedPeginReason expectedRejectedPeginReason) throws IOException {
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());

        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());
        Entry pegoutWaitingForConfirmationEntry = pegoutsWaitingForConfirmations.getEntries().stream().findFirst().get();
        BtcTransaction refundPegout = pegoutWaitingForConfirmationEntry.getBtcTransaction();
        Sha256Hash refundPegoutHash = refundPegout.getHash();
        List<Coin> refundPegoutOutpointValues = extractOutpointValues(refundPegout);

        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, expectedRejectedPeginReason);
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(rskTx.getHash().getBytes(), refundPegout, sentAmount);

        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        verify(provider, never()).setPegoutTxSigHash(any());

        if(activations == lovell700Activations) {
            verify(bridgeEventLogger, times(1)).logPegoutTransactionCreated(refundPegoutHash, refundPegoutOutpointValues);
        } else {
            verify(bridgeEventLogger, never()).logPegoutTransactionCreated(any(), any());
        }
    }

    // Before arrowhead600Activations is activated
    private void assertLegacyUndeterminedSenderPeginIsRejectedAsPeginV1InvalidPayloadBeforeRSKIP379(BtcTransaction btcTransaction) throws IOException {
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            btcTransaction, PEGIN_V1_INVALID_PAYLOAD
        );
        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(
            btcTransaction,
            LEGACY_PEGIN_UNDETERMINED_SENDER
        );

        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());
        verify(bridgeEventLogger, never()).logPegoutTransactionCreated(any(), any());

        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
        assertTrue(pegoutsWaitingForConfirmations.getEntries().isEmpty());
    }

    // After arrowhead600Activations is activated
    private void assertLegacyUndeterminedSenderPeginIsRejected(BtcTransaction btcTransaction) throws IOException {
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            btcTransaction, RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER
        );
        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(
            btcTransaction,
            LEGACY_PEGIN_UNDETERMINED_SENDER
        );

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());

        Assertions.assertTrue(activeFederationUtxos.isEmpty());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
        Assertions.assertTrue(pegoutsWaitingForConfirmations.getEntries().isEmpty());
    }

    private void assertInvalidPeginV1UndeterminedSenderIsRejected(BtcTransaction btcTransaction) throws IOException {
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            btcTransaction, PEGIN_V1_INVALID_PAYLOAD
        );
        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(
            btcTransaction,
            LEGACY_PEGIN_UNDETERMINED_SENDER
        );

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());
        verify(bridgeEventLogger, never()).logPegoutTransactionCreated(any(), any());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());

        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
        assertTrue(pegoutsWaitingForConfirmations.getEntries().isEmpty());
    }

    private static Stream<Arguments> common_args() {
        // before RSKIP379 activation
        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                false
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true
            ),

            // after RSKIP379 activation but before blockNumber to start using Pegout Index
            Arguments.of(
                arrowhead600Activations,
                false,
                false
            ),
            Arguments.of(
                arrowhead600Activations,
                false,
                true
            ),

            // after RSKIP379 activation and after blockNumber to start using Pegout Index
            Arguments.of(
                arrowhead600Activations,
                true,
                false
            ),
            Arguments.of(
                arrowhead600Activations,
                true,
                true
            )
        );
    }

    private static Stream<Arguments> activationsAndShouldUsePegoutIndexArgs() {
        return Stream.of(
            // before RSKIP379 activation
            Arguments.of(
                fingerrootActivations,
                false
            ),
            // after RSKIP379 activation but before using Pegout Index
            Arguments.of(
                arrowhead600Activations,
                false
            ),
            // after RSKIP379 activation and after start using Pegout Index
            Arguments.of(
                arrowhead600Activations,
                true
            ),
            Arguments.of(
                lovell700Activations,
                true
            )
        );
    }

    // unknown test
    private static Stream<Arguments> btc_transaction_sending_funds_to_unknown_address_args() {
        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                false,
                false
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true,
                false
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true,
                true
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                false,
                true
            ),


            Arguments.of(
                arrowhead600Activations,
                false,
                false,
                false
            ),
            Arguments.of(
                arrowhead600Activations,
                false,
                true,
                false
            ),
            Arguments.of(
                arrowhead600Activations,
                false,
                true,
                true
            ),
            Arguments.of(
                arrowhead600Activations,
                false,
                false,
                true
            ),

            Arguments.of(
                arrowhead600Activations,
                true,
                false,
                false
            ),
            Arguments.of(
                arrowhead600Activations,
                true,
                true,
                false
            ),
            Arguments.of(
                arrowhead600Activations,
                true,
                true,
                true
            ),
            Arguments.of(
                arrowhead600Activations,
                true,
                false,
                true
            )
        );
    }

    @BeforeEach
    void init() throws IOException {
        registerHeader = null;

        userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");
        NetworkParameters btcParams = bridgeMainnetConstants.getBtcParams();

        retiredFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        retiredFed = createFederation(bridgeMainnetConstants, retiredFedSigners);

        retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa04", "fa05", "fa06"}, true
        );

        retiringFedSigners.sort(BtcECKey.PUBKEY_COMPARATOR);
        List<FederationMember> retiringFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(retiringFedSigners);
        Instant creationTime = Instant.ofEpochMilli(1000L);
        long retiringFedCreationBlockNumber = 1;
        List<BtcECKey> erpPubKeys = federationMainnetConstants.getErpFedPubKeysList();
        long activationDelay = federationMainnetConstants.getErpFedActivationDelay();

        FederationArgs retiringFedArgs =
            new FederationArgs(retiringFedMembers, creationTime, retiringFedCreationBlockNumber, btcParams);
        retiringFederation = FederationFactory.buildP2shErpFederation(retiringFedArgs, erpPubKeys, activationDelay);

        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa07", "fa08", "fa09", "fa10", "fa11"}, true
        );
        activeFedSigners.sort(BtcECKey.PUBKEY_COMPARATOR);
        List<FederationMember> activeFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(activeFedSigners);
        long activeFedCreationBlockNumber = 2L;
        FederationArgs activeFedArgs =
            new FederationArgs(activeFedMembers, creationTime, activeFedCreationBlockNumber, btcParams);
        activeFederation = FederationFactory.buildP2shErpFederation(activeFedArgs, erpPubKeys, activationDelay);

        mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        bridgeEventLogger = mock(BridgeEventLogger.class);
        btcLockSenderProvider = new BtcLockSenderProvider();

        peginInstructionsProvider = new PeginInstructionsProvider();

        provider = mock(BridgeStorageProvider.class);
        when(provider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        whitelistStorageProvider = mock(WhitelistStorageProvider.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(whitelistStorageProvider.getLockWhitelist(lovell700Activations, btcMainnetParams)).thenReturn(lockWhitelist);

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getOldFederationBtcUTXOs())
            .thenReturn(retiringFederationUtxos);
        when(federationStorageProvider.getNewFederationBtcUTXOs(any(NetworkParameters.class), any(ActivationConfig.ForBlock.class)))
            .thenReturn(activeFederationUtxos);

        pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        when(federationStorageProvider.getNewFederation(any(FederationConstants.class), any(ActivationConfig.ForBlock.class)))
            .thenReturn(activeFederation);

        // Set executionBlock right after the migration should start
        long blockNumber = activeFederation.getCreationBlockNumber() +
            federationMainnetConstants.getFederationActivationAge(arrowhead600Activations) +
            federationMainnetConstants.getFundsMigrationAgeSinceActivationBegin() +
                               1;
        rskExecutionBlock = mock(Block.class);


        when(rskExecutionBlock.getNumber()).thenReturn(blockNumber);

        rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(PegTestUtils.createHash3(1));

        int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getPegoutTxIndexGracePeriodInBtcBlocks();

        heightAtWhichToStartUsingPegoutIndex = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
    }

    private PartialMerkleTree createPmtWithWitness(BtcTransaction btcTx) {
        List<Sha256Hash> hashesWithWitness = new ArrayList<>();
        hashesWithWitness.add(btcTx.getHash(true));
        byte[] bitsWithWitness = new byte[1];
        bitsWithWitness[0] = 0x3f;
        PartialMerkleTree partialMerkleTreeWithWitness = new PartialMerkleTree(btcMainnetParams, bitsWithWitness, hashesWithWitness, 1);
        Sha256Hash witnessMerkleRoot = partialMerkleTreeWithWitness.getTxnHashAndMerkleRoot(new ArrayList<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        when(provider.getCoinbaseInformation(any())).thenReturn(coinbaseInformation);
        return partialMerkleTreeWithWitness;
    }

    private PartialMerkleTree createPmtAndMockBlockStore(BtcTransaction btcTransaction, int height) throws BlockStoreException {
        PartialMerkleTree pmt = new PartialMerkleTree(btcMainnetParams, new byte[]{0x3f}, Collections.singletonList(btcTransaction.getHash()), 1);
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());

        registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcMainnetParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcMainnetParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), height + BridgeSupportRegisterBtcTransactionTest.bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations());
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcMainnetParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + BridgeSupportRegisterBtcTransactionTest.bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );
        return pmt;
    }

    private BridgeSupport buildBridgeSupport(ActivationConfig.ForBlock activations) {
        Repository repository = mock(Repository.class);
        when(repository.getBalance(bridgeContractAddress)).thenReturn(co.rsk.core.Coin.fromBitcoin(bridgeMainnetConstants.getMaxRbtc()));
        LockingCapSupport lockingCapSupport =  mock(LockingCapSupport.class);
        when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(bridgeMainnetConstants.getMaxRbtc()));

        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);
        FeePerKbSupport feePerKbSupport =  new FeePerKbSupportImpl(
            bridgeMainnetConstants.getFeePerKbConstants(),
            feePerKbStorageProvider
        );

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        whitelistStorageProvider = mock(WhitelistStorageProvider.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(whitelistStorageProvider.getLockWhitelist(activations, btcMainnetParams)).thenReturn(lockWhitelist);

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(rskExecutionBlock)
            .build();

        return BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withRepository(repository)
            .withProvider(provider)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .withFeePerKbSupport(feePerKbSupport)
            .withWhitelistSupport(new WhitelistSupportImpl(bridgeMainnetConstants.getWhitelistConstants(), whitelistStorageProvider, activations, mock(SignatureCache.class)))
            .withFederationSupport(federationSupport)
            .build();
    }

    @ParameterizedTest
    @MethodSource("btc_transaction_sending_funds_to_unknown_address_args")
    void registering_btc_transaction_sending_funds_to_unknown_address(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        BtcECKey senderBtcKey = new BtcECKey();
        ECKey senderRskKey = ECKey.fromPublicOnly(senderBtcKey.getPubKey());
        RskAddress rskAddress = new RskAddress(senderRskKey.getAddress());

        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin amountToSend = shouldSendAmountBelowMinimum ? belowMinimumPeginTxValue : minimumPeginTxValue;
        btcTransaction.addOutput(amountToSend, userAddress);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        // fingerroot - unknown tx should be processed and try to register
        if (activations == fingerrootActivations) {
            assertUnknownTxIsProcessedAsPegin(rskAddress, btcTransaction, 0);
        }
        // arrowhead600Activations but before grace period - unknown tx should be rejected
        else if (activations == arrowhead600Activations && !shouldUsePegoutTxIndex) {
            assertUnknownTxIsRejectedWithInvalidAmountReason(btcTransaction);
        }
        // arrowhead600Activations and after grace period - unknown tx are just ignored
        else {
            assertUnknownTxIsIgnored();
        }
    }

    @ParameterizedTest
    @MethodSource("btc_transaction_sending_funds_to_unknown_address_args")
    void registering_btc_v1_transaction_sending_funds_to_unknown_address(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        BtcECKey senderBtcKey = new BtcECKey();
        ECKey senderRskKey = ECKey.fromPublicOnly(senderBtcKey.getPubKey());
        RskAddress rskAddress = new RskAddress(senderRskKey.getAddress());

        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin amountToSend = shouldSendAmountBelowMinimum ? belowMinimumPeginTxValue : minimumPeginTxValue;
        btcTransaction.addOutput(amountToSend, userAddress);
        btcTransaction.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRsk(
                1,
                rskAddress,
                Optional.empty()
            )
        );

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        // fingerroot - unknown tx should be processed and try to register
        if (activations == fingerrootActivations) {
            assertUnknownTxIsProcessedAsPegin(rskAddress, btcTransaction, 1);
        }
        // arrowhead600Activations but before grace period - unknown tx should be rejected
        else if (activations == arrowhead600Activations && !shouldUsePegoutTxIndex) {
            assertUnknownTxIsRejectedWithInvalidAmountReason(btcTransaction);
        }
        // arrowhead600Activations and after grace period - unknown tx are just ignored
        else {
            assertUnknownTxIsIgnored();
        }
    }

    @ParameterizedTest
    @MethodSource("btc_transaction_sending_funds_to_unknown_address_args")
    void registering_btc_transaction_many_outputs_to_unknown_addresses(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        BtcECKey senderBtcKey = new BtcECKey();
        ECKey senderRskKey = ECKey.fromPublicOnly(senderBtcKey.getPubKey());
        RskAddress rskAddress = new RskAddress(senderRskKey.getAddress());

        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin amountToSend = shouldSendAmountBelowMinimum ? belowMinimumPeginTxValue : minimumPeginTxValue;

        btcTransaction.addOutput(amountToSend, userAddress);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(amountToSend, new BtcECKey().toAddress(btcMainnetParams));
        }

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert

        // fingerroot - unknown tx should be processed and try to register
        if (activations == fingerrootActivations) {
            assertUnknownTxIsProcessedAsPegin(rskAddress, btcTransaction, 0);
        }
        // arrowhead600Activations but before grace period - unknown tx should be rejected
        else if (activations == arrowhead600Activations && !shouldUsePegoutTxIndex) {
            assertUnknownTxIsRejectedWithInvalidAmountReason(btcTransaction);
        }
        // arrowhead600Activations and after grace period - unknown tx are just ignored
        else {
            assertUnknownTxIsIgnored();
        }
    }

    // Pegin tests

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_legacy_to_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_multiple_outputs_to_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        }

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue.multiply(10)), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(10, activeFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_to_active_fed_with_bech32_output(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());
        btcTransaction.addOutput(PegTestUtils.createBech32Output(btcMainnetParams, Coin.COIN));

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_to_active_fed_equal_to_minimum_with_other_random_outputs_below_minimum(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(PegTestUtils.createBech32Output(btcMainnetParams, belowMinimumPeginTxValue));
        btcTransaction.addOutput(belowMinimumPeginTxValue, userAddress);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_to_active_fed_below_minimum(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (shouldUsePegoutTxIndex) {
            assertInvalidPeginIsRejectedWithInvalidAmountReason(btcTransaction);
        } else {
            assertInvalidPeginIsIgnored();
        }
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_to_active_fed_below_and_above_minimum(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (shouldUsePegoutTxIndex) {
            assertInvalidPeginIsRejectedWithInvalidAmountReason(btcTransaction);
        } else {
            assertInvalidPeginIsIgnored();
        }
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_multiple_outputs_to_active_fed_sum_amount_equal_to_minimum_pegin(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));

        Coin amountPerOutput = minimumPeginTxValue.div(10);

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(amountPerOutput, activeFederation.getAddress());
        }

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (shouldUsePegoutTxIndex) {
            assertInvalidPeginIsRejectedWithInvalidAmountReason(btcTransaction);
        } else {
            assertInvalidPeginIsIgnored();
        }
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_to_active_and_retiring_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(minimumPeginTxValue, retiringFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue.multiply(2)), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertEquals(1, retiringFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_to_active_fed_below_minimum_and_retiring_above_minimum(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));

        btcTransaction.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(minimumPeginTxValue, retiringFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (shouldUsePegoutTxIndex) {
            assertInvalidPeginIsRejectedWithInvalidAmountReason(btcTransaction);
        } else {
            assertInvalidPeginIsIgnored();
        }
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_to_active_and_retiring_fed_and_unknown_address(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(minimumPeginTxValue, retiringFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, userAddress);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue.multiply(2)), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertEquals(1, retiringFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_v1_to_retiring_fed_can_be_registered(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());
        btcTransaction.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRsk(
                1,
                bridgeContractAddress,
                Optional.empty()
            )
        );

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(1));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertFalse(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_v1_two_rsk_op_return_cannot_be_registered(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());
        btcTransaction.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRsk(
                1,
                bridgeContractAddress,
                Optional.empty()
            )
        );
        btcTransaction.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRsk(
                1,
                bridgeContractAddress,
                Optional.empty()
            )
        );

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, PEGIN_V1_INVALID_PAYLOAD);
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_v1_invalid_protocol_legacy_sender_to_active_fed_(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());
        btcTransaction.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRsk(
                2,
                bridgeContractAddress,
                Optional.empty()
            )
        );

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, PEGIN_V1_INVALID_PAYLOAD);

        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertTrue(activeFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_v1_invalid_prefix_to_active_fed_can_be_registered(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());
        btcTransaction.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptWithInvalidPrefix(
                1,
                bridgeContractAddress,
                Optional.empty()
            )
        );

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_v1_segwit_to_retiring_fed_can_be_registered(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());
        btcTransaction.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRsk(
                1,
                bridgeContractAddress,
                Optional.empty()
            )
        );

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{0x1});
        btcTransaction.setWitness(0, txWitness);

        createPmtAndMockBlockStore(btcTransaction, height);

        PartialMerkleTree pmtWithWitness = createPmtWithWitness(btcTransaction);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmtWithWitness.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(1));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertFalse(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_v1_to_active_fed_with_invalid_payload_and_unknown_sender_cannot_be_processed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"key1", "key2", "key3"}, true);

        Federation unknownFed = createFederation(bridgeMainnetConstants, signers);

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            new Script(new byte[]{})
        );

        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.ZERO, PegTestUtils.createOpReturnScriptForRskWithCustomPayload(1, new byte[]{}));

        FederationTestUtils.addSignatures(unknownFed, signers, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (activations == fingerrootActivations){
            assertLegacyUndeterminedSenderPeginIsRejectedAsPeginV1InvalidPayloadBeforeRSKIP379(btcTransaction);
        } else {
            assertInvalidPeginV1UndeterminedSenderIsRejected(btcTransaction);
        }
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_from_multisig_to_retiring_fed_can_be_refunded(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"key1", "key2", "key3"}, true);

        Federation unknownFed = createFederation(bridgeMainnetConstants, signers);

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
        );
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());

        FederationTestUtils.addSignatures(unknownFed, signers, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        assertPeginIsRejectedAndRefunded(activations, btcTransaction, amountToSend, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER);
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_to_retiring_fed_cannot_be_processed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"key1", "key2", "key3"}, true);

        Federation unknownFed = createFederation(bridgeMainnetConstants, signers);

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
        );
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());

        FederationTestUtils.addSignatures(unknownFed, signers, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (activations == fingerrootActivations){
            assertLegacyUndeterminedSenderPeginIsRejectedAsPeginV1InvalidPayloadBeforeRSKIP379(btcTransaction);
        } else {
            assertLegacyUndeterminedSenderPeginIsRejected(btcTransaction);
        }
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void pegin_legacy_from_segwit_to_active_fed_cannot_be_processed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        List<BtcECKey> unknownFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"key1", "key2", "key3"}, true);

        Federation unknownFed = createFederation(bridgeMainnetConstants, unknownFedSigners);

        BtcTransaction fundingTx = new BtcTransaction(btcMainnetParams);

        fundingTx.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
        );
        fundingTx.addOutput(PegTestUtils.createBech32Output(btcMainnetParams, Coin.COIN));
        FederationTestUtils.addSignatures(unknownFed, unknownFedSigners, fundingTx);

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(fundingTx.getOutput(FIRST_OUTPUT_INDEX));
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());

        FederationTestUtils.addSignatures(unknownFed, unknownFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert

        // SINCE RSKIP379 ONLY TRANSACTIONS THAT REALLY ARE PROCESSED, REFUNDS OR REGISTER WILL BE MARK AS PROCESSED.
        if (activations == fingerrootActivations){
            assertLegacyUndeterminedSenderPeginIsRejectedAsPeginV1InvalidPayloadBeforeRSKIP379(btcTransaction);
        } else {
            assertLegacyUndeterminedSenderPeginIsRejected(btcTransaction);
        }
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void invalid_pegin_v1_from_multisig_to_active_fed_can_be_refunded(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"key1", "key2", "key3"}, true);

        Federation unknownFed = createFederation(bridgeMainnetConstants, signers);

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
        );

        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());

        btcTransaction.addOutput(Coin.ZERO, PegTestUtils.createOpReturnScriptForRskWithCustomPayload(1, new byte[]{}));

        FederationTestUtils.addSignatures(unknownFed, signers, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        assertPeginIsRejectedAndRefunded(activations, btcTransaction, Coin.COIN, PEGIN_V1_INVALID_PAYLOAD);
    }

    // Pegout tests

    @ParameterizedTest
    @MethodSource("common_args")
    void pegout_no_change_output(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
        );
        btcTransaction.addOutput(Coin.COIN, userAddress);

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @Test
    void pegout_sighash_no_exists_in_provider() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = heightAtWhichToStartUsingPegoutIndex;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        Coin amountToSend = Coin.COIN;
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
        );
        btcTransaction.addOutput(Coin.COIN, userAddress);
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, arrowhead600Activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(arrowhead600Activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, LEGACY_PEGIN_MULTISIG_SENDER);
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(eq(rskTx.getHash().getBytes()), any(BtcTransaction.class), eq(amountToSend));

        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());

        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegout_many_outputs_and_inputs_with_change_output(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(
                BitcoinTestUtils.createHash(i + 1),
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
            );
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 10));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 20));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.COIN), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 30));
        }

        btcTransaction.addOutput(quarterMinimumPegoutTxValue, activeFederation.getAddress());

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
                FIRST_INPUT_INDEX,
                activeFederation.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegout_many_outputs_and_one_input(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
        );

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 10));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 20));
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.COIN), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 30));
        }

        btcTransaction.addOutput(quarterMinimumPegoutTxValue, activeFederation.getAddress());

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
                FIRST_INPUT_INDEX,
                activeFederation.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegout_one_output_and_many_input(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(
                BitcoinTestUtils.createHash(i + 1),
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
            );
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        btcTransaction.addOutput(minimumPegoutTxValue, userAddress);

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
                FIRST_INPUT_INDEX,
                activeFederation.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    // Migration tests

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void migration_ok(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        Sha256Hash fundTxHash = BitcoinTestUtils.createHash(1);
        btcTransaction.addInput(fundTxHash, FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiringFederation.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiringFederation.getRedeemScript());
        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        when(federationStorageProvider.getLastRetiredFederationP2SHScript(activations)).thenReturn(Optional.ofNullable(inputScript));

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @Test
    void migration_sighash_no_exists_in_provider() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = heightAtWhichToStartUsingPegoutIndex;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        Coin amountToSend = Coin.COIN;
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, retiringFederation.getRedeemScript())
        );
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, arrowhead600Activations)).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(arrowhead600Activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, LEGACY_PEGIN_MULTISIG_SENDER);
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(eq(rskTx.getHash().getBytes()), any(BtcTransaction.class), eq(amountToSend));
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());

        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void migration_many_outputs_and_inputs(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(
                BitcoinTestUtils.createHash(i),
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, retiringFederation.getRedeemScript())
            );
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.COIN), activeFederation.getAddress());
        }

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(40, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void migration_many_outputs_and_one_input(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, retiringFederation.getRedeemScript())
        );

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
        }

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPegoutTxValue.add(Coin.COIN), activeFederation.getAddress());
        }

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(40, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void migration_one_outputs_and_many_input(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        for (int i = 0; i < 50; i++) {
            btcTransaction.addInput(
                BitcoinTestUtils.createHash(i),
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, retiringFederation.getRedeemScript())
            );
        }

        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        btcTransaction.addOutput(minimumPegoutTxValue, activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    // flyover pegin
    @ParameterizedTest
    @MethodSource("btc_transaction_sending_funds_to_unknown_address_args")
    void flyover_pegin(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;
        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        BtcECKey senderBtcKey = new BtcECKey();
        ECKey senderRskKey = ECKey.fromPublicOnly(senderBtcKey.getPubKey());
        RskAddress rskAddress = new RskAddress(senderRskKey.getAddress());
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin amountToSend = shouldSendAmountBelowMinimum ? belowMinimumPeginTxValue : minimumPeginTxValue;
        btcTransaction.addOutput(amountToSend, flyoverFederationAddress);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        // fingerroot - unknown tx should be processed and try to register
        if (activations == fingerrootActivations) {
            assertUnknownTxIsProcessedAsPegin(rskAddress, btcTransaction, 0);
        }
        // arrowhead600Activations but before grace period - unknown tx should be rejected
        else if (activations == arrowhead600Activations && !shouldUsePegoutTxIndex) {
            assertUnknownTxIsRejectedWithInvalidAmountReason(btcTransaction);
        }
        // arrowhead600Activations and after grace period - unknown tx are just ignored
        else {
            assertUnknownTxIsIgnored();
        }
    }

    @ParameterizedTest
    @MethodSource("btc_transaction_sending_funds_to_unknown_address_args")
    void flyover_segwit_pegin(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;
        if (existsRetiringFederation) {
            when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);
        }

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        BtcECKey senderBtcKey = new BtcECKey();
        ECKey senderRskKey = ECKey.fromPublicOnly(senderBtcKey.getPubKey());
        RskAddress rskAddress = new RskAddress(senderRskKey.getAddress());
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin amountToSend = shouldSendAmountBelowMinimum ? belowMinimumPeginTxValue : minimumPeginTxValue;
        btcTransaction.addOutput(amountToSend, flyoverFederationAddress);

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{0x1});
        btcTransaction.setWitness(0, txWitness);

        createPmtAndMockBlockStore(btcTransaction, height);

        PartialMerkleTree pmtWithWitness = createPmtWithWitness(btcTransaction);
        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmtWithWitness.bitcoinSerialize()
        );

        // assert
        // fingerroot - unknown tx should be processed and try to register
        if (activations == fingerrootActivations) {
            assertUnknownTxIsProcessedAsPegin(rskAddress, btcTransaction, 0);
        }
        // arrowhead600Activations but before grace period - unknown tx should be rejected
        else if (activations == arrowhead600Activations && !shouldUsePegoutTxIndex) {
            assertUnknownTxIsRejectedWithInvalidAmountReason(btcTransaction);
        }
        // arrowhead600Activations and after grace period - unknown tx are just ignored
        else {
            assertUnknownTxIsIgnored();
        }
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void flyover_segwit_as_migration_utxo(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;
        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction fundingTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        BtcECKey senderBtcKey = new BtcECKey();
        fundingTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin amountToSend = minimumPeginTxValue;
        fundingTx.addOutput(amountToSend, flyoverFederationAddress);
        fundingTx.addOutput(createBech32Output(bridgeMainnetConstants.getBtcParams(), amountToSend));

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTransaction.addInput(fundingTx.getOutput(0)).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(retiringFederation));
        btcTransaction.addOutput(Coin.COIN.multiply(10), activeFederation.getAddress());

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{0x1});
        btcTransaction.setWitness(0, txWitness);

        createPmtAndMockBlockStore(btcTransaction, height);

        PartialMerkleTree pmtWithWitness = createPmtWithWitness(btcTransaction);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmtWithWitness.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void flyover_segwit_as_migration_utxo_with_many_outputs_and_inputs(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;
        when(federationStorageProvider.getOldFederation(federationMainnetConstants, activations)).thenReturn(retiringFederation);

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction fundingTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        BtcECKey senderBtcKey = new BtcECKey();
        fundingTx.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin amountToSend = minimumPeginTxValue;
        fundingTx.addOutput(amountToSend, flyoverFederationAddress);
        fundingTx.addOutput(createBech32Output(bridgeMainnetConstants.getBtcParams(), amountToSend));

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTransaction.addInput(fundingTx.getOutput(0)).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(retiringFederation));
        btcTransaction.addOutput(Coin.COIN.multiply(10), activeFederation.getAddress());

        for (int i = 0; i < 9; i++) {
            BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
            btcTx.addInput(BitcoinTestUtils.createHash(i), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
            btcTx.addOutput(Coin.COIN, retiringFederation.getAddress());
            btcTransaction.addInput(btcTx.getOutput(0)).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(retiringFederation));
            btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        }

        FederationTestUtils.addSignatures(retiringFederation, retiringFedSigners, btcTransaction);

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{0x1});
        btcTransaction.setWitness(0, txWitness);

        createPmtAndMockBlockStore(btcTransaction, height);

        PartialMerkleTree pmtWithWitness = createPmtWithWitness(btcTransaction);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiringFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmtWithWitness.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(10, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    // old fed
    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void old_fed_migration(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
        NetworkParameters btcRegTestsParams = bridgeRegTestConstants.getBtcParams();
        Context.propagate(new Context(btcRegTestsParams));

        final List<BtcECKey> regtestOldFederationPrivateKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("47129ffed2c0273c75d21bb8ba020073bb9a1638df0e04853407461fdd9e8b83")),
            BtcECKey.fromPrivate(Hex.decode("9f72d27ba603cfab5a0201974a6783ca2476ec3d6b4e2625282c682e0e5f1c35")),
            BtcECKey.fromPrivate(Hex.decode("e1b17fcd0ef1942465eee61b20561b16750191143d365e71de08b33dd84a9788"))
        );
        when(provider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(whitelistStorageProvider.getLockWhitelist(lovell700Activations, btcMainnetParams)).thenReturn(lockWhitelist);

        when(federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestsParams, activations)).thenReturn(activeFederationUtxos);

        pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        Federation oldFederation = createFederation(bridgeRegTestConstants, regtestOldFederationPrivateKeys);

        BtcTransaction migrationTx = new BtcTransaction(btcRegTestsParams);
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(null, oldFederation.getRedeemScript());
        migrationTx.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            inputScript
        );
        migrationTx.addOutput(Coin.COIN, activeFederation.getAddress());

        when(federationStorageProvider.getLastRetiredFederationP2SHScript(activations)).thenReturn(Optional.ofNullable(inputScript));

        FederationTestUtils.addSignatures(oldFederation, regtestOldFederationPrivateKeys, migrationTx);

        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestsParams, new byte[]{0x3f}, Collections.singletonList(migrationTx.getHash()), 1);
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());

        registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestsParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestsParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(
            headBlock,
            new BigInteger("0"),
            height + BridgeSupportRegisterBtcTransactionTest.bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations()
        );
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestsParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + BridgeSupportRegisterBtcTransactionTest.bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        FeePerKbSupport feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(bridgeRegTestConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(rskExecutionBlock)
            .withActivations(activations)
            .build();

        // act
        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeRegTestConstants)
            .withProvider(provider)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .withFeePerKbSupport(feePerKbSupport)
            .withFederationSupport(federationSupport)
            .build();

        bridgeSupport.registerBtcTransaction(
            rskTx,
            migrationTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, never()).logUnrefundablePegin(migrationTx, LEGACY_PEGIN_UNDETERMINED_SENDER);
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        assertTrue(retiringFederationUtxos.isEmpty());
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(migrationTx.getHash(false), rskExecutionBlock.getNumber());

        if (shouldUsePegoutTxIndex) {
            verify(bridgeEventLogger, times(1)).logRejectedPegin(
                migrationTx, LEGACY_PEGIN_MULTISIG_SENDER
            );
            verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(
                eq(rskTx.getHash().getBytes()),
                any(BtcTransaction.class),
                eq(Coin.COIN)
            );
            assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());
            assertTrue(activeFederationUtxos.isEmpty());
        } else {
            verify(bridgeEventLogger, never()).logRejectedPegin(
                any(), any()
            );
            verify(bridgeEventLogger, never()).logReleaseBtcRequested(
                any(),
                any(),
                any()
            );
            assertEquals(1, activeFederationUtxos.size());
        }
    }

    // retired fed

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void last_retired_fed_to_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiredFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiredFed.getRedeemScript());
        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        FederationTestUtils.addSignatures(retiredFed, retiredFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiredFed.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        when(federationStorageProvider.getLastRetiredFederationP2SHScript(activations)).thenReturn(Optional.of(retiredFed.getP2SHScript()));

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsAndShouldUsePegoutIndexArgs")
    void no_last_retired_fed_in_storage_sending_funds_to_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? heightAtWhichToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());

        Script p2SHScript = ScriptBuilder.createP2SHOutputScript(retiredFed.getRedeemScript());
        Script inputScript = p2SHScript.createEmptyInputScript(null, retiredFed.getRedeemScript());
        btcTransaction.getInput(FIRST_INPUT_INDEX).setScriptSig(inputScript);

        FederationTestUtils.addSignatures(retiredFed, retiredFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            retiredFed.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (shouldUsePegoutTxIndex) {
            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
            verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
            verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
            verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());
            verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
            assertEquals(1, activeFederationUtxos.size());
            assertTrue(retiringFederationUtxos.isEmpty());
        } else {
            assertPeginIsRejectedAndRefunded(activations, btcTransaction, Coin.COIN, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("svp fund transaction registration tests")
    class SvpFundTxRegistrationTests {
        private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
        private final Coin spendableValueFromProposedFederation = bridgeMainnetConstants.getSpendableValueFromProposedFederation();

        private Repository repository;
        private PartialMerkleTree pmtWithTransactions;
        private int btcBlockWithPmtHeight;

        private Federation proposedFederation;

        private BridgeSupport bridgeSupport;
        private FederationSupport federationSupport;
        private FeePerKbSupport feePerKbSupport;

        @BeforeEach
        void setUp() {
            long rskExecutionBlockNumber = 1000L;
            long rskExecutionBlockTimestamp = 10L;
            BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
                .setNumber(rskExecutionBlockNumber)
                .setTimestamp(rskExecutionBlockTimestamp)
                .build();
            rskExecutionBlock = Block.createBlockFromHeader(blockHeader, true);

            activeFederation = FederationTestUtils.getErpFederation(federationMainnetConstants.getBtcParams());
            List<UTXO> activeFederationUTXOs = BitcoinTestUtils.createUTXOs(10, activeFederation.getAddress());
            proposedFederation = P2shErpFederationBuilder.builder().build();

            federationSupport = mock(FederationSupport.class);
            when(federationSupport.getActiveFederation()).thenReturn(activeFederation);
            when(federationSupport.getActiveFederationAddress()).thenReturn(activeFederation.getAddress());
            when(federationSupport.getActiveFederationBtcUTXOs()).thenReturn(activeFederationUTXOs);
            when(federationSupport.getProposedFederation()).thenReturn(Optional.of(proposedFederation));

            feePerKbSupport = mock(FeePerKbSupport.class);
            Coin feePerKb = Coin.valueOf(1000);
            when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);

            Keccak256 rskTxHash = PegTestUtils.createHash3(1);
            rskTx = mock(Transaction.class);
            when(rskTx.getHash()).thenReturn(rskTxHash);

            repository = createRepository();
            provider = new BridgeStorageProvider(
                repository,
                bridgeContractAddress,
                btcMainnetParams,
                allActivations
            );

            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainnetConstants)
                .withProvider(provider)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .build();
        }

        @Test
        void registerBtcTransaction_forSvpFundTransactionChange_whenProposedFederationDoesNotExist_shouldRegisterTransactionButNotUpdateSvpFundTransactionValues() throws Exception {
            // arrange
            BtcTransaction svpFundTransaction = arrangeSvpFundTransactionWithChange();

            signInputs(svpFundTransaction); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(svpFundTransaction);

            when(federationSupport.getProposedFederation()).thenReturn(Optional.empty());

            // act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpFundTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            provider.save();

            // assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(svpFundTransaction.getHash());
            assertSvpFundTransactionValuesWereNotUpdated();
        }

        @Test
        void registerBtcTransaction_forSvpFundTransactionChange_whenValidationPeriodEnded_shouldRegisterTransactionButNotUpdateSvpFundTransactionValues() throws Exception {
            // arrange
            // make rsk execution block to be after validation period ended
            long validationPeriodEndBlock = proposedFederation.getCreationBlockNumber()
                + bridgeMainnetConstants.getFederationConstants().getValidationPeriodDurationInBlocks();
            long rskExecutionBlockTimestamp = 10L;
            BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
                .setNumber(validationPeriodEndBlock + 1) // adding one more block to ensure validation period is ended
                .setTimestamp(rskExecutionBlockTimestamp)
                .build();
            rskExecutionBlock = Block.createBlockFromHeader(blockHeader, true);

            BtcTransaction svpFundTransaction = arrangeSvpFundTransactionWithChange();
            signInputs(svpFundTransaction); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(svpFundTransaction);

            // Act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpFundTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            provider.save();

            // assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(svpFundTransaction.getHash());
            assertSvpFundTransactionValuesWereNotUpdated();
        }

        @Test
        void registerBtcTransaction_forNormalPegout_whenSvpPeriodIsOngoing_shouldRegisterTransactionButNotUpdateSvpFundTransactionValues() throws Exception {
            // Arrange
            arrangeSvpFundTransactionWithChange();

            BtcTransaction pegout = createPegout();
            savePegoutIndex(pegout);
            signInputs(pegout); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(pegout);

            // Act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                pegout.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            provider.save();

            // assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(pegout.getHash());
            assertSvpFundTransactionValuesWereNotUpdated();
        }

        @Test
        void registerBtcTransaction_forSvpFundTransactionWithoutChangeOutput_whenSvpPeriodIsOngoing_shouldJustUpdateSvpFundTransactionValues() throws Exception {
            // Arrange
            BtcTransaction svpFundTransaction = createSvpFundTransaction();
            saveSvpFundTransactionUnsigned(svpFundTransaction);

            signInputs(svpFundTransaction); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(svpFundTransaction);

            // Act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpFundTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            provider.save();

            // Assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx);
            assertTransactionWasProcessed(svpFundTransaction.getHash());
            assertSvpFundTransactionValuesWereUpdated();
        }

        @Test
        void registerBtcTransaction_forSvpFundTransactionChange_whenSvpPeriodIsOngoing_shouldRegisterTransactionAndUpdateSvpFundTransactionValues() throws Exception {
            // Arrange
            BtcTransaction svpFundTransaction = arrangeSvpFundTransactionWithChange();

            signInputs(svpFundTransaction); // a transaction trying to be registered should be signed
            setUpForTransactionRegistration(svpFundTransaction);

            // Act
            int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
            bridgeSupport.registerBtcTransaction(
                rskTx,
                svpFundTransaction.bitcoinSerialize(),
                btcBlockWithPmtHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            provider.save();

            // Assert
            assertActiveFederationUtxosSize(activeFederationUtxosSizeBeforeRegisteringTx + 1);
            assertTransactionWasProcessed(svpFundTransaction.getHash());
            assertSvpFundTransactionValuesWereUpdated();
        }

        private BtcTransaction arrangeSvpFundTransactionWithChange() {
            BtcTransaction svpFundTransaction = createSvpFundTransaction();
            addOutputChange(svpFundTransaction);
            saveSvpFundTransactionUnsigned(svpFundTransaction);

            return svpFundTransaction;
        }

        private BtcTransaction createSvpFundTransaction() {
            BtcTransaction svpFundTransaction = new BtcTransaction(btcMainnetParams);

            Sha256Hash parentTxHash = BitcoinTestUtils.createHash(1);
            addInput(svpFundTransaction, parentTxHash);

            svpFundTransaction.addOutput(spendableValueFromProposedFederation, proposedFederation.getAddress());
            Address flyoverProposedFederationAddress =
                PegUtils.getFlyoverAddress(btcMainnetParams, bridgeMainnetConstants.getProposedFederationFlyoverPrefix(), proposedFederation.getRedeemScript());
            svpFundTransaction.addOutput(spendableValueFromProposedFederation, flyoverProposedFederationAddress);

            return svpFundTransaction;
        }

        private void saveSvpFundTransactionUnsigned(BtcTransaction svpFundTransaction) {
            savePegoutIndex(svpFundTransaction);
            saveSvpFundTransactionHashUnsigned(svpFundTransaction.getHash());
        }

        private BtcTransaction createPegout() {
            BtcTransaction pegout = new BtcTransaction(btcMainnetParams);
            Sha256Hash parentTxHash = BitcoinTestUtils.createHash(2);
            addInput(pegout, parentTxHash);
            addOutputChange(pegout);

            return pegout;
        }

        private void addInput(BtcTransaction transaction, Sha256Hash parentTxHash) {
            // we need to add an input that we can actually sign,
            // and we know the private keys for the following scriptSig
            Federation federation = P2shErpFederationBuilder.builder().build();
            Script scriptSig = federation.getP2SHScript().createEmptyInputScript(null, federation.getRedeemScript());

            transaction.addInput(parentTxHash, 0, scriptSig);
        }

        private void signInputs(BtcTransaction transaction) {
            List<BtcECKey> keysToSign =
                BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"member01", "member02", "member03", "member04", "member05"}, true);
            List<TransactionInput> inputs = transaction.getInputs();
            for (TransactionInput input : inputs) {
                BitcoinTestUtils.signTransactionInputFromP2shMultiSig(transaction, inputs.indexOf(input), keysToSign);
            }
        }

        private void addOutputChange(BtcTransaction transaction) {
            // add output to the active fed
            Script activeFederationP2SHScript = activeFederation.getP2SHScript();
            transaction.addOutput(Coin.COIN.multiply(10), activeFederationP2SHScript);
        }

        private void savePegoutIndex(BtcTransaction pegout) {
            BitcoinUtils.getFirstInputSigHash(pegout)
                .ifPresent(inputSigHash -> provider.setPegoutTxSigHash(inputSigHash));
        }

        private void saveSvpFundTransactionHashUnsigned(Sha256Hash svpFundTransactionHashUnsigned) {
            provider.setSvpFundTxHashUnsigned(svpFundTransactionHashUnsigned);
            bridgeSupport.save();
        }

        private void setUpForTransactionRegistration(BtcTransaction transaction) throws BlockStoreException {
            // recreate a valid chain that has the tx, so it passes the previous checks in registerBtcTransaction
            BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(btcMainnetParams, 100, 100);
            BtcBlockStoreWithCache btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(repository, bridgeMainnetConstants, provider, allActivations);

            pmtWithTransactions = createValidPmtForTransactions(Collections.singletonList(transaction.getHash()), btcMainnetParams);
            btcBlockWithPmtHeight = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeMainnetConstants.getPegoutTxIndexGracePeriodInBtcBlocks(); // we want pegout tx index to be activated

            int chainHeight = btcBlockWithPmtHeight + bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations();
            recreateChainFromPmt(btcBlockStoreWithCache, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcMainnetParams);

            provider.save();

            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainnetConstants)
                .withProvider(provider)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(rskExecutionBlock)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withRepository(repository)
                .build();
        }

        private void assertActiveFederationUtxosSize(int expectedActiveFederationUtxosSize) {
            assertEquals(expectedActiveFederationUtxosSize, federationSupport.getActiveFederationBtcUTXOs().size());
        }

        private void assertTransactionWasProcessed(Sha256Hash transactionHash) throws IOException {
            Optional<Long> rskBlockHeightAtWhichBtcTxWasProcessed = provider.getHeightIfBtcTxhashIsAlreadyProcessed(transactionHash);
            assertTrue(rskBlockHeightAtWhichBtcTxWasProcessed.isPresent());

            long rskExecutionBlockNumber = rskExecutionBlock.getNumber();
            assertEquals(rskExecutionBlockNumber, rskBlockHeightAtWhichBtcTxWasProcessed.get());
        }

        private void assertSvpFundTransactionValuesWereNotUpdated() {
            assertTrue(provider.getSvpFundTxHashUnsigned().isPresent());
            assertFalse(provider.getSvpFundTxSigned().isPresent());
        }

        private void assertSvpFundTransactionValuesWereUpdated() {
            Optional<BtcTransaction> svpFundTransactionSignedOpt = provider.getSvpFundTxSigned();
            assertTrue(svpFundTransactionSignedOpt.isPresent());

            assertFalse(provider.getSvpFundTxHashUnsigned().isPresent());
        }
    }
}

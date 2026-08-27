package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.PegTestUtils.*;
import static co.rsk.peg.bitcoin.BitcoinUtils.addSpendingFederationBaseScript;
import static co.rsk.peg.bitcoin.BitcoinUtils.createBaseInputScriptThatSpendsFromRedeemScript;
import static co.rsk.peg.bitcoin.BitcoinUtils.getSigHashForPegoutIndex;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static co.rsk.peg.federation.FederationTestUtils.addSignatures;
import static co.rsk.peg.federation.FederationTestUtils.addSignaturesAndFederationRedeemScript;
import static co.rsk.peg.pegin.RejectedPeginReason.*;
import static co.rsk.peg.utils.NonRefundablePeginReason.OUTPUTS_SENT_TO_DIFFERENT_TYPES_OF_FEDS;
import static co.rsk.peg.utils.NonRefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.*;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.utils.NonRefundablePeginReason;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.WhitelistStorageProvider;
import co.rsk.peg.whitelist.WhitelistSupportImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.test.builders.PegoutTransactionBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BridgeSupportRegisterBtcTransactionTest {
    private static final ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
    private static final ActivationConfig.ForBlock arrowhead600Activations = ActivationConfigsForTest.arrowhead600().forBlock(0);
    private static final ActivationConfig.ForBlock lovell700Activations = ActivationConfigsForTest.lovell700().forBlock(0);
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);

    private static final RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final FederationConstants federationMainnetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(ActivationConfigsForTest.all().forBlock(0));
    private static final Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);
    private static final int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
    private static final int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
    private static final int heightAtWhichToStartUsingPegoutIndex = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
    private static final int heightBeforeUsingPegoutIndex = 1;
    private static final Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");
    private static final Instant federationCreationTime = Instant.ofEpochMilli(1000L);

    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;
    private static final Sha256Hash BTC_TX_HASH = BitcoinTestUtils.createHash(1);
    private static final Sha256Hash BTC_BLOCK_HASH = BitcoinTestUtils.createHash(999);

    private final RskAddress destinationRskAddress = RskTestUtils.generateAddress("rskAddress");
    private final Script opReturnScript = PegTestUtils.createOpReturnScriptForRsk(
        1,
        destinationRskAddress,
        Optional.empty()
    );

    private final Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userRefundBtcAddress");
    private final Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "lpBtcAddress");
    private final Keccak256 derivationArgumentsHash = RskTestUtils.createHash(0);
    private final RskAddress lbcAddress = RskTestUtils.generateAddress("seed");

    private BridgeConstants bridgeConstants;
    private NetworkParameters networkParameters;

    private BridgeStorageProvider bridgeStorageProvider;
    private WhitelistStorageProvider whitelistStorageProvider;
    private FederationStorageProvider federationStorageProvider;
    private FederationSupport federationSupport;

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

    private long rskExecutionBlockNumber;
    private Block rskExecutionBlock;
    private Transaction rskTx;

    private co.rsk.bitcoinj.core.BtcBlock registerHeader;

    private BtcBlockStoreWithCache btcBlockStore;
    private BridgeSupport bridgeSupport;
    private FeePerKbSupport feePerKbSupport;

    private List<LogInfo> logs;

    private void assertPeginIsRejectedAndRefunded(ActivationConfig.ForBlock activations, BtcTransaction btcTransaction, Coin sentAmount, RejectedPeginReason expectedRejectedPeginReason) throws IOException {
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());

        assertEquals(1, pegoutsWaitingForConfirmations.getEntries(activations).size());
        Entry pegoutWaitingForConfirmationEntry = pegoutsWaitingForConfirmations.getEntries(activations).stream().findFirst().get();
        BtcTransaction refundPegout = pegoutWaitingForConfirmationEntry.getBtcTransaction();
        Sha256Hash refundPegoutHash = refundPegout.getHash();
        List<Coin> refundPegoutOutpointValues = extractOutpointValues(refundPegout);

        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, expectedRejectedPeginReason);
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(rskTx.getHash().getBytes(), refundPegout, sentAmount);

        verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        verify(bridgeStorageProvider, never()).setPegoutTxSigHash(any());

        if(activations == allActivations) {
            verify(bridgeEventLogger, times(1)).logPegoutTransactionCreated(refundPegoutHash, refundPegoutOutpointValues);
        } else {
            verify(bridgeEventLogger, never()).logPegoutTransactionCreated(any(), any());
        }
    }

    private void assertUtxoWasRegistered(BtcTransaction btcTransaction) throws IOException {
        verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        assertEquals(1, activeFederationUtxos.size());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    private PartialMerkleTree createPmtWithWitness(BtcTransaction btcTx) {
        List<Sha256Hash> hashesWithWitness = new ArrayList<>();
        hashesWithWitness.add(btcTx.getHash(true));
        byte[] bitsWithWitness = new byte[1];
        bitsWithWitness[0] = 0x3f;
        PartialMerkleTree partialMerkleTreeWithWitness = new PartialMerkleTree(btcMainnetParams, bitsWithWitness, hashesWithWitness, 1);
        Sha256Hash witnessMerkleRoot = partialMerkleTreeWithWitness.getTxnHashAndMerkleRoot(new ArrayList<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        when(bridgeStorageProvider.getCoinbaseInformation(any())).thenReturn(coinbaseInformation);
        return partialMerkleTreeWithWitness;
    }

    private PartialMerkleTree createPmtAndMockBlockStore(BtcTransaction btcTransaction, int height) throws BlockStoreException {
        PartialMerkleTree pmt = new PartialMerkleTree(btcMainnetParams, new byte[]{0x3f}, Collections.singletonList(btcTransaction.getHash()), 1);
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());

        registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcMainnetParams,
            1,
            BTC_BLOCK_HASH,
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
            BTC_BLOCK_HASH,
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

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(rskExecutionBlock)
            .build();

        return BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withRepository(repository)
            .withProvider(bridgeStorageProvider)
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

    @BeforeEach
    void init() throws IOException {
        retiredFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        retiredFed = createFederation(bridgeMainnetConstants, retiredFedSigners);

        retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa04", "fa05", "fa06"}, true
        );

        List<FederationMember> retiringFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(retiringFedSigners);
        long retiringFedCreationBlockNumber = 1;
        List<BtcECKey> erpPubKeys = federationMainnetConstants.getErpFedPubKeysList();
        long activationDelay = federationMainnetConstants.getErpFedActivationDelay();

        FederationArgs retiringFedArgs =
            new FederationArgs(retiringFedMembers, federationCreationTime, retiringFedCreationBlockNumber, btcMainnetParams);
        retiringFederation = FederationFactory.buildP2shErpFederation(retiringFedArgs, erpPubKeys, activationDelay);

        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa07", "fa08", "fa09", "fa10", "fa11"}, true
        );
        activeFedSigners.sort(BtcECKey.PUBKEY_COMPARATOR);
        List<FederationMember> activeFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(activeFedSigners);
        long activeFedCreationBlockNumber = 2L;
        FederationArgs activeFedArgs =
            new FederationArgs(activeFedMembers, federationCreationTime, activeFedCreationBlockNumber, btcMainnetParams);
        activeFederation = FederationFactory.buildP2shErpFederation(activeFedArgs, erpPubKeys, activationDelay);

        mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        bridgeEventLogger = mock(BridgeEventLogger.class);
        btcLockSenderProvider = new BtcLockSenderProvider();

        peginInstructionsProvider = new PeginInstructionsProvider();

        bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        whitelistStorageProvider = mock(WhitelistStorageProvider.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(whitelistStorageProvider.getLockWhitelist(allActivations, btcMainnetParams)).thenReturn(lockWhitelist);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getOldFederationBtcUTXOs())
            .thenReturn(retiringFederationUtxos);
        when(federationStorageProvider.getNewFederationBtcUTXOs(any(NetworkParameters.class), any(ActivationConfig.ForBlock.class)))
            .thenReturn(activeFederationUtxos);

        pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(bridgeStorageProvider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

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
    }

    @Nested
    class UnknownTransaction {
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

        // fingerroot
        private void assertUnknownTxIsProcessedAsPegin(RskAddress expectedRskAddressToBeLogged, BtcTransaction btcTransaction, int protocolVersion) throws IOException {
            verify(bridgeEventLogger, times(1)).logPeginBtc(expectedRskAddressToBeLogged, btcTransaction, Coin.ZERO, protocolVersion);
            verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());
            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
            assertTrue(activeFederationUtxos.isEmpty());
            assertTrue(retiringFederationUtxos.isEmpty());
        }

        // After arrowhead600Activations but before grace period
        private void assertUnknownTxIsRejectedWithInvalidAmountReason(BtcTransaction btcTransaction) throws IOException {
            verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, INVALID_AMOUNT);
            verify(bridgeEventLogger, times(1)).logNonRefundablePegin(btcTransaction, NonRefundablePeginReason.INVALID_AMOUNT);
            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
            verify(bridgeStorageProvider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
            assertTrue(activeFederationUtxos.isEmpty());
            assertTrue(retiringFederationUtxos.isEmpty());
        }

        // After arrowhead600Activations and grace period
        private void assertUnknownTxIsIgnored() throws IOException {
            verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());
            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
            verify(bridgeStorageProvider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
            assertTrue(activeFederationUtxos.isEmpty());
            assertTrue(retiringFederationUtxos.isEmpty());
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

            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

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

            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

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

            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

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

            RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

            BridgeSupport bridgeSupport = buildBridgeSupport(activations);
            Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
                derivationArgumentsHash,
                userRefundBtcAddress,
                lpBtcAddress,
                lbcAddress,
                activations
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

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

            RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

            BridgeSupport bridgeSupport = buildBridgeSupport(activations);
            Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
                derivationArgumentsHash,
                userRefundBtcAddress,
                lpBtcAddress,
                lbcAddress,
                activations
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

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
    }

    @Nested
    class PeginTransaction {

        // Before arrowhead600Activations is activated
        private void assertLegacyUndeterminedSenderPeginIsRejectedAsPeginV1InvalidPayloadBeforeRSKIP379(BtcTransaction btcTransaction) throws IOException {
            verify(bridgeEventLogger, times(1)).logRejectedPegin(
                btcTransaction, PEGIN_V1_INVALID_PAYLOAD
            );
            verify(bridgeEventLogger, times(1)).logNonRefundablePegin(
                btcTransaction,
                LEGACY_PEGIN_UNDETERMINED_SENDER
            );

            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());

            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
            verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());
            verify(bridgeEventLogger, never()).logPegoutTransactionCreated(any(), any());

            assertTrue(activeFederationUtxos.isEmpty());
            assertTrue(retiringFederationUtxos.isEmpty());
            assertTrue(pegoutsWaitingForConfirmations.getEntries(allActivations).isEmpty());
        }

        // After arrowhead600Activations is activated
        private void assertLegacyUndeterminedSenderPeginIsRejected(BtcTransaction btcTransaction,
                                                                   ForBlock activations) throws IOException {

            assertInvalidPeginMarkedAsProcessed(activations);

            verify(bridgeEventLogger, times(1)).logRejectedPegin(
                btcTransaction, RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER
            );
            verify(bridgeEventLogger, times(1)).logNonRefundablePegin(
                btcTransaction,
                LEGACY_PEGIN_UNDETERMINED_SENDER
            );

            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
            verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());

            Assertions.assertTrue(activeFederationUtxos.isEmpty());
            Assertions.assertTrue(retiringFederationUtxos.isEmpty());
            Assertions.assertTrue(pegoutsWaitingForConfirmations.getEntries(activations).isEmpty());
        }

        private void assertInvalidPeginV1UndeterminedSenderIsRejected(BtcTransaction btcTransaction,
                                                                      ForBlock activations) throws IOException {

            assertInvalidPeginMarkedAsProcessed(activations);

            verify(bridgeEventLogger, times(1)).logRejectedPegin(
                btcTransaction, PEGIN_V1_INVALID_PAYLOAD
            );
            verify(bridgeEventLogger, times(1)).logNonRefundablePegin(
                btcTransaction,
                LEGACY_PEGIN_UNDETERMINED_SENDER
            );

            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
            verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());
            verify(bridgeEventLogger, never()).logPegoutTransactionCreated(any(), any());

            assertTrue(activeFederationUtxos.isEmpty());
            assertTrue(retiringFederationUtxos.isEmpty());
            assertTrue(pegoutsWaitingForConfirmations.getEntries(activations).isEmpty());
        }

        private void assertInvalidPeginMarkedAsProcessed(ActivationConfig.ForBlock activations) throws IOException {
            // tx should be marked as processed if RSKIP459 is active and RSKIP551 is not active
            var shouldMarkTxAsProcessed = shouldMarkRejectedPeginAsProcessed(activations);
            if (shouldMarkTxAsProcessed) {
                verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
            } else {
                verify(bridgeStorageProvider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
            }
        }

        // Before peg-out tx index gets in use
        private void assertInvalidPeginIsIgnored() throws IOException {
            verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());
            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
            verify(bridgeStorageProvider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
            assertTrue(activeFederationUtxos.isEmpty());
            assertTrue(retiringFederationUtxos.isEmpty());
        }

        // After peg-out tx index gets in use
        private void assertInvalidPeginIsRejectedWithInvalidAmountReason(BtcTransaction btcTransaction, ActivationConfig.ForBlock activations) throws IOException {
            verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, INVALID_AMOUNT);
            verify(bridgeEventLogger, times(1)).logNonRefundablePegin(btcTransaction, NonRefundablePeginReason.INVALID_AMOUNT);
            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());

            assertInvalidPeginMarkedAsProcessed(activations);
            assertTrue(activeFederationUtxos.isEmpty());
            assertTrue(retiringFederationUtxos.isEmpty());
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
                ),

                // after RSKIP551 activation should not mark invalid peg-ins as processed
                Arguments.of(
                    allActivations,
                    true,
                    false
                ),
                Arguments.of(
                    allActivations,
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
                // after RSKIP551 activation should not mark invalid peg-ins as processed
                Arguments.of(
                    allActivations,
                    true
                )
            );
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
            assertUtxoWasRegistered(btcTransaction);
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue.multiply(10)), eq(0));
            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue), eq(0));
            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
                assertInvalidPeginIsRejectedWithInvalidAmountReason(btcTransaction, activations);
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
                assertInvalidPeginIsRejectedWithInvalidAmountReason(btcTransaction, activations);
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));

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
                assertInvalidPeginIsRejectedWithInvalidAmountReason(btcTransaction, activations);
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue.multiply(2)), eq(0));
            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));

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
                assertInvalidPeginIsRejectedWithInvalidAmountReason(btcTransaction, activations);
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue.multiply(2)), eq(0));
            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(1));
            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, PEGIN_V1_INVALID_PAYLOAD);
            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, PEGIN_V1_INVALID_PAYLOAD);

            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
            assertUtxoWasRegistered(btcTransaction);
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
            btcTransaction.addInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
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
            verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());

            verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(1));
            verify(bridgeStorageProvider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
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
                BTC_TX_HASH,
                FIRST_OUTPUT_INDEX,
                new Script(new byte[]{})
            );

            btcTransaction.addOutput(amountToSend, activeFederation.getAddress());
            btcTransaction.addOutput(Coin.ZERO, PegTestUtils.createOpReturnScriptForRskWithCustomPayload(1, new byte[]{}));

            addSignaturesAndFederationRedeemScript(unknownFed, signers, btcTransaction);

            PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

            // act
            bridgeSupport = buildBridgeSupport(activations);
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
                assertInvalidPeginV1UndeterminedSenderIsRejected(btcTransaction, activations);
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
                BTC_TX_HASH,
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
            );
            btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());

            addSignaturesAndFederationRedeemScript(unknownFed, signers, btcTransaction);

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
                BTC_TX_HASH,
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
            );
            btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());

            addSignaturesAndFederationRedeemScript(unknownFed, signers, btcTransaction);

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
                assertLegacyUndeterminedSenderPeginIsRejected(btcTransaction, activations);
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
                BTC_TX_HASH,
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
            );
            fundingTx.addOutput(PegTestUtils.createBech32Output(btcMainnetParams, Coin.COIN));
            addSignaturesAndFederationRedeemScript(unknownFed, unknownFedSigners, fundingTx);

            Coin amountToSend = Coin.COIN;
            BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
            btcTransaction.addInput(fundingTx.getOutput(FIRST_OUTPUT_INDEX));
            btcTransaction.addOutput(amountToSend, activeFederation.getAddress());

            addSignaturesAndFederationRedeemScript(unknownFed, unknownFedSigners, btcTransaction);

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
            if (activations == fingerrootActivations) {
                // BEFORE RSKIP379 REJECTED PEGIN WERE MARKED AS PROCESSED.
                assertLegacyUndeterminedSenderPeginIsRejectedAsPeginV1InvalidPayloadBeforeRSKIP379(btcTransaction);
            } else {
                assertLegacyUndeterminedSenderPeginIsRejected(btcTransaction, activations);
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
                BTC_TX_HASH,
                FIRST_OUTPUT_INDEX,
                ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
            );

            btcTransaction.addOutput(amountToSend, activeFederation.getAddress());

            btcTransaction.addOutput(Coin.ZERO, PegTestUtils.createOpReturnScriptForRskWithCustomPayload(1, new byte[]{}));

            addSignaturesAndFederationRedeemScript(unknownFed, signers, btcTransaction);

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

        void setUp(ForBlock activations) {
            Repository repository = createRepository();

            networkParameters = bridgeConstants.getBtcParams();
            FederationConstants federationConstants = bridgeConstants.getFederationConstants();

            bridgeStorageProvider = new BridgeStorageProvider(repository, networkParameters, activations);
            logs = new ArrayList<>();
            bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeConstants,
                activations,
                logs
            );

            StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
            federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
            federationStorageProvider.setOldFederation(retiringFederation);
            federationStorageProvider.setNewFederation(activeFederation);

            // Move the required blocks ahead for the new powpeg to become active
            rskExecutionBlockNumber = activeFederation.getCreationBlockNumber()
                + federationMainnetConstants.getFederationActivationAge(activations);
            Block currentBlock = createRskBlock(rskExecutionBlockNumber);

            FederationSupportBuilder federationSupportBuilder = FederationSupportBuilder.builder();
            federationSupport = federationSupportBuilder
                .withFederationConstants(federationConstants)
                .withFederationStorageProvider(federationStorageProvider)
                .withRskExecutionBlock(currentBlock)
                .withActivations(activations)
                .build();

            BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(networkParameters, 100, 100);
            btcBlockStore = btcBlockStoreFactory.newInstance(repository, bridgeConstants, bridgeStorageProvider, activations);
            btcLockSenderProvider = new BtcLockSenderProvider();
            peginInstructionsProvider = new PeginInstructionsProvider();

            BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
            bridgeSupport = bridgeSupportBuilder
                .withActivations(activations)
                .withExecutionBlock(currentBlock)
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
            assertEquals(1, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(allActivations).size());
        }

        private void assertPeginWasNotProcessed(Sha256Hash peginTxHash) throws IOException {
            assertTransactionWasNotProcessed(peginTxHash);
            assertRefundWasNotCreated();
            assertUtxosSize(0);
        }

        private void assertRefundWasNotCreated() throws IOException {
            assertEquals(0, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(allActivations).size());
        }

        private void assertTransactionWasNotProcessed(Sha256Hash transactionHash) throws IOException {
            Optional<Long> rskBlockHeightAtWhichBtcTxWasProcessed = bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(transactionHash);
            assertFalse(rskBlockHeightAtWhichBtcTxWasProcessed.isPresent());
        }

        private void assertUtxosSize(int expectedSize) {
            assertEquals(expectedSize, federationSupport.getActiveFederationBtcUTXOs().size());
        }

        @Nested
        @TestInstance(TestInstance.Lifecycle.PER_CLASS)
        @Tag("Pegin refund tests")
        class PeginRefundTestsWhenRetiringAndActiveFeds {
            private final int activeFedCreationBlockNumber = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates()
                + bridgeMainnetConstants.getPegoutTxIndexGracePeriodInBtcBlocks(); // we want pegout tx index to be activated

            private final List<BtcECKey> multiSigKeys = Arrays.asList(
                BitcoinTestUtils.getBtcEcKeyFromSeed("key1"),
                BitcoinTestUtils.getBtcEcKeyFromSeed("key2"),
                BitcoinTestUtils.getBtcEcKeyFromSeed("key3")
            );
            private final Script multiSigRedeemScript = ScriptBuilder.createRedeemScript(2, multiSigKeys);
            private final Coin prevTxValue = Coin.COIN;
            private final Coin valueToSend = prevTxValue.div(4);
            private final Address anotherOutputAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "address");

            private BtcTransaction prevTx;

            @BeforeEach
            void beforeEach() {
                bridgeConstants = BridgeMainNetConstants.getInstance();
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHMultisigSender_sentToP2shErpRetiringFed_shouldRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shMultiSig();
                pegin.addOutput(valueToSend, retiringFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have one input, related to the retiring fed
                assertPeginWasRejectedAndRefunded(1, pegin);
                // since retiring fed is legacy, redeem data should be in the script sig
                assertRefundInputIsFromLegacyFederation(retiringFederation, 0);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHMultisigSender_twoOutputsSentToP2shErpRetiringFed_shouldRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shMultiSig();
                pegin.addOutput(valueToSend, retiringFederation.getAddress());
                pegin.addOutput(valueToSend, retiringFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have two inputs, both related to the retiring fed
                int expectedAmoutOfRefundTxInputs = 2;
                assertPeginWasRejectedAndRefunded(expectedAmoutOfRefundTxInputs, pegin);
                // retiring fed is legacy
                assertRefundInputIsFromLegacyFederation(retiringFederation, 0);
                assertRefundInputIsFromLegacyFederation(retiringFederation, 1);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHP2WSHMultisigSender_sentToP2shErpRetiringFed_shouldRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shP2wshMultiSig();
                pegin.addOutput(valueToSend, retiringFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have one input, related to the retiring fed
                assertPeginWasRejectedAndRefunded(1, pegin);
                // since retiring fed is legacy, redeem data should be in the script sig
                assertRefundInputIsFromLegacyFederation(retiringFederation, 0);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHP2WSHMultisigSender_twoOutputsSentToP2shErpRetiringFed_shouldRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shP2wshMultiSig();
                pegin.addOutput(valueToSend, retiringFederation.getAddress());
                pegin.addOutput(valueToSend, retiringFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have two inputs, both related to the retiring fed
                int expectedAmoutOfRefundTxInputs = 2;
                assertPeginWasRejectedAndRefunded(expectedAmoutOfRefundTxInputs, pegin);
                // retiring fed is legacy
                assertRefundInputIsFromLegacyFederation(retiringFederation, 0);
                assertRefundInputIsFromLegacyFederation(retiringFederation, 1);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHMultisigSender_sentToP2shP2wshErpActiveFed_shouldRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shMultiSig();
                pegin.addOutput(valueToSend, activeFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have one input, related to the active fed
                assertPeginWasRejectedAndRefunded(1, pegin);
                // since active fed is segwit, redeem data should be in the witness
                assertRefundInputIsFromSegwitFederation(activeFederation, 0);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHMultisigSender_twoOutputsSentToP2shP2wshErpActiveFed_shouldRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shMultiSig();
                pegin.addOutput(valueToSend, activeFederation.getAddress());
                pegin.addOutput(valueToSend, activeFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have two inputs, both related to the active fed
                int expectedAmoutOfRefundTxInputs = 2;
                assertPeginWasRejectedAndRefunded(expectedAmoutOfRefundTxInputs, pegin);
                // active fed is segwit
                assertRefundInputIsFromSegwitFederation(activeFederation, 0);
                assertRefundInputIsFromSegwitFederation(activeFederation, 1);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHP2WSHMultisigSender_sentToP2shP2wshErpActiveFed_shouldRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shP2wshMultiSig();
                pegin.addOutput(valueToSend, activeFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have one input, related to the active fed
                assertPeginWasRejectedAndRefunded(1, pegin);
                // since active fed is segwit, redeem data should be in the witness
                assertRefundInputIsFromSegwitFederation(activeFederation, 0);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHP2WSHMultisigSender_twoOutputsSentToP2shP2wshErpActiveFed_shouldRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shP2wshMultiSig();
                pegin.addOutput(valueToSend, activeFederation.getAddress());
                pegin.addOutput(valueToSend, activeFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have two inputs, both related to the active fed
                int expectedAmoutOfRefundTxInputs = 2;
                assertPeginWasRejectedAndRefunded(expectedAmoutOfRefundTxInputs, pegin);
                // active fed is segwit
                assertRefundInputIsFromSegwitFederation(activeFederation, 0);
                assertRefundInputIsFromSegwitFederation(activeFederation, 1);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHMultisigSender_sentToBothP2shErpRetiringFedAndP2shErpActiveFed_shouldRefund() throws Exception {
                // arrange
                List<BtcECKey> retiringFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{
                    "member01",
                    "member02",
                    "member03",
                    "member04",
                    "member05",
                    "member06",
                    "member07",
                    "member08"
                }, true);
                retiringFederation = P2shErpFederationBuilder.builder()
                    .withMembersBtcPublicKeys(retiringFedKeys)
                    .build();
                activeFederation = P2shErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(lovell700Activations);

                BtcTransaction pegin = buildPeginFromP2shMultiSig();
                pegin.addOutput(valueToSend, retiringFederation.getAddress());
                pegin.addOutput(valueToSend, retiringFederation.getAddress());
                pegin.addOutput(valueToSend, activeFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have three inputs, two related to the retiring fed and one to the active fed
                int expectedAmoutOfRefundTxInputs = 3;
                assertPeginWasRejectedAndRefunded(expectedAmoutOfRefundTxInputs, pegin);
                // both feds are legacy
                // first and second inputs should belong to retiring fed
                assertRefundInputIsFromLegacyFederation(retiringFederation, 0);
                assertRefundInputIsFromLegacyFederation(retiringFederation, 1);
                // third input should belong to active fed
                assertRefundInputIsFromLegacyFederation(activeFederation, 2);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHP2WSHMultisigSender_sentToBothP2shErpRetiringFedAndP2shErpActiveFed_shouldRefund() throws Exception {
                // arrange
                List<BtcECKey> retiringFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{
                    "member01",
                    "member02",
                    "member03",
                    "member04",
                    "member05",
                    "member06",
                    "member07",
                    "member08"
                }, true);
                retiringFederation = P2shErpFederationBuilder.builder()
                    .withMembersBtcPublicKeys(retiringFedKeys)
                    .build();
                activeFederation = P2shErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(lovell700Activations);

                BtcTransaction pegin = buildPeginFromP2shP2wshMultiSig();
                pegin.addOutput(valueToSend, retiringFederation.getAddress());
                pegin.addOutput(valueToSend, activeFederation.getAddress());
                pegin.addOutput(valueToSend, activeFederation.getAddress());

                registerPegin(pegin);

                // assert
                // refund tx should have three inputs, one related to the retiring fed and two to the active fed
                int expectedAmoutOfRefundTxInputs = 3;
                assertPeginWasRejectedAndRefunded(expectedAmoutOfRefundTxInputs, pegin);
                // both feds are legacy
                // first input should belong to retiring fed
                assertRefundInputIsFromLegacyFederation(retiringFederation, 0);
                // second and third inputs should belong to active fed
                assertRefundInputIsFromLegacyFederation(activeFederation, 1);
                assertRefundInputIsFromLegacyFederation(activeFederation, 2);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHMultisigSender_sentToBothP2shErpRetiringFedAndP2shP2wshErpActiveFed_shouldNotRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shMultiSig();
                pegin.addOutput(valueToSend, retiringFederation.getAddress());
                pegin.addOutput(valueToSend, activeFederation.getAddress());
                pegin.addOutput(valueToSend, activeFederation.getAddress());

                // act
                registerPegin(pegin);

                // assert
                assertRejectedPeginWasNotRefunded(pegin);
            }

            @Test
            void registerBtcTransaction_legacyPeginP2SHP2WSHMultisigSender_sentToBothP2shErpRetiringFedAndP2shP2wshErpActiveFed_shouldNotRefund() throws Exception {
                // arrange
                retiringFederation = P2shErpFederationBuilder.builder().build();
                activeFederation = P2shP2wshErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .build();
                setUp(allActivations);

                BtcTransaction pegin = buildPeginFromP2shP2wshMultiSig();
                pegin.addOutput(valueToSend, retiringFederation.getAddress());
                pegin.addOutput(valueToSend, retiringFederation.getAddress());
                pegin.addOutput(valueToSend, activeFederation.getAddress());

                // act
                registerPegin(pegin);

                // assert
                assertRejectedPeginWasNotRefunded(pegin);
            }

            private void assertPeginWasRejectedAndRefunded(int expectedAmountOfRefundInputs, BtcTransaction rejectedPegin) throws IOException {
                BtcTransaction pegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);
                assertEquals(expectedAmountOfRefundInputs, pegout.getInputs().size());

                assertLogRejectedPegin(logs, rejectedPegin, LEGACY_PEGIN_MULTISIG_SENDER);
            }

            private void assertRefundInputIsFromLegacyFederation(Federation federation, int inputToFederationIndex) throws IOException {
                BtcTransaction pegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);
                var inputToFederation = pegout.getInput(inputToFederationIndex);
                assertScriptSigHasExpectedInputRedeemData(inputToFederation, federation.getRedeemScript());
            }

            private void assertRefundInputIsFromSegwitFederation(Federation federation, int inputToFederationIndex) throws IOException {
                BtcTransaction pegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);
                var inputToFederation = pegout.getInput(inputToFederationIndex);
                var inputWitness = pegout.getWitness(inputToFederationIndex);

                assertWitnessAndScriptSigHaveExpectedInputRedeemData(
                    inputWitness,
                    inputToFederation,
                    federation.getRedeemScript()
                );
            }

            private void assertRejectedPeginWasNotRefunded(BtcTransaction rejectedPegin) throws IOException {
                assertEquals(0, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(allActivations).size());

                assertLogRejectedPegin(logs, rejectedPegin, LEGACY_PEGIN_MULTISIG_SENDER);
                assertLogNonRefundablePegin(logs, rejectedPegin, OUTPUTS_SENT_TO_DIFFERENT_TYPES_OF_FEDS);
            }

            private BtcTransaction buildPeginFromP2shMultiSig() {
                Script multiSigOutputScript = ScriptBuilder.createP2SHOutputScript(multiSigRedeemScript);
                prevTx = new BtcTransaction(btcMainnetParams);
                prevTx.addOutput(prevTxValue, multiSigOutputScript);

                BtcTransaction peginFromP2shMultiSig = new BtcTransaction(btcMainnetParams);
                peginFromP2shMultiSig.addInput(prevTx.getOutput(0));

                Script inputScript = createBaseInputScriptThatSpendsFromRedeemScript(multiSigRedeemScript);
                peginFromP2shMultiSig.getInput(0).setScriptSig(inputScript);

                peginFromP2shMultiSig.addOutput(prevTxValue.div(6), anotherOutputAddress); // to have one output sent to a different address

                return peginFromP2shMultiSig;
            }

            private BtcTransaction buildPeginFromP2shP2wshMultiSig() {
                Script multiSigOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(multiSigRedeemScript);
                prevTx = new BtcTransaction(btcMainnetParams);
                prevTx.addOutput(prevTxValue, multiSigOutputScript);

                BtcTransaction peginFromP2shP2wshMultiSig = new BtcTransaction(btcMainnetParams);
                peginFromP2shP2wshMultiSig.addInput(prevTx.getOutput(0));

                Script inputScript = createBaseInputScriptThatSpendsFromRedeemScript(multiSigRedeemScript);
                peginFromP2shP2wshMultiSig.getInput(0).setScriptSig(inputScript);

                peginFromP2shP2wshMultiSig.addOutput(prevTxValue.div(6), anotherOutputAddress); // to have one output sent to a different address

                return peginFromP2shP2wshMultiSig;
            }
        }

        private BtcTransaction buildLegacyPegin(Federation federation, Script userScriptPubKey) {
            BtcTransaction pegin = new BtcTransaction(networkParameters);
            pegin.addInput(BTC_TX_HASH, 0, userScriptPubKey);

            pegin.addOutput(Coin.COIN, federation.getAddress());

            return pegin;
        }

        private BtcTransaction buildPeginV1(Federation federation, Script userScriptPubKey) {
            BtcTransaction pegin = new BtcTransaction(networkParameters);
            pegin.addInput(BTC_TX_HASH, 0, userScriptPubKey);

            pegin.addOutput(Coin.ZERO, opReturnScript);
            pegin.addOutput(Coin.COIN, federation.getAddress());

            return pegin;
        }

        @Nested
        @TestInstance(TestInstance.Lifecycle.PER_CLASS)
        @Tag("Pegin from users with non/parseable script pub keys")
        class PeginFromUserWithDifferentScriptPubKey {
            private final Script nonParseableScriptPubKey = ScriptBuilder.createInputScript(null, BitcoinTestUtils.getBtcEcKeyFromSeed("abc"));
            private final Script parseableScriptPubKey = ScriptBuilder.createInputScript(null, BtcECKey.fromPublicOnly(
                Hex.decode("0377a6c71c43d9fac4343f87538cd2880cf5ebefd3dd1d9aabdbbf454bca162de9")
            ));
            private final List<BtcECKey> realActiveFedKeys = List.of(
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

            @BeforeEach
            void beforeEach() {
                bridgeConstants = BridgeMainNetConstants.getInstance();
                networkParameters = bridgeConstants.getBtcParams();

                retiringFederation = P2shErpFederationBuilder.builder()
                    .build();

                int activeFedCreationBlockNumber = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates()
                    + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
                activeFederation = P2shErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .withMembersBtcPublicKeys(realActiveFedKeys)
                    .build();
            }

            @Test
            void registerBtcTx_legacyPeginWithParseableScriptPubKey_withoutSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
                // arrange
                setUp(allActivations);
                BtcTransaction pegin = buildLegacyPegin(activeFederation, parseableScriptPubKey);

                // act
                registerPegin(pegin);

                // assert
                assertPeginWasRegisteredSuccessfully(pegin.getHash());
            }

            @Test
            void registerBtcTx_legacyPeginWithParseableScriptPubKey_withSVPOnGoing_shouldRegisterPegIn() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
                // arrange
                setUp(allActivations);
                BtcTransaction pegin = buildLegacyPegin(activeFederation, parseableScriptPubKey);

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
                setUp(lovell700Activations);
                BtcTransaction pegin = buildLegacyPegin(activeFederation, parseableScriptPubKey);

                // save svp spend tx hash, so it enters the flow that throws exception
                bridgeStorageProvider.setSvpSpendTxHashUnsigned(Sha256Hash.ZERO_HASH);

                // act & assert
                assertThrows(IllegalStateException.class, () -> registerPegin(pegin));
                assertPeginWasNotProcessed(pegin.getHash());
            }

            @Test
            void registerBtcTx_legacyPeginWithNonParseableScriptPubKey_withoutSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
                // arrange
                setUp(allActivations);
                BtcTransaction pegin = buildLegacyPegin(activeFederation, nonParseableScriptPubKey);

                // act
                registerPegin(pegin);

                // assert
                assertPeginWasRegisteredSuccessfully(pegin.getHash());
            }

            @Test
            void registerBtcTx_legacyPeginWithNonParseableScriptPubKey_withSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
                // arrange
                setUp(allActivations);
                BtcTransaction pegin = buildLegacyPegin(activeFederation, nonParseableScriptPubKey);
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
                setUp(allActivations);
                BtcTransaction pegin = buildPeginV1(activeFederation, nonParseableScriptPubKey);

                // act
                registerPegin(pegin);

                // assert
                assertPeginWasRegisteredSuccessfully(pegin.getHash());
            }

            @Test
            void registerBtcTx_peginV1WithNonParseableScriptPubKey_withSVPOnGoing_shouldRegisterPegin() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
                // arrange
                setUp(allActivations);
                BtcTransaction pegin = buildPeginV1(activeFederation, nonParseableScriptPubKey);
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
                setUp(allActivations);
                BtcTransaction pegin = buildPeginV1(activeFederation, parseableScriptPubKey);
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
                setUp(lovell700Activations);
                BtcTransaction pegin = buildPeginV1(activeFederation, parseableScriptPubKey);
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
                BtcTransaction pegin = buildPeginV1(activeFederation, parseableScriptPubKey);

                // act
                registerPegin(pegin);

                // assert
                assertPeginWasRegisteredSuccessfully(pegin.getHash());
            }

            // data from testnet real pegin v1 that had a parseable script pub key
            // and was malformed (with an incorrect op return)
            // https://mempool.space/testnet/tx/77a135b5f233671686e655e462efa5d87013d94b105b8fcacc219e78503866a6
            @Test
            void registerBtcTx_testnetRealPeginV1_withSVPOnGoing_beforeRSKIP305_shouldThrowISE() throws IOException {
                // arrange
                bridgeConstants = BridgeTestNetConstants.getInstance();
                networkParameters = bridgeConstants.getBtcParams();
                FederationConstants federationConstants = bridgeConstants.getFederationConstants();

                List<BtcECKey> erpFedPubKeys = federationConstants.getErpFedPubKeysList();
                retiringFederation = P2shErpFederationBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withErpPublicKeys(erpFedPubKeys)
                    .build();

                int activeFedCreationBlockNumber = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates()
                    + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
                activeFederation = P2shErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .withMembersBtcPublicKeys(realActiveFedKeys)
                    .withNetworkParameters(networkParameters)
                    .withErpPublicKeys(erpFedPubKeys)
                    .build();

                setUp(lovell700Activations);
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
                networkParameters = bridgeConstants.getBtcParams();
                FederationConstants federationConstants = bridgeConstants.getFederationConstants();

                List<BtcECKey> erpFedPubKeys = federationConstants.getErpFedPubKeysList();
                retiringFederation = P2shErpFederationBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withErpPublicKeys(erpFedPubKeys)
                    .build();

                int activeFedCreationBlockNumber = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates()
                    + bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
                activeFederation = P2shErpFederationBuilder.builder()
                    .withCreationBlockNumber(activeFedCreationBlockNumber)
                    .withMembersBtcPublicKeys(realActiveFedKeys)
                    .withNetworkParameters(networkParameters)
                    .withErpPublicKeys(erpFedPubKeys)
                    .build();

                setUp(allActivations);

                // act
                registerPegin(testnetRealPegin);

                // assert
                assertTransactionWasProcessed(testnetRealPegin.getHash());
                assertRefundWasCreated();
                assertUtxosSize(0);
            }
        }
    }

    @Nested
    class ReleaseTransaction {

        private final Coin changeValue = minimumPeginTxValue.add(Coin.SATOSHI);
        private final List<BtcECKey> erpPubKeys = federationMainnetConstants.getErpFedPubKeysList();
        private final long activationDelay = federationMainnetConstants.getErpFedActivationDelay();
        private final NetworkParameters networkParameters = federationMainnetConstants.getBtcParams();
        private final FederationConstants federationConstants = bridgeMainnetConstants.getFederationConstants();
        private final List<BtcECKey> activeFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09"}, true);
        private final List<BtcECKey> retiringFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"newMember01", "newMember02", "newMember03", "newMember04", "newMember05", "member06", "member07", "member08", "member09"}, true);
        Federation activeFederation;
        Federation retiringFederation;
        Repository repository;
        Block currentBlock;

        @Nested
        class PegoutTransaction {

            @Test
            void registerBtcTransaction_withNoChangeOutput_forFingerroot_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction btcTransaction = getReleaseTxWithOneInputAndOutputWithoutChange(activeFederation, activeFederationKeys, userAddress);

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withNoChangeOutput_forFingerroot_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(fingerrootActivations);
                BtcTransaction btcTransaction = getReleaseTxWithOneInputAndOutputWithoutChange(activeFederation, activeFederationKeys, userAddress);

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withNoChangeOutput_withoutPegoutIndex_forArrowhead_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction btcTransaction = getReleaseTxWithOneInputAndOutputWithoutChange(activeFederation, activeFederationKeys, userAddress);

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withNoChangeOutput_withoutPegoutIndex_forArrowhead_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(arrowhead600Activations);
                BtcTransaction btcTransaction = getReleaseTxWithOneInputAndOutputWithoutChange(activeFederation, activeFederationKeys, userAddress);

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withNoChangeOutput_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction btcTransaction = getReleaseTxWithOneInputAndOutputWithoutChange(activeFederation, activeFederationKeys, userAddress);
                registerPegoutTxSigHash(btcTransaction);

                // act
                registerReleaseTransaction(btcTransaction, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withNoChangeOutput_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveFed();
                BtcTransaction btcTransaction = getReleaseTxWithOneInputAndOutputWithoutChange(activeFederation, activeFederationKeys, userAddress);
                registerPegoutTxSigHash(btcTransaction);

                // act
                registerReleaseTransaction(btcTransaction, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_notInPegoutTxIndexWithChangeOutputFromFederation_shouldBeDetectedAsLegacyPeginRejectedAndRefunded() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(allActivations, heightAtWhichToStartUsingPegoutIndex);
                BtcTransaction btcTransaction = getPegoutTxWithOneInputAndOutputWithChange();

                // act
                registerReleaseTransaction(btcTransaction, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertLogRejectedPegin(logs, btcTransaction, LEGACY_PEGIN_MULTISIG_SENDER);

                BtcTransaction refundPegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);
                assertReleaseRejectionWasSettled(
                    repository,
                    bridgeStorageProvider,
                    logs,
                    currentBlock.getNumber(),
                    rskTx.getHash(),
                    refundPegout,
                    List.of(changeValue),
                    changeValue
                );

                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndInputsAndChangeOutput_forFingerroot_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndInputsWithChangeOutput();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndInputsAndChangeOutput_forFingerroot_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(fingerrootActivations);
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndInputsWithChangeOutput();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndInputsAndChangeOutput_withoutPegoutIndex_forArrowhead_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndInputsWithChangeOutput();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndInputsAndChangeOutput_withoutPegoutIndex_forArrowhead_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(arrowhead600Activations);
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndInputsWithChangeOutput();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndInputsAndChangeOutput_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndInputsWithChangeOutput();
                registerPegoutTxSigHash(btcTransaction);

                // act
                registerReleaseTransaction(btcTransaction, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndInputsAndChangeOutput_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveFed();
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndInputsWithChangeOutput();
                registerPegoutTxSigHash(btcTransaction);

                // act
                registerReleaseTransaction(btcTransaction, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndOneInputAndChangeOutput_forFingerroot_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndOneInputWithChangeOutput();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndOneInputAndChangeOutput_forFingerroot_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(fingerrootActivations);
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndOneInputWithChangeOutput();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndOneInputAndChangeOutput_withoutPegoutIndex_forArrowhead_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndOneInputWithChangeOutput();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndOneInputAndChangeOutput_withoutPegoutIndex_forArrowhead_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(arrowhead600Activations);
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndOneInputWithChangeOutput();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndOneInputAndChangeOutput_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndOneInputWithChangeOutput();
                registerPegoutTxSigHash(btcTransaction);

                // act
                registerReleaseTransaction(btcTransaction, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndOneInputAndChangeOutput_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveFed();
                BtcTransaction btcTransaction = getPegoutTxWithManyOutputsAndOneInputWithChangeOutput();
                registerPegoutTxSigHash(btcTransaction);

                // act
                registerReleaseTransaction(btcTransaction, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertPegoutWithChangeWasProcessed(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withOneOutputAndManyInputs_forFingerroot_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction btcTransaction = getPegoutTxWithOneOutputAndManyInputsWithoutChange();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withOneOutputAndManyInputs_forFingerroot_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(fingerrootActivations);
                BtcTransaction btcTransaction = getPegoutTxWithOneOutputAndManyInputsWithoutChange();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withOneOutputAndManyInputs_withoutPegoutIndex_forArrowhead_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction btcTransaction = getPegoutTxWithOneOutputAndManyInputsWithoutChange();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withOneOutputAndManyInputs_withoutPegoutIndex_forArrowhead_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(arrowhead600Activations);
                BtcTransaction btcTransaction = getPegoutTxWithOneOutputAndManyInputsWithoutChange();

                // act
                registerReleaseTransaction(btcTransaction, heightBeforeUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withOneOutputAndManyInputs_withRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction btcTransaction = getPegoutTxWithOneOutputAndManyInputsWithoutChange();

                // act
                registerPegoutTxSigHash(btcTransaction);
                registerReleaseTransaction(btcTransaction, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            @Test
            void registerBtcTransaction_withOneOutputAndManyInputs_withoutRetiringFed_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveFed();
                BtcTransaction btcTransaction = getPegoutTxWithOneOutputAndManyInputsWithoutChange();

                // act
                registerPegoutTxSigHash(btcTransaction);
                registerReleaseTransaction(btcTransaction, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertReleaseTxWasProcessedWithNoNewUtxo(btcTransaction);
            }

            private void assertPegoutWithChangeWasProcessed(BtcTransaction pegout) throws IOException {
                assertReleaseTxWasProcessed(pegout);
                assertUtxosAddedInActiveFed(1);
                assertNoUtxoWasAddedInRetiringFed();
            }

            private BtcTransaction getPegoutTxWithOneInputAndOutputWithChange() {
                return PegoutTransactionBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withActiveFederation(activeFederation)
                    .withInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, Coin.COIN)
                    .withOutput(Coin.COIN, userAddress)
                    .withChangeAmount(changeValue)
                    .withSignatures(activeFederationKeys)
                    .build();
            }

            private BtcTransaction getPegoutTxWithManyOutputsAndInputsWithChangeOutput() {
                PegoutTransactionBuilder btcTransactionBuilder = PegoutTransactionBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withActiveFederation(activeFederation)
                    .withSignatures(activeFederationKeys);
                addManyInputs(btcTransactionBuilder);
                addManyOutputsWithChangeOutput(btcTransactionBuilder);
                return btcTransactionBuilder.build();
            }

            private BtcTransaction getPegoutTxWithManyOutputsAndOneInputWithChangeOutput() {
                PegoutTransactionBuilder btcTransactionBuilder = PegoutTransactionBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withActiveFederation(activeFederation)
                    .withInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, Coin.COIN)
                    .withSignatures(activeFederationKeys);
                addManyOutputsWithChangeOutput(btcTransactionBuilder);
                return btcTransactionBuilder.build();
            }

            private void addManyOutputsWithChangeOutput(PegoutTransactionBuilder btcTransactionBuilder) {
                Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
                Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
                for (int i = 0; i < 10; i++) {
                    btcTransactionBuilder.withOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i));
                }

                for (int i = 0; i < 10; i++) {
                    btcTransactionBuilder.withOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 10));
                }

                for (int i = 0; i < 10; i++) {
                    btcTransactionBuilder.withOutput(minimumPegoutTxValue.add(Coin.CENT), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 20));
                }

                for (int i = 0; i < 10; i++) {
                    btcTransactionBuilder.withOutput(minimumPegoutTxValue.add(Coin.COIN), BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "user" + i + 30));
                }

                btcTransactionBuilder.withChangeAmount(changeValue);
            }

            private BtcTransaction getPegoutTxWithOneOutputAndManyInputsWithoutChange() {
                Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
                PegoutTransactionBuilder btcTransactionBuilder = PegoutTransactionBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withActiveFederation(activeFederation)
                    .withOutput(minimumPegoutTxValue, userAddress)
                    .withoutChange()
                    .withSignatures(activeFederationKeys);
                addManyInputs(btcTransactionBuilder);
                return btcTransactionBuilder.build();
            }
        }

        @Nested
        class MigrationTransaction {

            @Test
            void registerBtcTransaction_withOneInputAndOutput_forFingerroot_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiringFederation, retiringFederationKeys, activeFederation.getAddress());

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withOneInputAndOutput_withoutPegoutIndex_forArrowhead_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiringFederation, retiringFederationKeys, activeFederation.getAddress());

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withOneInputAndOutput_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiringFederation, retiringFederationKeys, activeFederation.getAddress());

                // act
                registerPegoutTxSigHash(migrationTx);
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_notInPegoutTxIndex_forArrowhead_shouldBeDetectedAsLegacyPeginRejectedAndRefunded() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightAtWhichToStartUsingPegoutIndex);
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiringFederation, retiringFederationKeys, activeFederation.getAddress());

                // act
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertLogRejectedPegin(logs, migrationTx, LEGACY_PEGIN_MULTISIG_SENDER);
                BtcTransaction refundPegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);
                assertLegacyReleaseRejectionWasSettled(
                    bridgeStorageProvider,
                    logs,
                    currentBlock.getNumber(),
                    rskTx.getHash(),
                    refundPegout,
                    Coin.COIN,
                    arrowhead600Activations
                );
                assertReleaseTxWasProcessedWithNoNewUtxo(migrationTx);
            }

            @Test
            void registerBtcTransaction_notInPegoutTxIndex_shouldBeDetectedAsLegacyPeginRejectedAndRefunded() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiringFederation, retiringFederationKeys, activeFederation.getAddress());

                // act
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertLogRejectedPegin(logs, migrationTx, LEGACY_PEGIN_MULTISIG_SENDER);
                BtcTransaction refundPegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);
                assertReleaseRejectionWasSettled(
                    repository,
                    bridgeStorageProvider,
                    logs,
                    currentBlock.getNumber(),
                    rskTx.getHash(),
                    refundPegout,
                    List.of(Coin.COIN),
                    Coin.COIN
                );
                assertReleaseTxWasProcessedWithNoNewUtxo(migrationTx);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndInputs_forFingerroot_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithManyOutputsAndInputs();

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndInputs_withoutPegoutIndex_forArrowhead_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithManyOutputsAndInputs();

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndInputs_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction migrationTx = getMigrationTxWithManyOutputsAndInputs();

                // act
                registerPegoutTxSigHash(migrationTx);
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndOneInput_forFingerroot_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithManyOutputsAndOneInput();

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndOneInput_withoutPegoutIndex_forArrowhead_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithManyOutputsAndOneInput();

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withManyOutputsAndOneInput_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction migrationTx = getMigrationTxWithManyOutputsAndOneInput();

                // act
                registerPegoutTxSigHash(migrationTx);
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withOneOutputAndManyInputs_forFingerroot_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithOneOutputAndManyInputs();

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withOneOutputAndManyInputs_withoutPegoutIndex_forArrowhead_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithOneOutputAndManyInputs();

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withOneOutputAndManyInputs_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction migrationTx = getMigrationTxWithOneOutputAndManyInputs();

                // act
                registerPegoutTxSigHash(migrationTx);
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withFlyoverUtxoWithOneInputAndOutput_forFingerroot_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithFlyoverUtxo(fingerrootActivations);

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withFlyoverUtxoWithOneInputAndOutput_withoutPegoutIndex_forArrowhead_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithFlyoverUtxo(arrowhead600Activations);

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withFlyoverUtxoWithOneInputAndOutput_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction migrationTx = getMigrationTxWithFlyoverUtxo(allActivations);

                // act
                registerPegoutTxSigHash(migrationTx);
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withFlyoverUtxoWithManyOutputsAndInputs_forFingerroot_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(fingerrootActivations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithManyInputsAndOutputsWithFlyoverUtxo(fingerrootActivations);

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withFlyoverUtxoWithManyOutputsAndInputs_withoutPegoutIndex_forArrowhead_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringLegacyFed(arrowhead600Activations, heightBeforeUsingPegoutIndex);
                BtcTransaction migrationTx = getMigrationTxWithManyInputsAndOutputsWithFlyoverUtxo(arrowhead600Activations);

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_withFlyoverUtxoWithManyOutputsAndInputs_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveAndRetiringFed();
                BtcTransaction migrationTx = getMigrationTxWithManyInputsAndOutputsWithFlyoverUtxo(allActivations);

                // act
                registerPegoutTxSigHash(migrationTx);
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_fromLastRetiredFederation_forFingerroot_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(fingerrootActivations);
                federationStorageProvider.setLastRetiredFederationP2SHScript(retiredFed.getP2SHScript());
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiredFed, retiredFedSigners, activeFederation.getAddress());

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_fromLastRetiredFederation_withoutPegoutIndex_forArrowhead_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(arrowhead600Activations);
                federationStorageProvider.setLastRetiredFederationP2SHScript(retiredFed.getP2SHScript());
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiredFed, retiredFedSigners, activeFederation.getAddress());

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_fromLastRetiredFederation_shouldRegisterPegoutTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveFed();
                federationStorageProvider.setLastRetiredFederationP2SHScript(retiredFed.getP2SHScript());
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiredFed, retiredFedSigners, activeFederation.getAddress());

                // act
                registerPegoutTxSigHash(migrationTx);
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            @Test
            void registerBtcTransaction_fromRetiredFederationNotInStorage_forFingerroot_shouldRejectAndRefund() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(fingerrootActivations);
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiredFed, retiredFedSigners, activeFederation.getAddress());

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertLogRejectedPegin(logs, migrationTx, LEGACY_PEGIN_MULTISIG_SENDER);
                BtcTransaction refundPegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);
                assertLegacyReleaseRejectionWasSettled(
                    bridgeStorageProvider,
                    logs,
                    currentBlock.getNumber(),
                    rskTx.getHash(),
                    refundPegout,
                    Coin.COIN,
                    fingerrootActivations
                );
                assertReleaseTxWasProcessedWithNoNewUtxo(migrationTx);
            }

            @Test
            void registerBtcTransaction_fromRetiredFederationNotInStorage_withoutPegoutIndex_forArrowhead_shouldRejectAndRefund() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveLegacyFed(arrowhead600Activations);
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiredFed, retiredFedSigners, activeFederation.getAddress());

                // act
                registerReleaseTransaction(migrationTx, heightBeforeUsingPegoutIndex);

                // assert
                assertLogRejectedPegin(logs, migrationTx, LEGACY_PEGIN_MULTISIG_SENDER);
                BtcTransaction refundPegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);
                assertLegacyReleaseRejectionWasSettled(
                    bridgeStorageProvider,
                    logs,
                    currentBlock.getNumber(),
                    rskTx.getHash(),
                    refundPegout,
                    Coin.COIN,
                    arrowhead600Activations
                );
                assertReleaseTxWasProcessedWithNoNewUtxo(migrationTx);
            }

            @Test
            void registerBtcTransaction_fromRetiredFederationNotInStorage_shouldRegisterMigrationTx() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
                // arrange
                setupActiveFed();
                BtcTransaction migrationTx = getReleaseTxWithOneInputAndOutputWithoutChange(retiredFed, retiredFedSigners, activeFederation.getAddress());

                // act
                registerPegoutTxSigHash(migrationTx);
                registerReleaseTransaction(migrationTx, heightAtWhichToStartUsingPegoutIndex);

                // assert
                assertMigrationTxWasProcessed(migrationTx);
            }

            private void assertMigrationTxWasProcessed(BtcTransaction migrationTx) throws IOException {
                assertReleaseTxWasProcessed(migrationTx);
                assertUtxosAddedInActiveFed(migrationTx.getOutputs().size());
                assertNoUtxoWasAddedInRetiringFed();
            }

            private BtcTransaction getMigrationTxWithFlyoverUtxo(ActivationConfig.ForBlock activations) {
                Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
                    derivationArgumentsHash,
                    userRefundBtcAddress,
                    lpBtcAddress,
                    lbcAddress,
                    activations
                );
                Script retiringFederationFlyoverRedeemScript = PegUtils.getFlyoverFederationRedeemScript(
                    flyoverDerivationHash,
                    retiringFederation.getRedeemScript()
                );

                BtcTransaction migrationTx = PegoutTransactionBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withActiveFederation(retiringFederation)
                    .withInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, Coin.COIN)
                    .withOutput(Coin.COIN, activeFederation.getAddress())
                    .withoutChange()
                    .build();

                addSpendingFederationBaseScript(
                    migrationTx,
                    FIRST_INPUT_INDEX,
                    retiringFederationFlyoverRedeemScript,
                    retiringFederation.getFormatVersion()
                );
                addSignatures(retiringFederation, retiringFederationKeys, migrationTx);

                return migrationTx;
            }

            private BtcTransaction getMigrationTxWithManyOutputsAndInputs() {
                PegoutTransactionBuilder btcTransactionBuilder = PegoutTransactionBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withActiveFederation(retiringFederation)
                    .withoutChange()
                    .withSignatures(retiringFederationKeys);
                addManyInputs(btcTransactionBuilder);
                addManyMigrationOutputs(btcTransactionBuilder);
                return btcTransactionBuilder.build();
            }

            private BtcTransaction getMigrationTxWithManyInputsAndOutputsWithFlyoverUtxo(ActivationConfig.ForBlock activations) {
                Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
                    derivationArgumentsHash,
                    userRefundBtcAddress,
                    lpBtcAddress,
                    lbcAddress,
                    activations
                );
                Script retiringFederationFlyoverRedeemScript = PegUtils.getFlyoverFederationRedeemScript(
                    flyoverDerivationHash,
                    retiringFederation.getRedeemScript()
                );

                Sha256Hash flyoverInputTxHash = BitcoinTestUtils.createHash(123);
                PegoutTransactionBuilder btcTransactionBuilder = PegoutTransactionBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withActiveFederation(retiringFederation)
                    .withInput(flyoverInputTxHash, FIRST_OUTPUT_INDEX, Coin.COIN)
                    .withoutChange();
                addManyInputs(btcTransactionBuilder);
                addManyMigrationOutputs(btcTransactionBuilder);
                BtcTransaction migrationTx = btcTransactionBuilder.build();

                addSpendingFederationBaseScript(
                    migrationTx,
                    FIRST_INPUT_INDEX,
                    retiringFederationFlyoverRedeemScript,
                    retiringFederation.getFormatVersion()
                );
                addSignatures(retiringFederation, retiringFederationKeys, migrationTx);

                return migrationTx;
            }

            private BtcTransaction getMigrationTxWithManyOutputsAndOneInput() {
                PegoutTransactionBuilder btcTransactionBuilder = PegoutTransactionBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withActiveFederation(retiringFederation)
                    .withInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, Coin.COIN)
                    .withoutChange()
                    .withSignatures(retiringFederationKeys);
                addManyMigrationOutputs(btcTransactionBuilder);
                return btcTransactionBuilder.build();
            }

            private void addManyMigrationOutputs(PegoutTransactionBuilder btcTransactionBuilder) {
                Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
                Coin quarterMinimumPegoutTxValue = minimumPegoutTxValue.div(4);
                for (int i = 0; i < 10; i++) {
                    btcTransactionBuilder.withOutput(quarterMinimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
                }

                for (int i = 0; i < 10; i++) {
                    btcTransactionBuilder.withOutput(quarterMinimumPegoutTxValue.multiply(2).add(Coin.CENT), activeFederation.getAddress());
                }

                for (int i = 0; i < 10; i++) {
                    btcTransactionBuilder.withOutput(minimumPegoutTxValue.add(Coin.CENT), activeFederation.getAddress());
                }

                for (int i = 0; i < 10; i++) {
                    btcTransactionBuilder.withOutput(minimumPegoutTxValue.add(Coin.COIN), activeFederation.getAddress());
                }
            }


            private BtcTransaction getMigrationTxWithOneOutputAndManyInputs() {
                Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
                PegoutTransactionBuilder btcTransactionBuilder = PegoutTransactionBuilder.builder()
                    .withNetworkParameters(networkParameters)
                    .withActiveFederation(retiringFederation)
                    .withOutput(minimumPegoutTxValue, activeFederation.getAddress())
                    .withoutChange()
                    .withSignatures(retiringFederationKeys);
                addManyInputs(btcTransactionBuilder);
                return btcTransactionBuilder.build();
            }
        }

        void setUp(ForBlock activations) {
            repository = createRepository();
            bridgeStorageProvider = new BridgeStorageProvider(repository, networkParameters, activations);
            logs = new ArrayList<>();
            bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeMainnetConstants,
                activations,
                logs
            );

            StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
            federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

            // Move the required blocks ahead for the new powpeg to become active
            rskExecutionBlockNumber = activeFederation.getCreationBlockNumber()
                + federationMainnetConstants.getFederationActivationAge(activations);
            currentBlock = createRskBlock(rskExecutionBlockNumber);

            FederationSupportBuilder federationSupportBuilder = FederationSupportBuilder.builder();
            federationSupport = federationSupportBuilder
                .withFederationConstants(federationConstants)
                .withFederationStorageProvider(federationStorageProvider)
                .withRskExecutionBlock(currentBlock)
                .withActivations(activations)
                .build();

            BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(networkParameters, 100, 100);
            btcBlockStore = btcBlockStoreFactory.newInstance(repository, bridgeMainnetConstants, bridgeStorageProvider, activations);
            btcLockSenderProvider = new BtcLockSenderProvider();
            peginInstructionsProvider = new PeginInstructionsProvider();

            BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
            bridgeSupport = bridgeSupportBuilder
                .withActivations(activations)
                .withExecutionBlock(currentBlock)
                .withBridgeConstants(bridgeMainnetConstants)
                .withProvider(bridgeStorageProvider)
                .withRepository(repository)
                .withEventLogger(bridgeEventLogger)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .withPeginInstructionsProvider(peginInstructionsProvider)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .build();
        }

        private void setupActiveAndRetiringLegacyFed(ForBlock activations, int creationBlockNumber) {
            activeFederation = P2shErpFederationBuilder.builder()
                .withNetworkParameters(networkParameters)
                .withMembersBtcPublicKeys(activeFederationKeys)
                .withErpActivationDelay(activationDelay)
                .withErpPublicKeys(erpPubKeys)
                .withCreationBlockNumber(creationBlockNumber)
                .build();
            retiringFederation = P2shErpFederationBuilder.builder()
                .withNetworkParameters(networkParameters)
                .withMembersBtcPublicKeys(retiringFederationKeys)
                .withErpActivationDelay(activationDelay)
                .withErpPublicKeys(erpPubKeys)
                .withCreationBlockNumber(creationBlockNumber)
                .build();
            setUp(activations);
            federationStorageProvider.setNewFederation(activeFederation);
            federationStorageProvider.setOldFederation(retiringFederation);
        }

        private void setupActiveLegacyFed(ForBlock activations) {
            activeFederation = P2shErpFederationBuilder.builder()
                .withNetworkParameters(networkParameters)
                .withMembersBtcPublicKeys(activeFederationKeys)
                .withErpActivationDelay(activationDelay)
                .withErpPublicKeys(erpPubKeys)
                .withCreationBlockNumber(heightBeforeUsingPegoutIndex)
                .build();
            setUp(activations);
            federationStorageProvider.setNewFederation(activeFederation);
        }

        private void setupActiveAndRetiringFed() {
            retiringFederation = P2shErpFederationBuilder.builder()
                .withNetworkParameters(networkParameters)
                .withMembersBtcPublicKeys(retiringFederationKeys)
                .withErpActivationDelay(activationDelay)
                .withErpPublicKeys(erpPubKeys)
                .withCreationBlockNumber(heightAtWhichToStartUsingPegoutIndex)
                .build();
            activeFederation = P2shP2wshErpFederationBuilder.builder()
                .withNetworkParameters(networkParameters)
                .withMembersBtcPublicKeys(activeFederationKeys)
                .withErpActivationDelay(activationDelay)
                .withErpPublicKeys(erpPubKeys)
                .withCreationBlockNumber(heightAtWhichToStartUsingPegoutIndex)
                .build();

            setUp(allActivations);
            federationStorageProvider.setNewFederation(activeFederation);
            federationStorageProvider.setOldFederation(retiringFederation);
        }

        private void setupActiveFed() {
            activeFederation = P2shP2wshErpFederationBuilder.builder()
                .withNetworkParameters(networkParameters)
                .withMembersBtcPublicKeys(activeFederationKeys)
                .withErpActivationDelay(activationDelay)
                .withErpPublicKeys(erpPubKeys)
                .withCreationBlockNumber(heightAtWhichToStartUsingPegoutIndex)
                .build();
            setUp(allActivations);
            federationStorageProvider.setNewFederation(activeFederation);
        }

        private void registerReleaseTransaction(BtcTransaction releaseTx, int height) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
            PartialMerkleTree pmtWithTransactions = buildPMTAndRecreateChainForTransactionRegistration(
                bridgeStorageProvider,
                bridgeMainnetConstants,
                height,
                releaseTx,
                btcBlockStore
            );

            bridgeSupport.registerBtcTransaction(
                rskTx,
                releaseTx.bitcoinSerialize(),
                height,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeSupport.save();
        }

        private void registerPegoutTxSigHash(BtcTransaction pegout) {
            Sha256Hash sigHash = getSigHashForPegoutIndex(pegout)
                .orElseThrow(() -> new IllegalStateException("Could not compute sig hash for pegout"));
            bridgeStorageProvider.setPegoutTxSigHash(sigHash);
            bridgeStorageProvider.save();
        }

        private void assertReleaseTxWasProcessedWithNoNewUtxo(BtcTransaction releaseTx) throws IOException {
            assertReleaseTxWasProcessed(releaseTx);
            assertNoUtxoWasAddedInActiveFed();
            assertNoUtxoWasAddedInRetiringFed();
        }

        private void assertReleaseTxWasProcessed(BtcTransaction releaseTransaction) throws IOException {
            assertTransactionWasProcessed(bridgeStorageProvider, releaseTransaction.getHash(false), (int) currentBlock.getNumber());
        }

        private void assertUtxosAddedInActiveFed(int expectedUtxosCount) {
            assertEquals(expectedUtxosCount, federationSupport.getActiveFederationBtcUTXOs().size());
        }

        private void assertNoUtxoWasAddedInActiveFed() {
            assertTrue(federationSupport.getActiveFederationBtcUTXOs().isEmpty());
        }

        private void assertNoUtxoWasAddedInRetiringFed() {
            assertTrue(federationSupport.getRetiringFederationBtcUTXOs().isEmpty());
        }

        private void addManyInputs(PegoutTransactionBuilder btcTransactionBuilder) {
            for (int i = 0; i < 50; i++) {
                btcTransactionBuilder.withInput(BitcoinTestUtils.createHash(i + 1), FIRST_OUTPUT_INDEX, Coin.COIN);
            }
        }

        private BtcTransaction getReleaseTxWithOneInputAndOutputWithoutChange(Federation spendingFederation, List<BtcECKey> spendingFederationKeys, Address receiverAddress) {
            return PegoutTransactionBuilder.builder()
                .withNetworkParameters(networkParameters)
                .withActiveFederation(spendingFederation)
                .withInput(BTC_TX_HASH, FIRST_OUTPUT_INDEX, Coin.COIN)
                .withOutput(Coin.COIN, receiverAddress)
                .withoutChange()
                .withSignatures(spendingFederationKeys)
                .build();
        }
    }
}

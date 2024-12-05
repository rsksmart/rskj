package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
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
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.WhitelistStorageProvider;
import co.rsk.peg.whitelist.WhitelistSupportImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static co.rsk.peg.BridgeSupportTestUtil.mockChainOfStoredBlocks;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class RegisterBtcTransactionIT {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final FederationConstants federationMainnetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
    private static final ActivationConfig.ForBlock arrowhead600Activations = ActivationConfigsForTest.arrowhead600().forBlock(0);
    private static final ActivationConfig.ForBlock lovell700Activations = ActivationConfigsForTest.lovell700().forBlock(0);
    private WhitelistStorageProvider whitelistStorageProvider;
    private static final int FIRST_OUTPUT_INDEX = 0;
    private BridgeStorageProvider provider;
    private FederationStorageProvider federationStorageProvider;
    private Federation retiringFederation;
    private Federation activeFederation;
    private BtcBlockStoreWithCache.Factory mockFactory;
    private SignatureCache signatureCache;
    private BridgeEventLogger bridgeEventLogger;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;
    private final List<UTXO> retiringFederationUtxos = new ArrayList<>();
    private final List<UTXO> activeFederationUtxos = new ArrayList<>();
    private Block rskExecutionBlock;
    private Transaction rskTx;

    private int heightAtWhichToStartUsingPegoutIndex;

    private co.rsk.bitcoinj.core.BtcBlock registerHeader;

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

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), height + bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations());
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
                height + bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations(),
                height
        );
        return pmt;
    }

    private BridgeSupport buildBridgeSupport(ActivationConfig.ForBlock activations) {
        Repository repository = mock(Repository.class);
        when(repository.getBalance(PrecompiledContracts.BRIDGE_ADDR)).thenReturn(co.rsk.core.Coin.fromBitcoin(bridgeMainnetConstants.getMaxRbtc()));
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

    @BeforeEach
    void init() throws IOException {
        registerHeader = null;

        NetworkParameters btcParams = bridgeMainnetConstants.getBtcParams();

        List<BtcECKey> retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
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

        List<BtcECKey> activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
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

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
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

    private BtcTransaction getBtcTransaction(Coin amountToSend) {
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());
        return btcTransaction;
    }
}

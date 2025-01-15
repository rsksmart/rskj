package co.rsk.peg.federation;

import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BridgeSupportTestUtil;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.lockingcap.LockingCapStorageProviderImpl;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.lockingcap.LockingCapSupportImpl;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Triple;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;

class FederationChangeIT {

    private enum FederationType {
        NON_STANDARD_ERP,
        P2SH_ERP,
        STANDARD_MULTISIG
    }

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final List<Triple<BtcECKey, ECKey, ECKey>> ORIGINAL_FEDERATION_KEYS = List.of(
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
            ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
            ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
            ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
            ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
            ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
            ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("026b472f7d59d201ff1f540f111b6eb329e071c30a9d23e3d2bcd128fe73dc254c")),
            ECKey.fromPublicOnly(Hex.decode("0353dda9ae319eab0d3e1235896d58bd9840eadcf76c84244a5d7f60b1c66e45ce")),
            ECKey.fromPublicOnly(Hex.decode("030165892c353cd3752143b5b6c55372528e7279259fe1088d6f4dc957e146e557"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93")),
            ECKey.fromPublicOnly(Hex.decode("03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93")),
            ECKey.fromPublicOnly(Hex.decode("03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("0357f7ed4c118e581f49cd3b4d9dd1edb4295f4def49d6dcf2faaaaac87a1a0a42")),
            ECKey.fromPublicOnly(Hex.decode("03ff13a966f1e53af37ad1fa3681b1352238f4885c1d4159730f3503bb52d63b20")),
            ECKey.fromPublicOnly(Hex.decode("03ff13a966f1e53af37ad1fa3681b1352238f4885c1d4159730f3503bb52d63b20"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("03ae72827d25030818c4947a800187b1fbcc33ae751e248ae60094cc989fb880f6")),
            ECKey.fromPublicOnly(Hex.decode("03d7ff9b1de5cc746a93036b36f8d832ac1bfc64099f8aa37612745770d7fc4961")),
            ECKey.fromPublicOnly(Hex.decode("0300754b9dc92f27cd6702f06c460607a43c16de4531bfdc569bcdecdb12c54ccf"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("03e05bf6002b62651378b1954820539c36ca405cbb778c225395dd9ebff6780299")),
            ECKey.fromPublicOnly(Hex.decode("03095aba7a4f1fa0f98728e5230823d603abe517bdfeeb928861a73c4b9404aaf1")),
            ECKey.fromPublicOnly(Hex.decode("02b3e34f0898759a2b5e6acd88281638d41c8da04e1fba13b5b9c3c4bf42bea3b0"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("03ecd8af1e93c57a1b8c7f917bd9980af798adeb0205e9687865673353eb041e8d")),
            ECKey.fromPublicOnly(Hex.decode("03f4d76ec9a7a2722c0b06f5f4a489152244c8801e5ff2a43df7fefd75ce8e068f")),
            ECKey.fromPublicOnly(Hex.decode("02a935a8d59b92f9df82265cb983a76cca0308f82e9dc9dd92ff8887e2667d2a38"))
        ));
    private static final List<Triple<BtcECKey, ECKey, ECKey>> NEW_FEDERATION_KEYS = List.of(
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
            ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
            ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
            ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
            ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ),
        Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
            ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
            ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ));

    private Repository repository;
    private BridgeStorageProvider bridgeStorageProvider;
    private BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BtcBlockStore btcBlockStore;
    private BridgeEventLogger bridgeEventLogger;
    private FeePerKbSupport feePerKbSupport;
    private Block initialBlock;
    private StorageAccessor bridgeStorageAccessor;
    private FederationStorageProvider federationStorageProvider;
    private FederationSupportImpl federationSupport;
    private LockingCapSupport lockingCapSupport;
    private BridgeSupport bridgeSupport;

    @Test
    void whenAllActivationsArePresentAndFederationChanges_shouldCreateCommitAndActiveNewFed() throws Exception {
        // Arrange
        var activations = ActivationConfigsForTest.all().forBlock(0);
        setUpFederationChange(activations);
        // Create a default original federation using the list of UTXOs
        var originalFederation = createOriginalFederation(
            FederationType.P2SH_ERP, ORIGINAL_FEDERATION_KEYS, activations);
        var originalUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(
            BRIDGE_CONSTANTS.getBtcParams(), activations);
       
        // Act
        // Create pending federation using the new federation keys
        var newFederation = createPendingFederation(NEW_FEDERATION_KEYS, activations); 
        commitPendingFederation();
        // Since Lovell is activated we will commit the proposed federation
        commitProposedFederation(activations);
        // Move blockchain until the activation phase
        activateNewFederation(activations);

        // Assert
        assertUTXOsReferenceMovedFromNewToOldFederation(originalUTXOs, activations);
        assertNewAndOldFederationsHaveExpectedAddress(newFederation.getAddress(), originalFederation.getAddress());
        assertFundsWereNotMigrated();
    }
  
    /* Change federation related methods */

    private void setUpFederationChange(ActivationConfig.ForBlock activations) throws Exception {
        repository = BridgeSupportTestUtil.createRepository();
        repository.addBalance(
            PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMaxRbtc()));

        bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_CONSTANTS.getBtcParams(),
            activations);

        btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            BRIDGE_CONSTANTS.getBtcParams(),
            100,
            100);
        btcBlockStore = btcBlockStoreFactory.newInstance(
            repository,
            BRIDGE_CONSTANTS,
            bridgeStorageProvider,
            activations);
        // Setting a chain head different from genesis to avoid having to read the checkpoints file
        addNewBtcBlockOnTipOfChain(btcBlockStore);
        repository.save();

        bridgeEventLogger = new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, activations, new ArrayList<>());

        bridgeStorageAccessor = new InMemoryStorage();

        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        var initialBlockNumber = 0L;
        var initialBlockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(initialBlockNumber)
            .build();
        initialBlock = Block.createBlockFromHeader(initialBlockHeader, true);

        federationSupport = new FederationSupportImpl(
            BRIDGE_CONSTANTS.getFederationConstants(), federationStorageProvider, initialBlock, activations);

        var signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        var lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(
            lockingCapStorageProvider,
            activations,
            BRIDGE_CONSTANTS.getLockingCapConstants(),
            signatureCache);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.COIN);
    }

    private Federation createOriginalFederation(
          FederationType federationType,
          List<Triple<BtcECKey, ECKey, ECKey>> federationKeys,
          ActivationConfig.ForBlock activations) throws Exception {
        var originalFederationMembers = federationKeys.stream()
            .map(originalFederatorKey ->
                new FederationMember(
                    originalFederatorKey.getLeft(),
                    originalFederatorKey.getMiddle(),
                    originalFederatorKey.getRight()))
            .toList();
        var originalFederationArgs = new FederationArgs(
            originalFederationMembers,
            Instant.EPOCH,
            0,
            BRIDGE_CONSTANTS.getBtcParams());
        var erpPubKeys =
            BRIDGE_CONSTANTS.getFederationConstants().getErpFedPubKeysList();
        var activationDelay =
            BRIDGE_CONSTANTS.getFederationConstants().getErpFedActivationDelay();

        Federation originalFederation;
        switch (federationType) {
            case NON_STANDARD_ERP -> 
                originalFederation = FederationFactory.buildNonStandardErpFederation(
                    originalFederationArgs, erpPubKeys, activationDelay, activations);
            case P2SH_ERP -> {
                originalFederation = FederationFactory.buildP2shErpFederation(
                    originalFederationArgs, erpPubKeys, activationDelay);
            }
            default -> throw new Exception(
                String.format("Federation type %s is not supported", federationType));
        }

        // Set original federation
        federationStorageProvider.setNewFederation(originalFederation);

        // Set new UTXOs
        var originalUTXOs = createRandomUTXOs(originalFederation.getAddress());
        bridgeStorageAccessor.saveToRepository(
            NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), originalUTXOs, BridgeSerializationUtils::serializeUTXOList);

         return originalFederation;
    }  

    private Federation createPendingFederation(
          List<Triple<BtcECKey, ECKey, ECKey>> federationKeys,
          ActivationConfig.ForBlock activations) {
        // Create pending federation (doing this to avoid voting the pending Federation)
        var newFederationMembers = federationKeys.stream()
            .map(newFederatorKey ->
                new FederationMember(
                    newFederatorKey.getLeft(),
                    newFederatorKey.getMiddle(),
                    newFederatorKey.getRight()))
            .toList();
        var pendingFederation = new PendingFederation(newFederationMembers);

        // Set pending federation
        federationStorageProvider.setPendingFederation(pendingFederation);
        federationStorageProvider.save(BRIDGE_CONSTANTS.getBtcParams(), activations);

        // Return what will be the new federation
        return pendingFederation.buildFederation(
            Instant.EPOCH, 0L, BRIDGE_CONSTANTS.getFederationConstants(), activations);
    }

    private void commitPendingFederation() {
        // Pending Federation should exist
        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertNotNull(pendingFederation);
    
        // Proceed with the powpeg change
        federationSupport.commitFederation(false, pendingFederation.getHash(), bridgeEventLogger);

        // Since the proposed federation is committed, it should be null in storage
        pendingFederation = federationStorageProvider.getPendingFederation();
        assertNull(pendingFederation);
    }

    private void commitProposedFederation(ActivationConfig.ForBlock activations) {
        // Verify that the proposed federation exists in storage
        var proposedFederation = 
            federationStorageProvider.getProposedFederation(BRIDGE_CONSTANTS.getFederationConstants(), activations);
        assertTrue(proposedFederation.isPresent());

        // As in commitPendingFederation util method, to avoid the SVP process
        // we will commit directly
        federationSupport.commitProposedFederation();
    
        // Since the proposed federation is committed, it should be null in storage
        proposedFederation = 
            federationStorageProvider.getProposedFederation(BRIDGE_CONSTANTS.getFederationConstants(), activations);
        assertTrue(proposedFederation.isEmpty());
    }
    
    private void activateNewFederation(ActivationConfig.ForBlock activations) {
        // Move the required blocks ahead for the new powpeg to become active
        var blockNumber = 
            initialBlock.getNumber() + BRIDGE_CONSTANTS.getFederationConstants().getFederationActivationAge(activations);
        var initialBlockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        var activationBlock = Block.createBlockFromHeader(initialBlockHeader, true);

        // Now the new bridgeSupport points to the new block where the new federation
        // is considered to be active
        bridgeSupport = getBridgeSupportFromExecutionBlock(activationBlock, activations);
    }
    
    private BridgeSupport getBridgeSupportFromExecutionBlock(Block executionBlock, ActivationConfig.ForBlock activations) {
        FederationSupport fedSupport = FederationSupportBuilder.builder()
            .withFederationConstants(BRIDGE_CONSTANTS.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .build();

        return BridgeSupportBuilder.builder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFederationSupport(fedSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();
    }

    private static void addNewBtcBlockOnTipOfChain(BtcBlockStore blockStore) throws Exception {
        var chainHead = blockStore.getChainHead();
        var btcBlock = new BtcBlock(
            BRIDGE_CONSTANTS.getBtcParams(),
            1,
            chainHead.getHeader().getHash(),
            BitcoinTestUtils.createHash(chainHead.getHeight() + 1),
            0,
            0,
            0,
            List.of());
        var storedBlock = new StoredBlock(
            btcBlock,
            BigInteger.ZERO,
            chainHead.getHeight() + 1
        );

        blockStore.put(storedBlock);
        blockStore.setChainHead(storedBlock);
    }

    private List<UTXO> createRandomUTXOs(Address owner) {
        Script outputScript = ScriptBuilder.createOutputScript(owner);
        List<UTXO> utxos = new ArrayList<>();

        int howMany = getRandomInt(50, 500);
        for (int i = 1; i <= howMany; i++) {
            Coin randomValue = Coin.valueOf(getRandomInt(10_000, 1_000_000_000));
            Sha256Hash utxoHash = BitcoinTestUtils.createHash(i);
            utxos.add(new UTXO(utxoHash, 0, randomValue, 0, false, outputScript));
        }

        return utxos;
    }
    
    private int getRandomInt(int min, int max) {
        return TestUtils.generateInt(FederationChangeIT.class.toString() + min, max - min + 1) + min;
    }
    
    /* Assert federation change related methods */

    private void assertUTXOsReferenceMovedFromNewToOldFederation(List<UTXO> originalUTXOs, ActivationConfig.ForBlock activations) {
        // Assert old federation exists in storage
        assertNotNull(
            federationStorageProvider.getOldFederation(BRIDGE_CONSTANTS.getFederationConstants(), activations));
        // Assert new federation exists in storage
        assertNotNull(
            federationStorageProvider.getNewFederation(BRIDGE_CONSTANTS.getFederationConstants(), activations));
        // Assert old federation holds the original utxos
        List<UTXO> utxosToMigrate = federationStorageProvider.getOldFederationBtcUTXOs();
        assertTrue(originalUTXOs.stream().allMatch(utxosToMigrate::contains));
        // Assert the new federation does not have any utxos yet
        assertTrue(federationStorageProvider
            .getNewFederationBtcUTXOs(BRIDGE_CONSTANTS.getBtcParams(), activations)
            .isEmpty());
    }

    private void assertNewAndOldFederationsHaveExpectedAddress(
          Address expectedNewFederationAddress, Address expectedOldFederationAddress) {
        // New active and retiring federation
        assertEquals(expectedNewFederationAddress, bridgeSupport.getActiveFederationAddress());
        assertEquals(expectedOldFederationAddress, bridgeSupport.getRetiringFederationAddress());
    }
    
    private void assertFundsWereNotMigrated() throws Exception {
        // Pegouts waiting for confirmations should be empty
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
    }
}

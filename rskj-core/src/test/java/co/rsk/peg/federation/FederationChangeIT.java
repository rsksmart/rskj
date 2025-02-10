package co.rsk.peg.federation;

import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.bitcoinj.script.ScriptParser;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BridgeSupportTestUtil;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.PegoutsWaitingForConfirmations;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.bitcoin.UtxoUtils;
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
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Triple;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
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
    private static final Transaction UPDATE_COLLECTIONS_TX = buildUpdateCollectionsTx();
    private static final Transaction FIRST_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(null, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
    private static final Transaction SECOND_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(null, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());

    private Repository repository;
    private BridgeStorageProvider bridgeStorageProvider;
    private BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BtcBlockStore btcBlockStore;
    private BridgeEventLogger bridgeEventLogger;
    private FeePerKbSupport feePerKbSupport;
    private Block currentBlock;
    private StorageAccessor bridgeStorageAccessor;
    private FederationStorageProvider federationStorageProvider;
    private FederationSupportImpl federationSupport;
    private LockingCapSupport lockingCapSupport;
    private BridgeSupport bridgeSupport;

    @Test
    void whenAllActivationsArePresentAndFederationChanges_shouldSuccesfullyChangeFederation() throws Exception {
        // Arrange
   
        var activations = ActivationConfigsForTest.all().forBlock(0);
        setUp(activations);
        // Create a default original federation using the list of UTXOs
        var originalFederation = createOriginalFederation(
            FederationType.P2SH_ERP, ORIGINAL_FEDERATION_KEYS, activations);
        var originalUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(
            BRIDGE_CONSTANTS.getBtcParams(), activations);
       
        // Act & Assert
   
        // Create pending federation using the new federation keys
        voteToCreateEmptyPendingFederation();
        voteToAddFederatorPublicKeysToPendingFederation(NEW_FEDERATION_KEYS);
        var newFederation = voteToCommitPendingFederation(activations);
        // Since Lovell is activated we will commit the proposed federation
        commitProposedFederation(activations);
        // Next federation creation block height should be as expected
        assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation(
            FederationType.P2SH_ERP, originalFederation, activations);
        // Move blockchain until the activation phase
        activateNewFederation(activations);
   
        assertUTXOsReferenceMovedFromNewToOldFederation(originalUTXOs, activations);
        assertNewAndOldFederationsHaveExpectedAddress(
            newFederation.getAddress(), originalFederation.getAddress());
        assertMigrationHasNotStarted(activations);

        // Move blockchain until the migration phase
        activateMigration(activations);
        // Migrate funds
        migrateUTXOs();

        assertNewAndOldFederationsHaveExpectedAddress(
            newFederation.getAddress(), originalFederation.getAddress());
        assertMigrationHasStarted();
        verifySigHashes(activations);
        verifyPegoutTransactionCreatedEventWasEmitted(activations);
        verifyPegouts(activations);
        
        // Move blockchain until the end of the migration phase
        endMigration(activations);

        assertMigrationHasEnded(newFederation);
    }
  
    /* Change federation related methods */

    private void setUp(ActivationConfig.ForBlock activations) throws Exception {
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

        var bridgeEventLoggerImpl = new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, activations, new ArrayList<>());
        bridgeEventLogger = spy(bridgeEventLoggerImpl);

        bridgeStorageAccessor = new InMemoryStorage();

        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        var blockNumber = 0L;
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        federationSupport = new FederationSupportImpl(
            BRIDGE_CONSTANTS.getFederationConstants(), federationStorageProvider, currentBlock, activations);

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

    private int voteToCreatePendingFederation(Transaction tx) {
        var createFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});
        return federationSupport.voteFederationChange(tx, createFederationAbiCallSpec, null, bridgeEventLogger);
    }

    private int voteToAddFederatorPublicKeysToPendingFederation(Transaction tx, BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        ABICallSpec addFederatorAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(),
            new byte[][]{ btcPublicKey.getPubKey(), rskPublicKey.getPubKey(), mstPublicKey.getPubKey() }
        );

        return federationSupport.voteFederationChange(tx, addFederatorAbiCallSpec, null, bridgeEventLogger);
    }

    private int voteCommitPendingFederation(Transaction tx) {
        var pendingFederationHash = federationSupport.getPendingFederationHash();
        var commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{ pendingFederationHash.getBytes() });

        return federationSupport.voteFederationChange(tx, commitFederationAbiCallSpec, null, bridgeEventLogger);
    }
  
    private void voteToCreateEmptyPendingFederation() {
        // Voting with enough authorizers to create the pending federation
        var resultFromFirstAuthorizer = voteToCreatePendingFederation(FIRST_AUTHORIZED_TX);
        var resultFromSecondAuthorizer = voteToCreatePendingFederation(SECOND_AUTHORIZED_TX);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);

        assertEquals(0, federationSupport.getPendingFederationSize());
        assertNotNull(federationSupport.getPendingFederationHash());
    }

    private void voteToAddFederatorPublicKeysToPendingFederation(BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        int resultFromFirstAuthorizer = voteToAddFederatorPublicKeysToPendingFederation(FIRST_AUTHORIZED_TX, btcPublicKey, rskPublicKey, mstPublicKey);
        int resultFromSecondAuthorizer = voteToAddFederatorPublicKeysToPendingFederation(SECOND_AUTHORIZED_TX, btcPublicKey, rskPublicKey, mstPublicKey);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);
    }

    private void voteToAddFederatorPublicKeysToPendingFederation(List<Triple<BtcECKey, ECKey, ECKey>> newFederationKeys) {
        for (Triple<BtcECKey, ECKey, ECKey> keys : newFederationKeys) {
            BtcECKey memberBtcKey = keys.getLeft();
            ECKey memberRskKey = keys.getMiddle();
            ECKey memberMstKey = keys.getRight();

            voteToAddFederatorPublicKeysToPendingFederation(memberBtcKey, memberRskKey, memberMstKey);
        }
    }

    private Federation voteToCommitPendingFederation(ActivationConfig.ForBlock activations) {
        // Pending Federation should exist
        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertNotNull(pendingFederation);

        int firstVoteResult = voteCommitPendingFederation(FIRST_AUTHORIZED_TX);
        int secondVoteResult = voteCommitPendingFederation(SECOND_AUTHORIZED_TX);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteResult);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteResult);

        // Since the proposed federation is committed, it should be null in storage
        assertNull(federationStorageProvider.getPendingFederation());
      
        return pendingFederation.buildFederation(
            Instant.EPOCH, 0L, BRIDGE_CONSTANTS.getFederationConstants(), activations);
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
        proposedFederation = federationStorageProvider.getProposedFederation(BRIDGE_CONSTANTS.getFederationConstants(), activations);
        assertTrue(proposedFederation.isEmpty());
    }
    
    private void activateNewFederation(ActivationConfig.ForBlock activations) {
        // Move the required blocks ahead for the new powpeg to become active
        var blockNumber = 
            currentBlock.getNumber() + BRIDGE_CONSTANTS.getFederationConstants().getFederationActivationAge(activations);
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        // Now the new bridgeSupport points to the new block where the new federation
        // is considered to be active
        bridgeSupport = getBridgeSupportFromExecutionBlock(currentBlock, activations);
    }

    private void activateMigration(ActivationConfig.ForBlock activations) {
        // Move the required blocks ahead for the new federation to start migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = 
            currentBlock.getNumber() + BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationBegin() + 1L;
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        bridgeSupport = getBridgeSupportFromExecutionBlock(currentBlock, activations);
    }

    private void endMigration(ActivationConfig.ForBlock activations) throws Exception {
        // Move the required blocks ahead for the new federation to finish migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = 
            currentBlock.getNumber() + BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationEnd(activations) + 1L;
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        bridgeSupport = getBridgeSupportFromExecutionBlock(currentBlock, activations);

        // The first update collections after the migration finished should get rid of the retiring powpeg
        var updateCollectionsTx = UPDATE_COLLECTIONS_TX;
        bridgeSupport.updateCollections(updateCollectionsTx);
        bridgeSupport.save();
    }

    private void migrateUTXOs() throws Exception {
        // Migrate while there are still utxos to migrate
        var remainingUTXOs = federationStorageProvider.getOldFederationBtcUTXOs();
        var updateCollectionsTx = UPDATE_COLLECTIONS_TX;
        while (!remainingUTXOs.isEmpty()) {
            bridgeSupport.updateCollections(updateCollectionsTx);
            bridgeSupport.save();
        }
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

    private Script getFederationDefaultRedeemScript(Federation federation) {
        return federation instanceof ErpFederation ?
            ((ErpFederation) federation).getDefaultRedeemScript() :
            federation.getRedeemScript();
    }
   
    private static Script getFederationDefaultP2SHScript(Federation federation) {
        return federation instanceof ErpFederation ?
            ((ErpFederation) federation).getDefaultP2SHScript() :
            federation.getP2SHScript();
    }

    private static Transaction buildUpdateCollectionsTx() {
        var nonce = 3;
        var value = 0;
        var gasPrice = BigInteger.valueOf(0);
        var gasLimit = BigInteger.valueOf(100000);
        var rskTx = CallTransaction.createCallTransaction(nonce, gasPrice.longValue(),
                gasLimit.longValue(), PrecompiledContracts.BRIDGE_ADDR, value,
                Bridge.UPDATE_COLLECTIONS, Constants.MAINNET_CHAIN_ID);
        var randomKey = BtcECKey.fromPrivate(Hex.decode("45c5b07fc1a6f58892615b7c31dca6c96db58c4bbc538a6b8a22999aaa860c32"));
        rskTx.sign(randomKey.getPrivKeyBytes());
        return rskTx;
    }
    
    
    /* Assert and verify federation change related methods */

    private void assertUTXOsReferenceMovedFromNewToOldFederation(List<UTXO> utxos, ActivationConfig.ForBlock activations) {
        // Assert old federation exists in storage
        assertNotNull(
            federationStorageProvider.getOldFederation(BRIDGE_CONSTANTS.getFederationConstants(), activations));
        // Assert new federation exists in storage
        assertNotNull(
            federationStorageProvider.getNewFederation(BRIDGE_CONSTANTS.getFederationConstants(), activations));
        // Assert old federation holds the original utxos
        List<UTXO> utxosToMigrate = federationStorageProvider.getOldFederationBtcUTXOs();
        assertTrue(utxos.stream().allMatch(utxosToMigrate::contains));
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
    
    private void assertMigrationHasNotStarted(ActivationConfig.ForBlock activations) throws Exception {
        // Current block is behind fedActivationAge + fundsMigrationAgeBegin
        var blockNumber = BRIDGE_CONSTANTS.getFederationConstants().getFederationActivationAge(activations) + 
            BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationBegin();
        assertTrue(currentBlock.getNumber() <= blockNumber);

        // Pegouts waiting for confirmations should be empty
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
    }
     
    private void assertMigrationHasStarted() throws Exception {
        // Pegouts waiting for confirmations should not be empty
        assertFalse(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
    }

    private void assertMigrationHasEnded(Federation newFederation) {
        // New active federation still there, retiring federation no longer there
        assertEquals(newFederation, bridgeSupport.getActiveFederation());
        assertNull(bridgeSupport.getRetiringFederationAddress());
    }
    
    private void assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation(
          FederationType federationType, Federation originalFederation, ActivationConfig.ForBlock activations) {
        var lastRetiredFederationP2SHScriptOptional = 
            federationStorageProvider.getLastRetiredFederationP2SHScript(activations);
        assertTrue(lastRetiredFederationP2SHScriptOptional.isPresent());
        Script lastRetiredFederationP2SHScript = lastRetiredFederationP2SHScriptOptional.get();

        if (activations.isActive(ConsensusRule.RSKIP377)){
            if (federationType == FederationType.NON_STANDARD_ERP
                || federationType == FederationType.P2SH_ERP) {
                assertNotEquals(lastRetiredFederationP2SHScript, originalFederation.getP2SHScript());
            }
            assertEquals(lastRetiredFederationP2SHScript, getFederationDefaultP2SHScript(originalFederation));
        } else {
            if (federationType == FederationType.NON_STANDARD_ERP
                || federationType == FederationType.P2SH_ERP) {
                assertEquals(lastRetiredFederationP2SHScript, originalFederation.getP2SHScript());
                assertNotEquals(lastRetiredFederationP2SHScript, getFederationDefaultP2SHScript(originalFederation));
            } else {
                assertEquals(lastRetiredFederationP2SHScript, originalFederation.getP2SHScript());
                assertEquals(lastRetiredFederationP2SHScript, getFederationDefaultP2SHScript(originalFederation));
            }
        }
    }
   
    private void verifySigHashes(ActivationConfig.ForBlock activations) throws Exception {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries().stream()
            .map(Entry::getBtcTransaction)
            .toList();

        pegoutsTxs.forEach(
            pegoutTx -> assertPegoutTxSigHashesAreSaved(pegoutTx, activations));
    }

    private void verifyPegoutTransactionCreatedEventWasEmitted(ActivationConfig.ForBlock activations) throws Exception {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries().stream()
            .map(Entry::getBtcTransaction)
            .toList();

        if (activations.isActive(ConsensusRule.RSKIP428)) {
            pegoutsTxs.forEach(this::verifyPegoutTransactionCreatedEvent);
        } else {
            verify(bridgeEventLogger, never()).logPegoutTransactionCreated(any(), any());
        }
    }
    
    private void verifyPegouts(ActivationConfig.ForBlock activations) throws Exception {
        var activeFederation = federationStorageProvider.getNewFederation(
            BRIDGE_CONSTANTS.getFederationConstants(), activations);
        var retiringFederation = federationStorageProvider.getOldFederation(
            BRIDGE_CONSTANTS.getFederationConstants(), activations);

        for (PegoutsWaitingForConfirmations.Entry pegoutEntry : bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries()) {
            var pegoutBtcTransaction = pegoutEntry.getBtcTransaction();
            for (TransactionInput input : pegoutBtcTransaction.getInputs()) {
                // Each input should contain the right scriptSig
                var inputScriptChunks = input.getScriptSig().getChunks();
                var inputRedeemScript = new Script(inputScriptChunks.get(inputScriptChunks.size() - 1).data);

                // Get the standard redeem script to compare against, since it could be a flyover redeem script
                var redeemScriptChunks = ScriptParser.parseScriptProgram(
                    inputRedeemScript.getProgram());

                var redeemScriptParser = RedeemScriptParserFactory.get(redeemScriptChunks);
                var inputStandardRedeemScriptChunks = redeemScriptParser.extractStandardRedeemScriptChunks();
                var inputStandardRedeemScript = new ScriptBuilder().addChunks(inputStandardRedeemScriptChunks).build();

                Optional<Federation> spendingFederationOptional = Optional.empty();
                if (inputStandardRedeemScript.equals(getFederationDefaultRedeemScript(activeFederation))) {
                    spendingFederationOptional = Optional.of(activeFederation);
                } else if (retiringFederation != null &&
                    inputStandardRedeemScript.equals(getFederationDefaultRedeemScript(retiringFederation))) {
                    spendingFederationOptional = Optional.of(retiringFederation);
                } else {
                    fail("Pegout scriptsig does not match any Federation");
                }

                // Check the script sig composition
                Federation spendingFederation = spendingFederationOptional.get();
                assertEquals(ScriptOpCodes.OP_0, inputScriptChunks.get(0).opcode);
                for (int i = 1; i <= spendingFederation.getNumberOfSignaturesRequired(); i++) {
                    assertEquals(ScriptOpCodes.OP_0, inputScriptChunks.get(i).opcode);
                }

                int index = spendingFederation.getNumberOfSignaturesRequired() + 1;
                if (spendingFederation instanceof ErpFederation) {
                    // Should include an additional OP_0
                    assertEquals(ScriptOpCodes.OP_0, inputScriptChunks.get(index).opcode);
                }
            }
        }
    }

    private void assertPegoutTxSigHashesAreSaved(BtcTransaction pegoutTx, ActivationConfig.ForBlock activations) {
        var lastPegoutSigHash = BitcoinUtils.getFirstInputSigHash(pegoutTx);
        assertTrue(lastPegoutSigHash.isPresent());
        assertTrue(bridgeStorageProvider.hasPegoutTxSigHash(lastPegoutSigHash.get()));
    }
    
    // comment for now
    // private void assertPegoutTxSigHashesAreNotSaved(BtcTransaction pegoutTx, ActivationConfig.ForBlock activations) {
    //     var lastPegoutSigHash = BitcoinUtils.getFirstInputSigHash(pegoutTx);
    //     assertTrue(lastPegoutSigHash.isPresent());
    //     assertFalse(bridgeStorageProvider.hasPegoutTxSigHash(lastPegoutSigHash.get()));
    // }

    private void verifyPegoutTransactionCreatedEvent(BtcTransaction pegoutTx) {
        var pegoutTxHash = pegoutTx.getHash();
        var outpointValues = UtxoUtils.extractOutpointValues(pegoutTx);
        verify(bridgeEventLogger).logPegoutTransactionCreated(pegoutTxHash, outpointValues);
    }
}

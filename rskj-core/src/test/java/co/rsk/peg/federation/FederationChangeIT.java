package co.rsk.peg.federation;

import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.*;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;

class FederationChangeIT {

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final List<FederationMember> ORIGINAL_FEDERATION_MEMBERS = FederationTestUtils.getFederationMembers(9);
    private static final List<FederationMember> NEW_FEDERATION_MEMBERS = FederationTestUtils.getFederationMembers(9);
    private static final SignatureCache SIGNATURE_CACHE = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    private static final Transaction UPDATE_COLLECTIONS_TX = buildUpdateCollectionsTx();
    private static final Transaction FIRST_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(SIGNATURE_CACHE, FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress());
    private static final Transaction SECOND_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(SIGNATURE_CACHE, FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress());
    private static final ActivationConfig.ForBlock ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0);

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
   
        setUp();
        // Create a default original federation using the list of UTXOs
        var originalFederation = createOriginalFederation(ORIGINAL_FEDERATION_MEMBERS);
        var originalUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(BRIDGE_CONSTANTS.getBtcParams(), ACTIVATIONS);
        var expectedFederation = createExpectedFederation(NEW_FEDERATION_MEMBERS);
       
        // Act & Assert
   
        // Create pending federation using the new federation keys
        voteToCreateEmptyPendingFederation();
        voteToAddFederatorPublicKeysToPendingFederation(NEW_FEDERATION_MEMBERS);

        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertPendingFederationIsBuiltAsExpected(pendingFederation);

        voteToCommitPendingFederation();
        var newFederation = federationSupport.getActiveFederation();
        assertEquals(expectedFederation, newFederation);
        
        // Since Lovell is activated we will commit the proposed federation
        commitProposedFederation();
        assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation(
            originalFederation);

        // Move blockchain until the activation phase
        activateNewFederation();
   
        assertUTXOsReferenceMovedFromNewToOldFederation(originalUTXOs);
        assertNewAndOldFederationsHaveExpectedAddress(
            newFederation.getAddress(), originalFederation.getAddress());
        assertMigrationHasNotStarted();

        // Move blockchain until the migration phase
        activateMigration();
        // Migrate funds
        migrateUTXOs();

        assertNewAndOldFederationsHaveExpectedAddress(
            newFederation.getAddress(), originalFederation.getAddress());
        assertMigrationHasStarted();
        verifySigHashes();
        verifyPegoutTransactionCreatedEventWasEmitted();
        verifyPegouts();
        
        // Move blockchain until the end of the migration phase
        endMigration();

        assertMigrationHasEnded(newFederation);
    }
  
    /* Change federation related methods */

    private void setUp() throws Exception {
        repository = BridgeSupportTestUtil.createRepository();
        repository.addBalance(
            PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMaxRbtc()));

        bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            BRIDGE_CONSTANTS.getBtcParams(),
            ACTIVATIONS);

        btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            BRIDGE_CONSTANTS.getBtcParams(),
            100,
            100);
        btcBlockStore = btcBlockStoreFactory.newInstance(
            repository,
            BRIDGE_CONSTANTS,
            bridgeStorageProvider,
            ACTIVATIONS);
        // Setting a chain head different from genesis to avoid having to read the checkpoints file
        addNewBtcBlockOnTipOfChain(btcBlockStore);
        repository.save();

        var bridgeEventLoggerImpl = new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, ACTIVATIONS, new ArrayList<>());
        bridgeEventLogger = spy(bridgeEventLoggerImpl);

        bridgeStorageAccessor = new InMemoryStorage();

        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        var blockNumber = 0L;
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        federationSupport = new FederationSupportImpl(
            BRIDGE_CONSTANTS.getFederationConstants(), federationStorageProvider, currentBlock, ACTIVATIONS);

        var lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(
            lockingCapStorageProvider,
            ACTIVATIONS,
            BRIDGE_CONSTANTS.getLockingCapConstants(),
            SIGNATURE_CACHE);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.COIN);
    }

    private Federation createOriginalFederation(List<FederationMember> federationMembers) throws Exception {
        var originalFederationArgs = new FederationArgs(
            federationMembers,
            Instant.EPOCH,
            0,
            BRIDGE_CONSTANTS.getBtcParams());
        var erpPubKeys =
            BRIDGE_CONSTANTS.getFederationConstants().getErpFedPubKeysList();
        var activationDelay =
            BRIDGE_CONSTANTS.getFederationConstants().getErpFedActivationDelay();

        Federation originalFederation = FederationFactory.buildP2shErpFederation(
            originalFederationArgs, erpPubKeys, activationDelay);
        // Set original federation
        federationStorageProvider.setNewFederation(originalFederation);

        // Set new UTXOs
        var originalUTXOs = createRandomUTXOs(originalFederation.getAddress());
        bridgeStorageAccessor.saveToRepository(
            NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), originalUTXOs, BridgeSerializationUtils::serializeUTXOList);

         return originalFederation;
    }  

    private Federation createExpectedFederation(List<FederationMember> federationMembers) {
        var expectedFederationArgs = new FederationArgs(
            federationMembers,
            Instant.EPOCH,
            0,
            BRIDGE_CONSTANTS.getBtcParams());
        var erpPubKeys =
            BRIDGE_CONSTANTS.getFederationConstants().getErpFedPubKeysList();
        var activationDelay =
            BRIDGE_CONSTANTS.getFederationConstants().getErpFedActivationDelay();

        return FederationFactory.buildP2shErpFederation(
            expectedFederationArgs, erpPubKeys, activationDelay);
    }

    private int voteToCreatePendingFederation(Transaction tx) {
        var createFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});
        return federationSupport.voteFederationChange(tx, createFederationAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private int voteToAddFederatorPublicKeysToPendingFederation(Transaction tx, BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        ABICallSpec addFederatorAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(),
            new byte[][]{ btcPublicKey.getPubKey(), rskPublicKey.getPubKey(), mstPublicKey.getPubKey() }
        );

        return federationSupport.voteFederationChange(tx, addFederatorAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private int voteCommitPendingFederation(Transaction tx) {
        var pendingFederationHash = federationSupport.getPendingFederationHash();
        var commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{ pendingFederationHash.getBytes() });

        return federationSupport.voteFederationChange(tx, commitFederationAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
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

    private void voteToAddFederatorPublicKeysToPendingFederation(List<FederationMember> newFederationMembers) {
        var expectedPendingFederationSize = 0;

        for (FederationMember member : newFederationMembers) {
            var memberBtcKey = member.getBtcPublicKey();
            var memberRskKey = member.getRskPublicKey();
            var memberMstKey = member.getMstPublicKey();

            voteToAddFederatorPublicKeysToPendingFederation(memberBtcKey, memberRskKey, memberMstKey);

            assertEquals(++expectedPendingFederationSize, federationSupport.getPendingFederationSize());
            assertTrue(federationStorageProvider.getPendingFederation().getMembers().contains(member));
        }
    }

    private void voteToCommitPendingFederation() {
        // Pending Federation should exist
        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertNotNull(pendingFederation);

        var firstVoteResult = voteCommitPendingFederation(FIRST_AUTHORIZED_TX);
        var secondVoteResult = voteCommitPendingFederation(SECOND_AUTHORIZED_TX);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteResult);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteResult);

        // Since the proposed federation is committed, it should be null in storage
        assertNull(federationStorageProvider.getPendingFederation());
    }

    private void commitProposedFederation() {
        // Verify that the proposed federation exists in storage
        var proposedFederation = 
            federationStorageProvider.getProposedFederation(BRIDGE_CONSTANTS.getFederationConstants(), ACTIVATIONS);
        assertTrue(proposedFederation.isPresent());

        // As in commitPendingFederation util method, to avoid the SVP process
        // we will commit directly
        federationSupport.commitProposedFederation();
    
        // Since the proposed federation is committed, it should be null in storage
        proposedFederation = federationStorageProvider.getProposedFederation(BRIDGE_CONSTANTS.getFederationConstants(), ACTIVATIONS);
        assertTrue(proposedFederation.isEmpty());
    }
    
    private void activateNewFederation() {
        // Move the required blocks ahead for the new powpeg to become active
        var blockNumber = 
            currentBlock.getNumber() + BRIDGE_CONSTANTS.getFederationConstants().getFederationActivationAge(ACTIVATIONS);
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        // Now the new bridgeSupport points to the new block where the new federation
        // is considered to be active
        bridgeSupport = getBridgeSupportFromExecutionBlock(currentBlock);
    }

    private void activateMigration() {
        // Move the required blocks ahead for the new federation to start migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = 
            currentBlock.getNumber() + BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationBegin() + 1L;
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        bridgeSupport = getBridgeSupportFromExecutionBlock(currentBlock);
    }

    private void endMigration() throws Exception {
        // Move the required blocks ahead for the new federation to finish migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = 
            currentBlock.getNumber() + BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationEnd(ACTIVATIONS) + 1L;
        var blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .build();
        currentBlock = Block.createBlockFromHeader(blockHeader, true);

        bridgeSupport = getBridgeSupportFromExecutionBlock(currentBlock);

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

    private BridgeSupport getBridgeSupportFromExecutionBlock(Block executionBlock) {
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
            .withActivations(ACTIVATIONS)
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

        int howMany = getRandomInt(1, 50);
        for (int i = 1; i < howMany; i++) {
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

    private void assertUTXOsReferenceMovedFromNewToOldFederation(List<UTXO> utxos) {
        // Assert old federation exists in storage
        assertNotNull(
            federationStorageProvider.getOldFederation(BRIDGE_CONSTANTS.getFederationConstants(), ACTIVATIONS));
        // Assert new federation exists in storage
        assertNotNull(
            federationStorageProvider.getNewFederation(BRIDGE_CONSTANTS.getFederationConstants(), ACTIVATIONS));
        // Assert old federation holds the original utxos
        List<UTXO> utxosToMigrate = federationStorageProvider.getOldFederationBtcUTXOs();
        assertTrue(utxos.stream().allMatch(utxosToMigrate::contains));
        // Assert the new federation does not have any utxos yet
        assertTrue(federationStorageProvider
            .getNewFederationBtcUTXOs(BRIDGE_CONSTANTS.getBtcParams(), ACTIVATIONS)
            .isEmpty());
    }

    private void assertNewAndOldFederationsHaveExpectedAddress(
          Address expectedNewFederationAddress, Address expectedOldFederationAddress) {
        // New active and retiring federation
        assertEquals(expectedNewFederationAddress, bridgeSupport.getActiveFederationAddress());
        assertEquals(expectedOldFederationAddress, bridgeSupport.getRetiringFederationAddress());
    }
    
    private void assertMigrationHasNotStarted() throws Exception {
        // Current block is behind fedActivationAge + fundsMigrationAgeBegin
        var blockNumber = BRIDGE_CONSTANTS.getFederationConstants().getFederationActivationAge(ACTIVATIONS) + 
            BRIDGE_CONSTANTS.getFederationConstants().getFundsMigrationAgeSinceActivationBegin();
        assertTrue(currentBlock.getNumber() <= blockNumber);

        // Pegouts waiting for confirmations should be empty
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
    }
     
    private void assertMigrationHasStarted() throws Exception {
        // Pegouts waiting for confirmations should not be empty
        assertEquals(1, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    private void assertMigrationHasEnded(Federation newFederation) {
        // New active federation still there, retiring federation no longer there
        assertEquals(newFederation, bridgeSupport.getActiveFederation());
        assertNull(bridgeSupport.getRetiringFederationAddress());
    }
    
    private void assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation(
          Federation originalFederation) {
        var lastRetiredFederationP2SHScriptOptional = 
            federationStorageProvider.getLastRetiredFederationP2SHScript(ACTIVATIONS);
        assertTrue(lastRetiredFederationP2SHScriptOptional.isPresent());
        Script lastRetiredFederationP2SHScript = lastRetiredFederationP2SHScriptOptional.get();

        assertNotEquals(lastRetiredFederationP2SHScript, originalFederation.getP2SHScript());
        assertEquals(lastRetiredFederationP2SHScript, getFederationDefaultP2SHScript(originalFederation));
    }

    private void assertPendingFederationIsBuiltAsExpected(PendingFederation pendingFederation) {
        assertNotNull(pendingFederation);
        assertEquals(9, pendingFederation.getSize());
        assertTrue(pendingFederation.getMembers().containsAll(NEW_FEDERATION_MEMBERS));
    }
   
    private void verifySigHashes() throws Exception {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries().stream()
            .map(Entry::getBtcTransaction)
            .toList();

        pegoutsTxs.forEach(
            pegoutTx -> assertPegoutTxSigHashesAreSaved(pegoutTx));
    }

    private void verifyPegoutTransactionCreatedEventWasEmitted() throws Exception {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries().stream()
            .map(Entry::getBtcTransaction)
            .toList();

        pegoutsTxs.forEach(this::verifyPegoutTransactionCreatedEvent);
    }
    
    private void verifyPegouts() throws Exception {
        var activeFederation = federationStorageProvider.getNewFederation(
            BRIDGE_CONSTANTS.getFederationConstants(), ACTIVATIONS);
        var retiringFederation = federationStorageProvider.getOldFederation(
            BRIDGE_CONSTANTS.getFederationConstants(), ACTIVATIONS);

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

    private void assertPegoutTxSigHashesAreSaved(BtcTransaction pegoutTx) {
        var lastPegoutSigHash = BitcoinUtils.getFirstInputSigHash(pegoutTx);
        assertTrue(lastPegoutSigHash.isPresent());
        assertTrue(bridgeStorageProvider.hasPegoutTxSigHash(lastPegoutSigHash.get()));
    }

    private void verifyPegoutTransactionCreatedEvent(BtcTransaction pegoutTx) {
        var pegoutTxHash = pegoutTx.getHash();
        var outpointValues = UtxoUtils.extractOutpointValues(pegoutTx);
        verify(bridgeEventLogger).logPegoutTransactionCreated(pegoutTxHash, outpointValues);
    }
}

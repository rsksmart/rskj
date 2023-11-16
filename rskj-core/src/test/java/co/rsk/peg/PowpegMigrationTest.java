package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.trie.Trie;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.rsk.peg.PegTestUtils.BTC_TX_LEGACY_VERSION;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PowpegMigrationTest {

    /***
     * Key is BtcTxHash and output index. Value is the address that received the funds
     * I can use this to validate that a certain redeemscript trying to spend this utxo generates the corresponding address
     */
    private final Map<Sha256Hash, Address> whoCanSpendTheseUtxos = new HashMap<>();
    private final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

    private void testChangePowpeg(
        FederationType oldPowPegFederationType,
        List<Triple<BtcECKey, ECKey, ECKey>> oldPowPegKeys,
        Address oldPowPegAddress,
        List<UTXO> existingUtxos,
        FederationType newPowPegFederationType,
        List<Triple<BtcECKey, ECKey, ECKey>> newPowPegKeys,
        Address newPowPegAddress,
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations,
        long migrationShouldFinishAfterThisAmountOfBlocks
    ) throws Exception {
        Repository repository = new MutableRepository(
            new MutableTrieCache(new MutableTrieImpl(null, new Trie()))
        );
        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations
        );

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
            bridgeConstants.getBtcParams(),
            100,
            100
        );

        BtcBlockStore btcBlockStore = btcBlockStoreFactory.newInstance(
            repository,
            bridgeConstants,
            bridgeStorageProvider,
            activations
        );

        // Setting a chain head different from genesis to avoid having to read the checkpoints file
        addNewBtcBlockOnTipOfChain(btcBlockStore, bridgeConstants);

        repository.save();

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        long blockNumber = 0;

        // Creation phase
        Block initialBlock = mock(Block.class);
        when(initialBlock.getNumber()).thenReturn(blockNumber);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(initialBlock)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .build();

        List<FederationMember> originalPowpegMembers = oldPowPegKeys.stream().map(theseKeys ->
            new FederationMember(
                theseKeys.getLeft(),
                theseKeys.getMiddle(),
                theseKeys.getRight()
            )
        ).collect(Collectors.toList());

        Federation originalPowpeg;
        switch (oldPowPegFederationType) {
            case legacyErp:
                ErpRedeemScriptBuilder erpRedeemScriptBuilder =
                    ErpRedeemScriptBuilderUtils.defineNonStandardErpRedeemScriptBuilder(activations, bridgeConstants.getBtcParams());
                originalPowpeg = new ErpFederation(
                    originalPowpegMembers,
                    Instant.now(),
                    0,
                    bridgeConstants.getBtcParams(),
                    bridgeConstants.getErpFedPubKeysList(),
                    bridgeConstants.getErpFedActivationDelay(),
                    activations,
                    erpRedeemScriptBuilder
                );
                break;
            case p2shErp:
                originalPowpeg = new ErpFederation(
                    originalPowpegMembers,
                    Instant.now(),
                    0,
                    bridgeConstants.getBtcParams(),
                    bridgeConstants.getErpFedPubKeysList(),
                    bridgeConstants.getErpFedActivationDelay(),
                    activations,
                    new P2shErpRedeemScriptBuilder()
                );
                // TODO: CHECK REDEEMSCRIPT
                break;
            default:
                throw new Exception(
                    String.format("Federation type %s is not supported", oldPowPegFederationType)
                );
        }

        assertEquals(oldPowPegAddress, originalPowpeg.getAddress());

        // Set original powpeg information
        bridgeStorageProvider.setNewFederation(originalPowpeg);
        bridgeStorageProvider.getNewFederationBtcUTXOs().addAll(existingUtxos);

        // Create Pending federation (doing this to avoid voting the pending Federation)
        List<FederationMember> newPowpegMembers = newPowPegKeys.stream().map(newPowpegKey ->
            new FederationMember(
                newPowpegKey.getLeft(),
                newPowpegKey.getMiddle(),
                newPowpegKey.getRight()
            )
        ).collect(Collectors.toList());
        PendingFederation pendingFederation = new PendingFederation(newPowpegMembers);
        // Create the Federation just to provide it to utility methods
        Federation newFederation = pendingFederation.buildFederation(
            Instant.now(),
            0,
            bridgeConstants,
            activations
        );

        // Set pending powpeg information
        bridgeStorageProvider.setPendingFederation(pendingFederation);
        bridgeStorageProvider.save();

        // Proceed with the powpeg change
        bridgeSupport.commitFederation(false, pendingFederation.getHash());

        ArgumentCaptor<Federation> argumentCaptor = ArgumentCaptor.forClass(Federation.class);
        verify(bridgeEventLogger).logCommitFederation(
            any(),
            eq(originalPowpeg),
            argumentCaptor.capture()
        );

        // TODO check this. maybe adding the FedType to the constructor solves it
        // Verify new powpeg information
        Federation newPowPeg = argumentCaptor.getValue();
        assertEquals(newPowPegAddress, newPowPeg.getAddress());
        switch (newPowPegFederationType) {
            case legacyErp:
                assertSame(ErpFederation.class, newPowPeg.getClass());
                break;
            case p2shErp:
                assertSame(ErpFederation.class, newPowPeg.getClass());
                // TODO: CHECK REDEEMSCRIPT
                break;
            default:
                throw new Exception(String.format(
                    "New powpeg is not of the expected type. Expected %s but got %s",
                    newPowPegFederationType,
                    newPowPeg.getClass()
                ));
        }

        // Verify UTXOs were moved to pending POWpeg
        List<UTXO> utxosToMigrate = bridgeStorageProvider.getOldFederationBtcUTXOs();
        for (UTXO utxo : existingUtxos) {
            assertTrue(utxosToMigrate.stream().anyMatch(storedUtxo -> storedUtxo.equals(utxo)));
        }
        assertTrue(bridgeStorageProvider.getNewFederationBtcUTXOs().isEmpty());

        // Trying to create a new powpeg again should fail
        // -2 corresponds to a new powpeg was elected and the Bridge is waiting for this new powpeg to activate
        attemptToCreateNewFederation(bridgeSupport, bridgeConstants, -2);

        // No change in active powpeg
        assertEquals(oldPowPegAddress, bridgeSupport.getFederationAddress());
        assertNull(bridgeSupport.getRetiringFederationAddress());

        // Update collections should not trigger migration
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
        Transaction updateCollectionsTx = Transaction.builder().nonce(new BtcECKey().getPrivKey()).build();
        bridgeSupport.updateCollections(updateCollectionsTx);
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());

        // peg-in after committing new fed
        testPegins(
            bridgeSupport,
            bridgeConstants,
            bridgeStorageProvider,
            btcBlockStore,
            oldPowPegAddress,
            newPowPegAddress,
            true,
            false
        );

        testFlyoverPegins(
            bridgeSupport,
            bridgeConstants,
            bridgeStorageProvider,
            btcBlockStore,
            originalPowpeg,
            newFederation,
            true,
            false
        );

        // peg-out after committing new fed
        assertTrue(bridgeStorageProvider.getReleaseRequestQueue().getEntries().isEmpty());

        testPegouts(
            bridgeSupport,
            bridgeStorageProvider,
            bridgeConstants,
            activations,
            blockNumber,
            repository,
            bridgeEventLogger,
            btcBlockStoreFactory,
            oldPowPegAddress
        );

        /*
          Activation phase
         */
        // Move the required blocks ahead for the new powpeg to become active
        // (overriding block number to ensure we don't move beyond the activation phase)
        blockNumber = initialBlock.getNumber() + bridgeConstants.getFederationActivationAge(activations);
        Block activationBlock = mock(Block.class);
        doReturn(blockNumber).when(activationBlock).getNumber();

        // assuming fed activation age after rskip383 is greater than legacy fed activation age, we can check that new fed
        // should not be active at the legacy activation age when RSKIP383 is active
        if (activations.isActive(ConsensusRule.RSKIP383)){
            ActivationConfig.ForBlock activationsBeforeRSKIP383 = mock(ActivationConfig.ForBlock.class);
            when(activationsBeforeRSKIP383.isActive(ConsensusRule.RSKIP383)).thenReturn(false);

            long legacyFedActivationBlockNumber = initialBlock.getNumber() + bridgeConstants.getFederationActivationAge(activationsBeforeRSKIP383);
            Assertions.assertTrue(blockNumber > legacyFedActivationBlockNumber);

            Block legacyFedActivationBlock = mock(Block.class);
            doReturn(legacyFedActivationBlockNumber).when(legacyFedActivationBlock).getNumber();

            bridgeSupport = new BridgeSupportBuilder()
                .withProvider(bridgeStorageProvider)
                .withRepository(repository)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(legacyFedActivationBlock)
                .withActivations(activations)
                .withBridgeConstants(bridgeConstants)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withPeginInstructionsProvider(new PeginInstructionsProvider())
                .build();

            assertEquals(oldPowPegAddress, bridgeSupport.getFederationAddress());
            assertNull(bridgeSupport.getRetiringFederation());
        }

        bridgeSupport = new BridgeSupportBuilder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(activationBlock)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .build();

        // New active powpeg and retiring powpeg
        assertEquals(newPowPegAddress, bridgeSupport.getFederationAddress());
        assertEquals(oldPowPegAddress, bridgeSupport.getRetiringFederationAddress());

        if (bridgeConstants.getFundsMigrationAgeSinceActivationBegin() > 0) {
            // No migration yet
            assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
            updateCollectionsTx = Transaction.builder().nonce(new BtcECKey().getPrivKey()).build();
            bridgeSupport.updateCollections(updateCollectionsTx);
            assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
        }

        // Trying to create a new powpeg again should fail
        // -3 corresponds to a new powpeg was elected and the Bridge is waiting for this new powpeg to migrate
        attemptToCreateNewFederation(bridgeSupport, bridgeConstants, -3);

        // peg-in after new fed activates
        testPegins(
            bridgeSupport,
            bridgeConstants,
            bridgeStorageProvider,
            btcBlockStore,
            oldPowPegAddress,
            newPowPegAddress,
            true,
            true
        );

        testFlyoverPegins(
            bridgeSupport,
            bridgeConstants,
            bridgeStorageProvider,
            btcBlockStore,
            originalPowpeg,
            newFederation,
            true,
            true
        );

        /*
         Migration phase
         */

        // Move the required blocks ahead for the new powpeg to start migrating
        blockNumber = blockNumber + bridgeConstants.getFundsMigrationAgeSinceActivationBegin() + 1;
        Block migrationBlock = mock(Block.class);
        // Adding 1 as the migration is exclusive
        doReturn(blockNumber).when(migrationBlock).getNumber();

        bridgeSupport = new BridgeSupportBuilder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(migrationBlock)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .build();

        // New active powpeg and retiring powpeg
        assertEquals(newPowPegAddress, bridgeSupport.getFederationAddress());
        assertEquals(oldPowPegAddress, bridgeSupport.getRetiringFederationAddress());

        // Trying to create a new powpeg again should fail
        // -3 corresponds to a new powpeg was elected and the Bridge is waiting for this new powpeg to migrate
        attemptToCreateNewFederation(bridgeSupport, bridgeConstants, -3);

        // Migration should start !
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());

        // This might not be true if the transaction exceeds the max bitcoin transaction size!!!
        int expectedMigrations = activations.isActive(ConsensusRule.RSKIP294) ?
            (int) Math.ceil((double) utxosToMigrate.size() / bridgeConstants.getMaxInputsPerPegoutTransaction())
            : 1;

        // Migrate while there are still utxos to migrate
        while (!bridgeStorageProvider.getOldFederationBtcUTXOs().isEmpty()) {
            updateCollectionsTx = Transaction.builder().nonce(new BtcECKey().getPrivKey()).build();
            bridgeSupport.updateCollections(updateCollectionsTx);
        }

        assertEquals(
            expectedMigrations,
            bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size()
        );

        for (PegoutsWaitingForConfirmations.Entry entry : bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries()) {
            BtcTransaction pegout = entry.getBtcTransaction();
            // This would fail if we were to implement UTXO expansion at some point
            assertEquals(1, pegout.getOutputs().size());
            assertEquals(
                newPowPegAddress,
                pegout.getOutput(0).getAddressFromP2SH(bridgeConstants.getBtcParams())
            );
            for (TransactionInput input : pegout.getInputs()) {
                int chunksSize = input.getScriptSig().getChunks().size();
                Script redeemScript = new Script(input.getScriptSig().getChunks().get(chunksSize - 1).data);
                Address spendingAddress = getAddressFromRedeemScript(bridgeConstants, redeemScript);
                assertEquals(whoCanSpendTheseUtxos.get(input.getOutpoint().getHash()), spendingAddress);
            }
            if(activations.isActive(ConsensusRule.RSKIP376)){
                assertEquals(BTC_TX_VERSION_2, pegout.getVersion());
            } else {
                assertEquals(BTC_TX_LEGACY_VERSION, pegout.getVersion());
            }
        }

        verifyPegouts(bridgeStorageProvider);

        // peg-in during migration
        testPegins(
            bridgeSupport,
            bridgeConstants,
            bridgeStorageProvider,
            btcBlockStore,
            oldPowPegAddress,
            newPowPegAddress,
            true,
            true
        );

        testFlyoverPegins(
            bridgeSupport,
            bridgeConstants,
            bridgeStorageProvider,
            btcBlockStore,
            originalPowpeg,
            newFederation,
            true,
            true
        );

        // Should be migrated
        int newlyAddedUtxos = activations.isActive(ConsensusRule.RSKIP294) ?
            (int) Math.ceil((double) bridgeStorageProvider.getOldFederationBtcUTXOs().size() / bridgeConstants.getMaxInputsPerPegoutTransaction()) :
            1;

        // Migrate while there are still utxos to migrate
        while (!bridgeStorageProvider.getOldFederationBtcUTXOs().isEmpty()) {
            updateCollectionsTx = Transaction.builder().nonce(new BtcECKey().getPrivKey()).build();
            bridgeSupport.updateCollections(updateCollectionsTx);
        }

        assertEquals(
            expectedMigrations + newlyAddedUtxos,
            bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size()
        );

        verifyPegouts(bridgeStorageProvider);

        // peg-out during migration
        assertTrue(bridgeStorageProvider.getReleaseRequestQueue().getEntries().isEmpty());

        testPegouts(
            bridgeSupport,
            bridgeStorageProvider,
            bridgeConstants,
            activations,
            blockNumber,
            repository,
            bridgeEventLogger,
            btcBlockStoreFactory,
            newPowPegAddress
        );

        /*
          After Migration phase
         */

        // Move the height to the block previous to the migration finishing, it should keep on migrating
        blockNumber = blockNumber + bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations) - 2;
        Block migrationFinishingBlock = mock(Block.class);
        // Substracting 2 as the previous height was activation + 1 and migration is exclusive
        doReturn(blockNumber).when(migrationFinishingBlock).getNumber();
        assertEquals(
            migrationShouldFinishAfterThisAmountOfBlocks,
            bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations)
        );

        bridgeSupport = new BridgeSupportBuilder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(migrationFinishingBlock)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .build();

        // New active powpeg and retiring powpeg is still there
        assertEquals(newPowPegAddress, bridgeSupport.getFederationAddress());
        assertEquals(oldPowPegAddress, bridgeSupport.getRetiringFederationAddress());

        // Last update collections before the migration finishes
        updateCollectionsTx = Transaction.builder().nonce(new BtcECKey().getPrivKey()).build();
        bridgeSupport.updateCollections(updateCollectionsTx);

        // New active powpeg and retiring powpeg is still there
        assertEquals(newPowPegAddress, bridgeSupport.getFederationAddress());
        assertEquals(oldPowPegAddress, bridgeSupport.getRetiringFederationAddress());

        // Move the height to the block after the migration finishes
        blockNumber = blockNumber + 3;
        Block migrationFinishedBlock = mock(Block.class);
        doReturn(blockNumber).when(migrationFinishedBlock).getNumber();
        assertEquals(
            migrationShouldFinishAfterThisAmountOfBlocks,
            bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations)
        );

        bridgeSupport = new BridgeSupportBuilder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(migrationFinishedBlock)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .build();

        // New active powpeg and retiring powpeg is still there
        assertEquals(newPowPegAddress, bridgeSupport.getFederationAddress());
        assertEquals(oldPowPegAddress, bridgeSupport.getRetiringFederationAddress());

        // The first update collections after the migration finished should get rid of the retiring powpeg
        updateCollectionsTx = Transaction.builder().nonce(new BtcECKey().getPrivKey()).build();
        bridgeSupport.updateCollections(updateCollectionsTx);

        // New active powpeg still there, retiring powpeg no longer there
        assertEquals(newPowPegAddress, bridgeSupport.getFederationAddress());
        assertNull(bridgeSupport.getRetiringFederationAddress());

        // peg-in after migration
        testPegins(
            bridgeSupport,
            bridgeConstants,
            bridgeStorageProvider,
            btcBlockStore,
            oldPowPegAddress,
            newPowPegAddress,
            false,
            true
        );

        testFlyoverPegins(
            bridgeSupport,
            bridgeConstants,
            bridgeStorageProvider,
            btcBlockStore,
            originalPowpeg,
            newFederation,
            false,
            true
        );

        Optional<Script> lastRetiredFederationP2SHScriptOptional = bridgeStorageProvider.getLastRetiredFederationP2SHScript();
        assertTrue(lastRetiredFederationP2SHScriptOptional.isPresent());
        Script lastRetiredFederationP2SHScript = lastRetiredFederationP2SHScriptOptional.get();

        if (activations.isActive(ConsensusRule.RSKIP377)){
            if (oldPowPegFederationType == FederationType.legacyErp || oldPowPegFederationType == FederationType.p2shErp){
                assertNotEquals(lastRetiredFederationP2SHScript, originalPowpeg.getP2SHScript());
            }
            assertEquals(lastRetiredFederationP2SHScript, originalPowpeg instanceof ErpFederation ? ((ErpFederation) originalPowpeg).getStandardP2SHScript() : originalPowpeg.getP2SHScript());
        } else {
            if (oldPowPegFederationType == FederationType.legacyErp || oldPowPegFederationType == FederationType.p2shErp){
                assertEquals(lastRetiredFederationP2SHScript, originalPowpeg.getP2SHScript());
                assertNotEquals(lastRetiredFederationP2SHScript, originalPowpeg instanceof ErpFederation ? ((ErpFederation) originalPowpeg).getStandardP2SHScript() : originalPowpeg.getP2SHScript());
            } else {
                assertEquals(lastRetiredFederationP2SHScript, originalPowpeg.getP2SHScript());
                assertEquals(lastRetiredFederationP2SHScript, originalPowpeg instanceof ErpFederation ? ((ErpFederation) originalPowpeg).getStandardP2SHScript() : originalPowpeg.getP2SHScript());
            }
        }
    }

    private void verifyPegouts(BridgeStorageProvider bridgeStorageProvider) throws IOException {
        Federation activeFederation = bridgeStorageProvider.getNewFederation();
        Federation retiringFederation = bridgeStorageProvider.getOldFederation();

        for (PegoutsWaitingForConfirmations.Entry pegoutEntry : bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries()) {
            BtcTransaction pegoutBtcTransaction = pegoutEntry.getBtcTransaction();
            for (TransactionInput input : pegoutBtcTransaction.getInputs()) {
                // Each input should contain the right scriptsig
                List<ScriptChunk> inputScriptChunks = input.getScriptSig().getChunks();
                Script inputRedeemScript = new Script(inputScriptChunks.get(inputScriptChunks.size() - 1).data);

                // Get the standard redeem script to compare against, since it could be a flyover redeem script
                ScriptParserResult result = ScriptParser.parseScriptProgram(inputRedeemScript.getProgram());
                assertFalse(result.getException().isPresent());
                Script inputStandardRedeemScript = RedeemScriptParserFactory.get(result.getChunks()).extractStandardRedeemScript();

                Optional<Federation> spendingFederationOptional = Optional.empty();
                if (inputStandardRedeemScript.equals(activeFederation instanceof ErpFederation ? ((ErpFederation) activeFederation).getStandardRedeemScript() : activeFederation.getRedeemScript())) {
                    spendingFederationOptional = Optional.of(activeFederation);
                } else if (retiringFederation != null &&
                    inputStandardRedeemScript.equals(retiringFederation instanceof ErpFederation ? ((ErpFederation) retiringFederation).getStandardRedeemScript() : retiringFederation.getRedeemScript()) ) {
                    spendingFederationOptional = Optional.of(retiringFederation);
                } else {
                    fail("pegout scriptsig does not match any Federation");
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
                // Won't compare the redeemscript as I've already compared it above
            }
        }
    }

    private void testPegouts(
        BridgeSupport bridgeSupport,
        BridgeStorageProvider bridgeStorageProvider,
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations,
        long blockNumber,
        Repository repository,
        BridgeEventLogger bridgeEventLogger,
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory,
        Address powpegAddress
    ) throws IOException {
        BtcECKey pegoutRecipientKey = new BtcECKey();
        ECKey pegoutSigner = ECKey.fromPrivate(pegoutRecipientKey.getPrivKeyBytes());
        Address pegoutRecipientAddress = pegoutRecipientKey.toAddress(bridgeConstants.getBtcParams());
        co.rsk.core.Coin peggedOutAmount = co.rsk.core.Coin.fromBitcoin(Coin.COIN);

        int existingPegouts = bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size();

        Transaction pegoutTx = Transaction.builder()
            .chainId((byte) 1)
            .destination(PrecompiledContracts.BRIDGE_ADDR)
            .value(peggedOutAmount)
            .nonce(new BtcECKey().getPrivKey()) // Using a private key to generate randomness through the nonce
            .build();

        pegoutTx.sign(pegoutSigner.getPrivKeyBytes());

        bridgeSupport.releaseBtc(pegoutTx);

        assertTrue(bridgeStorageProvider.getReleaseRequestQueue().getEntries().stream().anyMatch(request ->
                request.getDestination().equals(pegoutRecipientAddress) &&
                    request.getAmount().equals(peggedOutAmount.toBitcoin())
            )
        );

        if (activations.isActive(ConsensusRule.RSKIP271)) {
            // Peg-out batching is enabled need to move the height to the next pegout event
            blockNumber = bridgeSupport.getNextPegoutCreationBlockNumber();
            Block nextPegoutEventBlock = mock(Block.class);
            // Adding 1 as the migration is exclusive
            doReturn(blockNumber).when(nextPegoutEventBlock).getNumber();

            bridgeSupport = new BridgeSupportBuilder()
                .withProvider(bridgeStorageProvider)
                .withRepository(repository)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(nextPegoutEventBlock)
                .withActivations(activations)
                .withBridgeConstants(bridgeConstants)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withPeginInstructionsProvider(new PeginInstructionsProvider())
                .build();
        }

        Transaction pegoutCreationTx = Transaction.builder().nonce(new BtcECKey().getPrivKey()).build();
        bridgeSupport.updateCollections(pegoutCreationTx);

        // Verify there is one more pegout request
        assertEquals(
            existingPegouts + 1,
            bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().size()
        );

        // The last pegout is the one just created
        PegoutsWaitingForConfirmations.Entry lastPegout = null;
        Iterator<PegoutsWaitingForConfirmations.Entry> collectionItr = bridgeStorageProvider
            .getPegoutsWaitingForConfirmations()
            .getEntries()
            .stream()
            .iterator();
        while (collectionItr.hasNext()) {
            lastPegout = collectionItr.next();
        }

        if (lastPegout == null || lastPegout.getBtcTransaction() == null) {
            fail("Couldn't find the recently created pegout in the release transaction set");
        }

        // Verify the recipients are the expected ones
        for (TransactionOutput output : lastPegout.getBtcTransaction().getOutputs()) {
            switch (output.getScriptPubKey().getScriptType()) {
                case P2PKH: // Output for the pegout receiver
                    assertEquals(
                        pegoutRecipientAddress,
                        output.getAddressFromP2PKHScript(bridgeConstants.getBtcParams())
                    );
                    break;
                case P2SH: // Change output for the federation
                    assertEquals(
                        powpegAddress,
                        output.getAddressFromP2SH(bridgeConstants.getBtcParams())
                    );
                    break;
                default:
                    fail("Unexpected script type");
            }
        }

        verifyPegouts(bridgeStorageProvider);

        // Confirm the peg-outs
        blockNumber = blockNumber + bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations();
        Block confirmedPegoutBlock = mock(Block.class);
        // Adding 1 as the migration is exclusive
        doReturn(blockNumber).when(confirmedPegoutBlock).getNumber();

        bridgeSupport = new BridgeSupportBuilder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(confirmedPegoutBlock)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .build();

        int confirmedPegouts = bridgeStorageProvider.getPegoutsWaitingForSignatures().size();

        // Confirm all existing pegouts
        for (int i = 0; i < existingPegouts + 1; i++) {
            // Adding a random nonce ensure the rskTxHash is different each time
            bridgeSupport.updateCollections(Transaction.builder().nonce(new BtcECKey().getPrivKey()).build());
        }
        bridgeSupport.save();

        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
        assertEquals(
            confirmedPegouts + existingPegouts + 1,
            bridgeStorageProvider.getPegoutsWaitingForSignatures().size()
        );
    }

    private void testPegins(
        BridgeSupport bridgeSupport,
        BridgeConstants bridgeConstants,
        BridgeStorageProvider bridgeStorageProvider,
        BtcBlockStore btcBlockStore,
        Address oldPowPegAddress,
        Address newPowPegAddress,
        boolean shouldPeginToOldPowpegWork,
        boolean shouldPeginToNewPowpegWork
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {

        // Perform peg-in to the old powpeg address
        BtcTransaction peginToRetiringPowPeg = createPegin(
            bridgeSupport,
            bridgeConstants,
            btcBlockStore,
            Collections.singletonList(new TransactionOutput(
                bridgeConstants.getBtcParams(),
                null,
                Coin.COIN,
                oldPowPegAddress
            )),
            true,
            true,
            true
        );
        Sha256Hash peginToRetiringPowPegHash = peginToRetiringPowPeg.getHash();
        boolean isPeginToRetiringPowPegRegistered = bridgeStorageProvider.getOldFederationBtcUTXOs()
            .stream()
            .anyMatch(utxo -> utxo.getHash().equals(peginToRetiringPowPegHash));

        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(peginToRetiringPowPegHash));
        assertEquals(shouldPeginToOldPowpegWork, isPeginToRetiringPowPegRegistered);
        assertFalse(bridgeStorageProvider.getNewFederationBtcUTXOs().stream().anyMatch(utxo ->
            utxo.getHash().equals(peginToRetiringPowPegHash))
        );

        if (!oldPowPegAddress.equals(newPowPegAddress)) {
            // Perform peg-in to the future powpeg - should be ignored
            BtcTransaction peginToFuturePowPeg = createPegin(
                bridgeSupport,
                bridgeConstants,
                btcBlockStore,
                Collections.singletonList(new TransactionOutput(
                    bridgeConstants.getBtcParams(),
                    null,
                    Coin.COIN,
                    newPowPegAddress
                )),
                true,
                true,
                true
            );
            Sha256Hash peginToFuturePowPegHash = peginToFuturePowPeg.getHash();
            boolean isPeginToNewPowPegRegistered = bridgeStorageProvider.getNewFederationBtcUTXOs()
                .stream()
                .anyMatch(utxo -> utxo.getHash().equals(peginToFuturePowPegHash));

            // This assertion should change when we change peg-in verification
            assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(peginToFuturePowPegHash));
            assertFalse(bridgeStorageProvider.getOldFederationBtcUTXOs().stream().anyMatch(utxo ->
                utxo.getHash().equals(peginToFuturePowPegHash))
            );
            assertEquals(shouldPeginToNewPowpegWork, isPeginToNewPowPegRegistered);
        }
    }

    private void attemptToCreateNewFederation(
        BridgeSupport bridgeSupport,
        BridgeConstants bridgeConstants,
        int expectedResult) throws BridgeIllegalArgumentException {

        ABICallSpec createSpec = new ABICallSpec("create", new byte[][]{});
        Transaction voteTx = mock(Transaction.class);
        RskAddress federationChangeAuthorizer = new RskAddress(
            bridgeConstants.getFederationChangeAuthorizer().authorizedAddresses.get(0)
        );
        when(voteTx.getSender(any())).thenReturn(federationChangeAuthorizer);
        int federationChangeResult = bridgeSupport.voteFederationChange(voteTx, createSpec);

        assertEquals(expectedResult, federationChangeResult);
    }

    private Address getAddressFromRedeemScript(BridgeConstants bridgeConstants, Script redeemScript) {
        return Address.fromP2SHHash(
            bridgeConstants.getBtcParams(),
            ScriptBuilder.createP2SHOutputScript(redeemScript).getPubKeyHash()
        );
    }

    private void testFlyoverPegins(
        BridgeSupport bridgeSupport,
        BridgeConstants bridgeConstants,
        BridgeStorageProvider bridgeStorageProvider,
        BtcBlockStore btcBlockStore,
        Federation recipientOldFederation,
        Federation recipientNewFederation,
        boolean shouldPeginToOldPowpegWork,
        boolean shouldPeginToNewPowpegWork
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Pair<BtcTransaction, Keccak256> flyoverPeginToRetiringPowpeg = createFlyoverPegin(
            bridgeSupport,
            bridgeConstants,
            btcBlockStore,
            recipientOldFederation,
            true,
            true,
            true
        );

        assertEquals(
            shouldPeginToOldPowpegWork,
            bridgeStorageProvider.isFlyoverDerivationHashUsed(
                flyoverPeginToRetiringPowpeg.getLeft().getHash(),
                flyoverPeginToRetiringPowpeg.getRight()
            )
        );
        assertEquals(
            shouldPeginToOldPowpegWork,
            bridgeStorageProvider.getOldFederationBtcUTXOs().stream().anyMatch(utxo ->
                utxo.getHash().equals(flyoverPeginToRetiringPowpeg.getLeft().getHash())
            )
        );
        assertFalse(bridgeStorageProvider.getNewFederationBtcUTXOs().stream().anyMatch(utxo ->
            utxo.getHash().equals(flyoverPeginToRetiringPowpeg.getLeft().getHash())
        ));

        Pair<BtcTransaction, Keccak256> flyoverPeginToNewPowpeg = createFlyoverPegin(
            bridgeSupport,
            bridgeConstants,
            btcBlockStore,
            recipientNewFederation,
            true,
            true,
            true
        );

        assertEquals(
            shouldPeginToNewPowpegWork,
            bridgeStorageProvider.isFlyoverDerivationHashUsed(
                flyoverPeginToNewPowpeg.getLeft().getHash(),
                flyoverPeginToNewPowpeg.getRight()
            )
        );
        assertFalse(bridgeStorageProvider.getOldFederationBtcUTXOs().stream().anyMatch(utxo ->
            utxo.getHash().equals(flyoverPeginToNewPowpeg.getLeft().getHash())
        ));
        assertEquals(
            shouldPeginToNewPowpegWork,
            bridgeStorageProvider.getNewFederationBtcUTXOs().stream().anyMatch(utxo ->
                utxo.getHash().equals(flyoverPeginToNewPowpeg.getLeft().getHash())
            )
        );
    }

    private Pair<BtcTransaction, Keccak256> createFlyoverPegin(
        BridgeSupport bridgeSupport,
        BridgeConstants bridgeConstants,
        BtcBlockStore blockStore,
        Federation recipientFederation,
        boolean shouldExistInBlockStore,
        boolean shouldBeConfirmed,
        boolean shouldHaveValidPmt
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        InternalTransaction flyoverPeginTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null,
            null
        );

        BtcTransaction peginBtcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        // Randomize the input to avoid repeating same Btc tx hash
        peginBtcTx.addInput(
            PegTestUtils.createHash(blockStore.getChainHead().getHeight() + 1),
            0,
            new Script(new byte[]{})
        );

        // The derivation arguments will be randomly calculated
        // The serialization and hashing was extracted from https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP176.md#bridge
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(blockStore.getChainHead().getHeight() + 1);
        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address liquidityProviderBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        byte[] infoToHash = new byte[94];
        int pos = 0;

        // Derivation hash
        byte[] derivationArgumentsHashSerialized = derivationArgumentsHash.getBytes();
        System.arraycopy(
            derivationArgumentsHashSerialized,
            0,
            infoToHash,
            pos,
            derivationArgumentsHashSerialized.length
        );
        pos += derivationArgumentsHashSerialized.length;

        // User BTC refund address version
        byte[] userRefundBtcAddressVersionSerialized = userRefundBtcAddress.getVersion() != 0 ?
            ByteUtil.intToBytesNoLeadZeroes(userRefundBtcAddress.getVersion()) :
            new byte[]{0};
        System.arraycopy(
            userRefundBtcAddressVersionSerialized,
            0,
            infoToHash,
            pos,
            userRefundBtcAddressVersionSerialized.length
        );
        pos += userRefundBtcAddressVersionSerialized.length;

        // User BTC refund address
        byte[] userRefundBtcAddressHash160 = userRefundBtcAddress.getHash160();
        System.arraycopy(
            userRefundBtcAddressHash160,
            0,
            infoToHash,
            pos,
            userRefundBtcAddressHash160.length
        );
        pos += userRefundBtcAddressHash160.length;

        // LBC address
        byte[] lbcAddressSerialized = lbcAddress.getBytes();
        System.arraycopy(
            lbcAddressSerialized,
            0,
            infoToHash,
            pos,
            lbcAddressSerialized.length
        );
        pos += lbcAddressSerialized.length;

        // Liquidity provider BTC address version
        byte[] liquidityProviderBtcAddressVersionSerialized = liquidityProviderBtcAddress.getVersion() != 0 ?
            ByteUtil.intToBytesNoLeadZeroes(liquidityProviderBtcAddress.getVersion()) :
            new byte[]{0};
        System.arraycopy(
            liquidityProviderBtcAddressVersionSerialized,
            0,
            infoToHash,
            pos,
            liquidityProviderBtcAddressVersionSerialized.length
        );
        pos += liquidityProviderBtcAddressVersionSerialized.length;

        // Liquidity provider BTC address
        byte[] liquidityProviderBtcAddressHash160 = liquidityProviderBtcAddress.getHash160();
        System.arraycopy(
            liquidityProviderBtcAddressHash160,
            0,
            infoToHash,
            pos,
            liquidityProviderBtcAddressHash160.length
        );
        Keccak256 derivationHash = new Keccak256(HashUtil.keccak256(infoToHash));

        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            recipientFederation.getRedeemScript(),
            Sha256Hash.wrap(derivationHash.toHexString())
            // Parsing to Sha256Hash in order to use helper. Does not change functionality
        );

        Address recipient = getAddressFromRedeemScript(bridgeConstants, flyoverRedeemScript);
        peginBtcTx.addOutput(
            Coin.COIN,
            recipient
        );
        whoCanSpendTheseUtxos.put(peginBtcTx.getHash(), recipient);

        int height = 0;
        if (shouldExistInBlockStore) {
            StoredBlock chainHead = blockStore.getChainHead();
            BtcBlock btcBlock = new BtcBlock(
                bridgeConstants.getBtcParams(),
                1,
                chainHead.getHeader().getHash(),
                peginBtcTx.getHash(),
                0,
                0,
                0,
                Collections.singletonList(peginBtcTx)
            );
            height = chainHead.getHeight() + 1;
            StoredBlock storedBlock = new StoredBlock(btcBlock, BigInteger.ZERO, height);
            blockStore.put(storedBlock);
            blockStore.setChainHead(storedBlock);
        }

        if (shouldBeConfirmed) {
            int requiredConfirmations = bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
            for (int i = 0; i < requiredConfirmations; i++) {
                addNewBtcBlockOnTipOfChain(blockStore, bridgeConstants);
            }
        }

        Sha256Hash hashForPmt = shouldHaveValidPmt ? peginBtcTx.getHash() : Sha256Hash.ZERO_HASH;
        PartialMerkleTree pmt = new PartialMerkleTree(
            bridgeConstants.getBtcParams(),
            new byte[]{1},
            Collections.singletonList(hashForPmt),
            1
        );

        bridgeSupport.registerFlyoverBtcTransaction(
            flyoverPeginTx,
            peginBtcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            liquidityProviderBtcAddress,
            true
        );

        bridgeSupport.save();

        return Pair.of(peginBtcTx, derivationHash);
    }

    private BtcTransaction createPegin(
        BridgeSupport bridgeSupport,
        BridgeConstants bridgeConstants,
        BtcBlockStore blockStore,
        List<TransactionOutput> outputs,
        boolean shouldExistInBlockStore,
        boolean shouldBeConfirmed,
        boolean shouldHaveValidPmt
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {

        Transaction peginRegistrationTx = mock(Transaction.class);

        BtcTransaction peginBtcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        // Randomize the input to avoid repeating same Btc tx hash
        peginBtcTx.addInput(
            PegTestUtils.createHash(blockStore.getChainHead().getHeight() + 1),
            0,
            new Script(new byte[]{})
        );
        outputs.forEach(peginBtcTx::addOutput);
        // Adding OP_RETURN output to identify this peg-in as v1 and avoid sender identification
        peginBtcTx.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRsk(
                1,
                PrecompiledContracts.BRIDGE_ADDR,
                Optional.empty()
            )
        );

        outputs.forEach(output -> whoCanSpendTheseUtxos.put(
            peginBtcTx.getHash(),
            output.getAddressFromP2SH(bridgeConstants.getBtcParams())
        ));

        int height = 0;
        if (shouldExistInBlockStore) {
            StoredBlock chainHead = blockStore.getChainHead();
            BtcBlock btcBlock = new BtcBlock(
                bridgeConstants.getBtcParams(),
                1,
                chainHead.getHeader().getHash(),
                peginBtcTx.getHash(),
                0,
                0,
                0,
                Collections.singletonList(peginBtcTx)
            );
            height = chainHead.getHeight() + 1;
            StoredBlock storedBlock = new StoredBlock(btcBlock, BigInteger.ZERO, height);
            blockStore.put(storedBlock);
            blockStore.setChainHead(storedBlock);
        }

        if (shouldBeConfirmed) {
            int requiredConfirmations = bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
            for (int i = 0; i < requiredConfirmations; i++) {
                addNewBtcBlockOnTipOfChain(blockStore, bridgeConstants);
            }
        }

        Sha256Hash hashForPmt = shouldHaveValidPmt ? peginBtcTx.getHash() : Sha256Hash.ZERO_HASH;
        PartialMerkleTree pmt = new PartialMerkleTree(
            bridgeConstants.getBtcParams(),
            new byte[]{1},
            Collections.singletonList(hashForPmt),
            1
        );

        bridgeSupport.registerBtcTransaction(
            peginRegistrationTx,
            peginBtcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        bridgeSupport.save();

        return peginBtcTx;
    }

    private void addNewBtcBlockOnTipOfChain(BtcBlockStore blockStore, BridgeConstants bridgeConstants) throws BlockStoreException {
        StoredBlock chainHead = blockStore.getChainHead();
        BtcBlock btcBlock = new BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            chainHead.getHeader().getHash(),
            PegTestUtils.createHash(chainHead.getHeight() + 1),
            0,
            0,
            0,
            Collections.emptyList()
        );
        StoredBlock storedBlock = new StoredBlock(
            btcBlock,
            BigInteger.ZERO,
            chainHead.getHeight() + 1
        );
        blockStore.put(storedBlock);
        blockStore.setChainHead(storedBlock);
    }

    private Address getMainnetPowpegAddress() {
        return Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "3DsneJha6CY6X9gU2M9uEc4nSdbYECB4Gh"
        );
    }

    private List<Triple<BtcECKey, ECKey, ECKey>> getMainnetPowpegKeys() {
        List<Triple<BtcECKey, ECKey, ECKey>> keys = new ArrayList<>();

        keys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
            ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
            ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ));
        keys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
            ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
            ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ));
        keys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
            ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
            ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ));
        keys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("026b472f7d59d201ff1f540f111b6eb329e071c30a9d23e3d2bcd128fe73dc254c")),
            ECKey.fromPublicOnly(Hex.decode("0353dda9ae319eab0d3e1235896d58bd9840eadcf76c84244a5d7f60b1c66e45ce")),
            ECKey.fromPublicOnly(Hex.decode("030165892c353cd3752143b5b6c55372528e7279259fe1088d6f4dc957e146e557"))
        ));
        keys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93")),
            ECKey.fromPublicOnly(Hex.decode("03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93")),
            ECKey.fromPublicOnly(Hex.decode("03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93"))
        ));
        keys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("0357f7ed4c118e581f49cd3b4d9dd1edb4295f4def49d6dcf2faaaaac87a1a0a42")),
            ECKey.fromPublicOnly(Hex.decode("03ff13a966f1e53af37ad1fa3681b1352238f4885c1d4159730f3503bb52d63b20")),
            ECKey.fromPublicOnly(Hex.decode("03ff13a966f1e53af37ad1fa3681b1352238f4885c1d4159730f3503bb52d63b20"))
        ));
        keys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("03ae72827d25030818c4947a800187b1fbcc33ae751e248ae60094cc989fb880f6")),
            ECKey.fromPublicOnly(Hex.decode("03d7ff9b1de5cc746a93036b36f8d832ac1bfc64099f8aa37612745770d7fc4961")),
            ECKey.fromPublicOnly(Hex.decode("0300754b9dc92f27cd6702f06c460607a43c16de4531bfdc569bcdecdb12c54ccf"))
        ));
        keys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("03e05bf6002b62651378b1954820539c36ca405cbb778c225395dd9ebff6780299")),
            ECKey.fromPublicOnly(Hex.decode("03095aba7a4f1fa0f98728e5230823d603abe517bdfeeb928861a73c4b9404aaf1")),
            ECKey.fromPublicOnly(Hex.decode("02b3e34f0898759a2b5e6acd88281638d41c8da04e1fba13b5b9c3c4bf42bea3b0"))
        ));
        keys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("03ecd8af1e93c57a1b8c7f917bd9980af798adeb0205e9687865673353eb041e8d")),
            ECKey.fromPublicOnly(Hex.decode("03f4d76ec9a7a2722c0b06f5f4a489152244c8801e5ff2a43df7fefd75ce8e068f")),
            ECKey.fromPublicOnly(Hex.decode("02a935a8d59b92f9df82265cb983a76cca0308f82e9dc9dd92ff8887e2667d2a38"))
        ));

        return keys;
    }

    private int getRandomInt(int min, int max) {
        return TestUtils.generateInt(PowpegMigrationTest.class.toString() + min, max - min + 1) + min;
    }

    private List<UTXO> createRandomUtxos(Address owner) {
        Script outputScript = ScriptBuilder.createOutputScript(owner);
        List<UTXO> result = new ArrayList<>();

        int howMany = getRandomInt(100, 1000);
        for (int i = 1; i <= howMany; i++) {
            Coin randomValue = Coin.valueOf(getRandomInt(10_000, 1_000_000_000));
            Sha256Hash utxoHash = PegTestUtils.createHash(i);
            result.add(new UTXO(utxoHash, 0, randomValue, 0, false, outputScript));
            whoCanSpendTheseUtxos.put(utxoHash, owner);
        }

        return result;
    }

    @Test
    void test_change_powpeg_from_erpFederation_with_mainnet_powpeg_pre_RSKIP_353_creates_erpFederation() throws Exception {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0);

        Address originalPowpegAddress = getMainnetPowpegAddress();
        List<UTXO> utxos = createRandomUtxos(originalPowpegAddress);

        List<Triple<BtcECKey, ECKey, ECKey>> newPowpegKeys = new ArrayList<>();
        newPowpegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
            ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
            ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ));
        newPowpegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
            ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
            ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ));
        newPowpegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
            ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
            ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ));

        Address newPowpegAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "3Lqn662zEgbPU4nRYowUo9UY7HNRkbBNgN"
        );

        testChangePowpeg(
            FederationType.legacyErp,
            getMainnetPowpegKeys(),
            originalPowpegAddress,
            utxos,
            FederationType.legacyErp,
            newPowpegKeys,
            newPowpegAddress,
            bridgeConstants,
            activations,
            bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations)
        );
    }

    @Test
    void test_change_powpeg_from_erpFederation_with_mainnet_powpeg_post_RSKIP_353_creates_p2shErpFederation() throws Exception {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop401().forBlock(0);

        Address originalPowpegAddress = getMainnetPowpegAddress();
        List<UTXO> utxos = createRandomUtxos(originalPowpegAddress);

        Address newPowpegAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "3AboaP7AAJs4us95cWHxK4oRELmb4y7Pa7"
        );

        testChangePowpeg(
            FederationType.legacyErp,
            getMainnetPowpegKeys(),
            originalPowpegAddress,
            utxos,
            FederationType.p2shErp,
            getMainnetPowpegKeys(), // Using same keys as the original powpeg, should result in a different address since it will create a p2sh erp federation
            newPowpegAddress,
            bridgeConstants,
            activations,
            bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations)
        );
    }

    @Test
    void test_change_powpeg_from_p2shErpFederation_with_mainnet_powpeg_post_RSKIP_353_creates_p2shErpFederation() throws Exception {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop401().forBlock(0);

        Address originalPowpegAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "3AboaP7AAJs4us95cWHxK4oRELmb4y7Pa7"
        );
        List<UTXO> utxos = createRandomUtxos(originalPowpegAddress);

        List<Triple<BtcECKey, ECKey, ECKey>> newPowPegKeys = new ArrayList<>();
        newPowPegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
            ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
            ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ));
        newPowPegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
            ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
            ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ));
        newPowPegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
            ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
            ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ));

        Address newPowPegAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "3BqwgR9sxEsKUaApV6zJ5eU7DnabjjCvSU"
        );

        testChangePowpeg(
            FederationType.p2shErp,
            getMainnetPowpegKeys(),
            originalPowpegAddress,
            utxos,
            FederationType.p2shErp,
            newPowPegKeys,
            newPowPegAddress,
            bridgeConstants,
            activations,
            bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations)
        );
    }

    private static Stream<Arguments> activationsArgProvider() {
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest
            .hop401()
            .forBlock(0);

        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest
            .fingerroot500()
            .forBlock(0);

        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest
            .tbd600()
            .forBlock(0);

        return Stream.of(
            Arguments.of(hopActivations),
            Arguments.of(fingerrootActivations),
            Arguments.of(tbdActivations)
        );
    }

    @ParameterizedTest
    @MethodSource("activationsArgProvider")
    void test_change_powpeg_from_p2shErpFederation_with_mainnet_powpeg(ActivationConfig.ForBlock activations) throws Exception {
        Address originalPowpegAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "3AboaP7AAJs4us95cWHxK4oRELmb4y7Pa7"
        );
        List<UTXO> utxos = createRandomUtxos(originalPowpegAddress);

        List<Triple<BtcECKey, ECKey, ECKey>> newPowPegKeys = new ArrayList<>();
        newPowPegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
            ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
            ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ));
        newPowPegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
            ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
            ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ));
        newPowPegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
            ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
            ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ));

        Address newPowPegAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "3BqwgR9sxEsKUaApV6zJ5eU7DnabjjCvSU"
        );

        testChangePowpeg(
            FederationType.p2shErp,
            getMainnetPowpegKeys(),
            originalPowpegAddress,
            utxos,
            FederationType.p2shErp,
            newPowPegKeys,
            newPowPegAddress,
            bridgeConstants,
            activations,
            bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations)
        );
    }

    @Test
    void test_change_powpeg_from_p2shErpFederation_with_mainnet_powpeg_post_RSKIP_377_stores_standard_redeemScript() throws Exception {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest
            .fingerroot500()
            .forBlock(0);

        Address originalPowpegAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "3AboaP7AAJs4us95cWHxK4oRELmb4y7Pa7"
        );
        List<UTXO> utxos = createRandomUtxos(originalPowpegAddress);

        List<Triple<BtcECKey, ECKey, ECKey>> newPowPegKeys = new ArrayList<>();
        newPowPegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
            ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
            ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ));
        newPowPegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
            ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
            ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ));
        newPowPegKeys.add(Triple.of(
            BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
            ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
            ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ));

        Address newPowPegAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "3BqwgR9sxEsKUaApV6zJ5eU7DnabjjCvSU"
        );

        testChangePowpeg(
            FederationType.p2shErp,
            getMainnetPowpegKeys(),
            originalPowpegAddress,
            utxos,
            FederationType.p2shErp,
            newPowPegKeys,
            newPowPegAddress,
            bridgeConstants,
            activations,
            bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations)
        );
    }

    private enum FederationType {
        legacyErp,
        p2shErp,
        standardMultisig
    }
}

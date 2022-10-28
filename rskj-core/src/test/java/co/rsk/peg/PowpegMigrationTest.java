package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.trie.Trie;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bouncycastle.util.encoders.Hex;
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
import org.ethereum.util.MapSnapshot;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PowpegMigrationTest {

    private enum FederationType {
        erp,
        p2sh,
        standard
    }

    private void testChangePowpeg(
            FederationType federationTypeFrom,
            List<Triple<BtcECKey, ECKey, ECKey>> keysFrom,
            Address expectedAddressFrom,
            List<UTXO> existingUtxos,
            FederationType federationTypeTo,
            List<Triple<BtcECKey, ECKey, ECKey>> keysTo,
            Address expectedAddressTo,
            BridgeConstants bridgeConstants,
            ActivationConfig.ForBlock activations,
            long migrationShouldFinishAfterThisAmountOfBlocks
    ) throws Exception {

        Repository repository = new MutableRepository(
                new MutableTrieCache(
                        new MutableTrieImpl(null, new Trie())
                )
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

        BtcBlockStore btcBlockStore = btcBlockStoreFactory.newInstance(repository, bridgeConstants, bridgeStorageProvider, activations);

        // Setting a chain head different than genesis to avoid having to read the checkpoints file
        addNewBtcBlockOnTipOfChain(btcBlockStore, bridgeConstants);

        repository.save();

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        /***
         * Creation phase
         */

        Block initialBlock = mock(Block.class);
        when(initialBlock.getNumber()).thenReturn(0L);

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

        List<FederationMember> originalPowpegMembers = keysFrom.stream().map(theseKeys -> new FederationMember(theseKeys.getLeft(), theseKeys.getMiddle(), theseKeys.getRight())).collect(Collectors.toList());

        Federation originalPowpeg;
        switch (federationTypeFrom) {
            case erp:
                originalPowpeg = new ErpFederation(
                        originalPowpegMembers,
                        Instant.now(),
                        0,
                        bridgeConstants.getBtcParams(),
                        bridgeConstants.getErpFedPubKeysList(),
                        bridgeConstants.getErpFedActivationDelay(),
                        activations
                );
                break;
            case p2sh:
                originalPowpeg = new P2shErpFederation(
                        originalPowpegMembers,
                        Instant.now(),
                        0,
                        bridgeConstants.getBtcParams(),
                        bridgeConstants.getErpFedPubKeysList(),
                        bridgeConstants.getErpFedActivationDelay(),
                        activations
                );
                // TODO: CHECK REDEEMSCRIPT
                break;
            default:
                throw new Exception();
        }

        Assert.assertEquals(expectedAddressFrom, originalPowpeg.getAddress());

        // Set original powpeg informatino
        bridgeStorageProvider.setNewFederation(originalPowpeg);
        bridgeStorageProvider.getNewFederationBtcUTXOs().addAll(existingUtxos);

        // Create Pending federation (Doing this to avoid voting the pending Federation)
        List<FederationMember> newPowpegMembers = keysTo.stream().map(theseKeys -> new FederationMember(theseKeys.getLeft(), theseKeys.getMiddle(), theseKeys.getRight())).collect(Collectors.toList());
        PendingFederation pendingFederation = new PendingFederation(newPowpegMembers);
        // Create the Federation just to provide it to utilitary methods
        Federation newFederation = pendingFederation.buildFederation(Instant.now(), 0, bridgeConstants, activations);

        // Set pending powpeg information
        bridgeStorageProvider.setPendingFederation(pendingFederation);

        bridgeStorageProvider.save();

        // Proceed with powpeg change
        bridgeSupport.commitFederation(false, pendingFederation.getHash());

        ArgumentCaptor<Federation> argumentCaptor = ArgumentCaptor.forClass(Federation.class);
        verify(bridgeEventLogger).logCommitFederation(any(), eq(originalPowpeg), argumentCaptor.capture());

        // Verify new powpeg information
        Federation newPowPeg = argumentCaptor.getValue();
        assertEquals(expectedAddressTo, newPowPeg.getAddress());
        switch (federationTypeTo) {
            case erp:
                assertTrue(newPowPeg instanceof ErpFederation);
                assertFalse(newPowPeg instanceof P2shErpFederation);
                break;
            case p2sh:
                assertTrue(newPowPeg instanceof ErpFederation);
                assertTrue(newPowPeg instanceof P2shErpFederation);
                // TODO: CHECK REDEEMSCRIPT
                break;
        }

        // Verify UTXOs were moved to pending POWpeg
        List<UTXO> utxosToMigrate = bridgeStorageProvider.getOldFederationBtcUTXOs();
        for (UTXO utxo: existingUtxos) {
            assertTrue(utxosToMigrate.stream().anyMatch(storedUtxo -> storedUtxo.equals(utxo)));
        }
        assertTrue(bridgeStorageProvider.getNewFederationBtcUTXOs().isEmpty());

        // Trying to create a new powpeg again should fail
        // -2 corresponds to a new powpeg was elected and the Bridge is waiting for this new powpeg to activate
        attemptToCreateNewFederation(bridgeSupport, bridgeConstants, -2);

        // No change in active powpeg
        assertEquals(expectedAddressFrom, bridgeSupport.getFederationAddress());
        assertNull(bridgeSupport.getRetiringFederationAddress());

        // Update collections should not trigger migration
        assertTrue(bridgeStorageProvider.getReleaseTransactionSet().getEntries().isEmpty());
        Transaction updateCollectionsTx = mock(Transaction.class);
        when(updateCollectionsTx.getHash()).thenReturn(Keccak256.ZERO_HASH);
        bridgeSupport.updateCollections(updateCollectionsTx);
        assertTrue(bridgeStorageProvider.getReleaseTransactionSet().getEntries().isEmpty());

        /***
         * peg-in after commiting new fed
         */

        testPegins(bridgeSupport, bridgeConstants, bridgeStorageProvider, btcBlockStore, expectedAddressFrom, expectedAddressTo, true, false);

        testFlyoverPegins(bridgeSupport, bridgeConstants, bridgeStorageProvider, btcBlockStore, originalPowpeg, newFederation, true, false);

        /***
         * Activation phase
         */

        // Move the required blocks ahead for the new powpeg to become active
        Block activationBlock = mock(Block.class);
        doReturn(initialBlock.getNumber() + bridgeConstants.getFederationActivationAge()).when(activationBlock).getNumber();

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
        assertEquals(expectedAddressTo, bridgeSupport.getFederationAddress());
        assertEquals(expectedAddressFrom, bridgeSupport.getRetiringFederationAddress());

        if (bridgeConstants.getFundsMigrationAgeSinceActivationBegin() > 0) {
            // No migration yet
            assertTrue(bridgeStorageProvider.getReleaseTransactionSet().getEntries().isEmpty());
            updateCollectionsTx = mock(Transaction.class);
            when(updateCollectionsTx.getHash()).thenReturn(Keccak256.ZERO_HASH);
            bridgeSupport.updateCollections(updateCollectionsTx);
            assertTrue(bridgeStorageProvider.getReleaseTransactionSet().getEntries().isEmpty());
        }

        // Trying to create a new powpeg again should fail
        // -3 corresponds to a new powpeg was elected and the Bridge is waiting for this new powpeg to migrate
        attemptToCreateNewFederation(bridgeSupport, bridgeConstants, -3);

        /***
         * peg-in after new fed activates
         */

        testPegins(bridgeSupport, bridgeConstants, bridgeStorageProvider, btcBlockStore, expectedAddressFrom, expectedAddressTo, true, true);

        testFlyoverPegins(bridgeSupport, bridgeConstants, bridgeStorageProvider, btcBlockStore, originalPowpeg, newFederation, true, true);

        /***
         * Migration phase
         */

        // Move the required blocks ahead for the new powpeg to start migrating
        Block migrationBlock = mock(Block.class);
        // Adding 1 as the migration is exclusive
        doReturn(activationBlock.getNumber() + bridgeConstants.getFundsMigrationAgeSinceActivationBegin() + 1).when(migrationBlock).getNumber();

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
        assertEquals(expectedAddressTo, bridgeSupport.getFederationAddress());
        assertEquals(expectedAddressFrom, bridgeSupport.getRetiringFederationAddress());

        // Trying to create a new powpeg again should fail
        // -3 corresponds to a new powpeg was elected and the Bridge is waiting for this new powpeg to migrate
        attemptToCreateNewFederation(bridgeSupport, bridgeConstants, -3);

        // Migration should start !
        assertTrue(bridgeStorageProvider.getReleaseTransactionSet().getEntries().isEmpty());

        // This might not be true if the transaction exceeds the max bitcoin transaction size!!!
        int expectedMigrations = activations.isActive(ConsensusRule.RSKIP294) ? (int)Math.ceil((double)utxosToMigrate.size() / bridgeConstants.getMaxInputsPerPegoutTransaction()): 1;

        // Migrate while there are still utxos to migrate
        while(true) {
            updateCollectionsTx = mock(Transaction.class);
            when(updateCollectionsTx.getHash()).thenReturn(Keccak256.ZERO_HASH);
            bridgeSupport.updateCollections(updateCollectionsTx);

            if (bridgeStorageProvider.getOldFederationBtcUTXOs().isEmpty()) {
                break;
            }
        }
        assertEquals(expectedMigrations, bridgeStorageProvider.getReleaseTransactionSet().getEntries().size());
        for (ReleaseTransactionSet.Entry pegout: bridgeStorageProvider.getReleaseTransactionSet().getEntries()) {
            // This would fail if we were to implement UTXO expansion at some point
            assertEquals(1, pegout.getTransaction().getOutputs().size());
            assertEquals(expectedAddressTo, pegout.getTransaction().getOutput(0).getAddressFromP2SH(bridgeConstants.getBtcParams()));
        }

        /***
         * peg-in during migration
         */

        testPegins(bridgeSupport, bridgeConstants, bridgeStorageProvider, btcBlockStore, expectedAddressFrom, expectedAddressTo, true, true);

        testFlyoverPegins(bridgeSupport, bridgeConstants, bridgeStorageProvider, btcBlockStore, originalPowpeg, newFederation, true, true);

        // Should be migrated

        int newlyAddedUtxos = activations.isActive(ConsensusRule.RSKIP294) ? (int)Math.ceil((double)bridgeStorageProvider.getOldFederationBtcUTXOs().size() / bridgeConstants.getMaxInputsPerPegoutTransaction()): 1;

        // Migrate while there are still utxos to migrate
        while(true) {
            updateCollectionsTx = mock(Transaction.class);
            when(updateCollectionsTx.getHash()).thenReturn(Keccak256.ZERO_HASH);
            bridgeSupport.updateCollections(updateCollectionsTx);

            if (bridgeStorageProvider.getOldFederationBtcUTXOs().isEmpty()) {
                break;
            }
        }

        assertEquals(expectedMigrations + newlyAddedUtxos, bridgeStorageProvider.getReleaseTransactionSet().getEntries().size());

        /***
         * After Migration phase
         */

        // Move the height to the block previous to the migration finishing, it should keep on migrating
        Block migrationFinishingBlock = mock(Block.class);
        // Substracting 2 as the previous height was activation + 1 and migration is exclusive
        doReturn(migrationBlock.getNumber() + bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations) - 2).when(migrationFinishingBlock).getNumber();
        assertEquals(migrationShouldFinishAfterThisAmountOfBlocks, bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations));

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
        assertEquals(expectedAddressTo, bridgeSupport.getFederationAddress());
        assertEquals(expectedAddressFrom, bridgeSupport.getRetiringFederationAddress());

        // The first Update collections after the migration finished should get rid of the retiring powpeg
        updateCollectionsTx = mock(Transaction.class);
        when(updateCollectionsTx.getHash()).thenReturn(Keccak256.ZERO_HASH);
        bridgeSupport.updateCollections(updateCollectionsTx);

        // New active powpeg and retiring powpeg is still there
        assertEquals(expectedAddressTo, bridgeSupport.getFederationAddress());
        assertEquals(expectedAddressFrom, bridgeSupport.getRetiringFederationAddress());

        // Move the height to the block previous to the migration finishing, it should keep on migrating
        Block migrationFinishedBlock = mock(Block.class);
        doReturn(migrationFinishingBlock.getNumber() + 3).when(migrationFinishedBlock).getNumber();
        assertEquals(migrationShouldFinishAfterThisAmountOfBlocks, bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations));

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
        assertEquals(expectedAddressTo, bridgeSupport.getFederationAddress());
        assertEquals(expectedAddressFrom, bridgeSupport.getRetiringFederationAddress());

        // The first Update collections after the migration finished should get rid of the retiring powpeg
        updateCollectionsTx = mock(Transaction.class);
        when(updateCollectionsTx.getHash()).thenReturn(Keccak256.ZERO_HASH);
        bridgeSupport.updateCollections(updateCollectionsTx);

        // New active powpeg and retiring powpeg no longer there
        assertEquals(expectedAddressTo, bridgeSupport.getFederationAddress());
        assertNull(bridgeSupport.getRetiringFederationAddress());

        /***
         * peg-in during migration
         */

        testPegins(bridgeSupport, bridgeConstants, bridgeStorageProvider, btcBlockStore, expectedAddressFrom, expectedAddressTo, false, true);

        testFlyoverPegins(bridgeSupport, bridgeConstants, bridgeStorageProvider, btcBlockStore, originalPowpeg, newFederation, false, true);

    }

    private void testPegins(
            BridgeSupport bridgeSupport,
            BridgeConstants bridgeConstants,
            BridgeStorageProvider bridgeStorageProvider,
            BtcBlockStore btcBlockStore,
            Address expectedAddressFrom,
            Address expectedAddressTo,
            boolean shouldPeginToOldPowpegWork,
            boolean shouldPeginToNewPowpegWork
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {

        // Perform valid peg-in to active powpeg - should work
        BtcTransaction peginToRetiringPowpeg = createPegin(
                bridgeSupport,
                bridgeConstants,
                btcBlockStore,
                Arrays.asList(new TransactionOutput(bridgeConstants.getBtcParams(), null, Coin.COIN, expectedAddressFrom)),
                true,
                true,
                true
        );

        assertEquals(true, bridgeSupport.isBtcTxHashAlreadyProcessed(peginToRetiringPowpeg.getHash()));
        assertEquals(shouldPeginToOldPowpegWork, bridgeStorageProvider.getOldFederationBtcUTXOs().stream().anyMatch(utxo -> utxo.getHash().equals(peginToRetiringPowpeg.getHash())));
        assertFalse(bridgeStorageProvider.getNewFederationBtcUTXOs().stream().anyMatch(utxo -> utxo.getHash().equals(peginToRetiringPowpeg.getHash())));

        if (!expectedAddressFrom.equals(expectedAddressTo)) {
            // Perform valid peg-in to future powpeg - should be ignored
            BtcTransaction peginToFuturePowpeg = createPegin(
                    bridgeSupport,
                    bridgeConstants,
                    btcBlockStore,
                    Arrays.asList(new TransactionOutput(bridgeConstants.getBtcParams(), null, Coin.COIN, expectedAddressTo)),
                    true,
                    true,
                    true
            );

            // This assertion should change when we change peg-in verification
            assertEquals(true, bridgeSupport.isBtcTxHashAlreadyProcessed(peginToFuturePowpeg.getHash()));
            assertFalse(bridgeStorageProvider.getOldFederationBtcUTXOs().stream().anyMatch(utxo -> utxo.getHash().equals(peginToFuturePowpeg.getHash())));
            assertEquals(shouldPeginToNewPowpegWork, bridgeStorageProvider.getNewFederationBtcUTXOs().stream().anyMatch(utxo -> utxo.getHash().equals(peginToFuturePowpeg.getHash())));
        }

    }

    private void attemptToCreateNewFederation(BridgeSupport bridgeSupport, BridgeConstants bridgeConstants, int expectedResult) throws BridgeIllegalArgumentException {
        ABICallSpec createSpec = new ABICallSpec("create", new byte[][]{});
        Transaction voteTx = mock(Transaction.class);
        when(voteTx.getSender()).thenReturn(new RskAddress(bridgeConstants.getFederationChangeAuthorizer().authorizedAddresses.get(0)));
        assertEquals(expectedResult, bridgeSupport.voteFederationChange(voteTx, createSpec).intValue());
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
        Pair<BtcTransaction, Keccak256> flyoverPeginToRetiringPowpeg =
                createFlyoverPegin(bridgeSupport, bridgeConstants, btcBlockStore, recipientOldFederation, true, true, true);

        assertEquals(
                shouldPeginToOldPowpegWork,
                bridgeStorageProvider.isFlyoverDerivationHashUsed(
                        flyoverPeginToRetiringPowpeg.getLeft().getHash(),
                        flyoverPeginToRetiringPowpeg.getRight()
                )
        );
        assertEquals(
                shouldPeginToOldPowpegWork,
                bridgeStorageProvider.getOldFederationBtcUTXOs().stream()
                        .anyMatch(utxo -> utxo.getHash().equals(flyoverPeginToRetiringPowpeg.getLeft().getHash()))
        );
        assertFalse(
                bridgeStorageProvider.getNewFederationBtcUTXOs().stream()
                        .anyMatch(utxo -> utxo.getHash().equals(flyoverPeginToRetiringPowpeg.getLeft().getHash()))
        );

        Pair<BtcTransaction, Keccak256> flyoverPeginToNewPowpeg =
                createFlyoverPegin(bridgeSupport, bridgeConstants, btcBlockStore, recipientNewFederation, true, true, true);

        assertEquals(
                shouldPeginToNewPowpegWork,
                bridgeStorageProvider.isFlyoverDerivationHashUsed(
                        flyoverPeginToNewPowpeg.getLeft().getHash(),
                        flyoverPeginToNewPowpeg.getRight()
                )
        );
        assertFalse(
                bridgeStorageProvider.getOldFederationBtcUTXOs().stream()
                        .anyMatch(utxo -> utxo.getHash().equals(flyoverPeginToNewPowpeg.getLeft().getHash()))
        );
        assertEquals(
                shouldPeginToNewPowpegWork,
                bridgeStorageProvider.getNewFederationBtcUTXOs().stream()
                        .anyMatch(utxo -> utxo.getHash().equals(flyoverPeginToNewPowpeg.getLeft().getHash()))
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
                null
        );

        BtcTransaction peginBtcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        // Randomize the input to avoid repeating same Btc tx hash
        peginBtcTx.addInput(PegTestUtils.createHash(blockStore.getChainHead().getHeight() + 1), 0, new Script(new byte[]{}));

        // The derivation arguments will be randomly calculated
        // The serialization and hashing was extracted from https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP176.md#bridge
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(blockStore.getChainHead().getHeight() + 1);
        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address liquidityProviderBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        byte[] infoToHash = new byte[94];
        System.arraycopy(
                derivationArgumentsHash.getBytes(),
                0,
                infoToHash,
                0,
                32
        );
        System.arraycopy(
                userRefundBtcAddress.getVersion() != 0 ? ByteUtil.intToBytesNoLeadZeroes(userRefundBtcAddress.getVersion()) : new byte[]{0},
                0,
                infoToHash,
                32,
                1
        );
        System.arraycopy(
                userRefundBtcAddress.getHash160(),
                0,
                infoToHash,
                33,
                20
        );
        System.arraycopy(
                lbcAddress.getBytes(),
                0,
                infoToHash,
                53,
                20
        );

        System.arraycopy(
                liquidityProviderBtcAddress.getVersion() != 0 ? ByteUtil.intToBytesNoLeadZeroes(liquidityProviderBtcAddress.getVersion()) : new byte[]{0},
                0,
                infoToHash,
                73,
                1
        );
        System.arraycopy(
                liquidityProviderBtcAddress.getHash160(),
                0,
                infoToHash,
                74,
                20
        );
        Keccak256 derivationHash = new Keccak256(HashUtil.keccak256(infoToHash));

        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
                recipientFederation.getRedeemScript(),
                Sha256Hash.wrap(derivationHash.toHexString()) // Parsing to Sha256Hash in order to use helper. Does not change functionality
        );

        peginBtcTx.addOutput(
                Coin.COIN,
                Address.fromP2SHHash(
                    bridgeConstants.getBtcParams(),
                    ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript).getPubKeyHash()
                )
        );

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
                    Arrays.asList(peginBtcTx)
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

        PartialMerkleTree pmt = new PartialMerkleTree(
                bridgeConstants.getBtcParams(),
                new byte[] { 1 },
                Arrays.asList(shouldHaveValidPmt ? peginBtcTx.getHash() : Sha256Hash.ZERO_HASH),
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
        peginBtcTx.addInput(PegTestUtils.createHash(blockStore.getChainHead().getHeight() + 1), 0, new Script(new byte[]{}));
        outputs.forEach(peginBtcTx::addOutput);
        // Adding OP_RETURN output to identify this peg-in as v1 and avoid sender identification
        peginBtcTx.addOutput(Coin.ZERO, PegTestUtils.createOpReturnScriptForRsk(1, PrecompiledContracts.BRIDGE_ADDR, Optional.empty()));

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
                    Arrays.asList(peginBtcTx)
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

        PartialMerkleTree pmt = new PartialMerkleTree(
                bridgeConstants.getBtcParams(),
                new byte[] { 1 },
                Arrays.asList(shouldHaveValidPmt ? peginBtcTx.getHash() : Sha256Hash.ZERO_HASH),
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
        StoredBlock storedBlock = new StoredBlock(btcBlock, BigInteger.ZERO, chainHead.getHeight() + 1);
        blockStore.put(storedBlock);
        blockStore.setChainHead(storedBlock);
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
        return new Random().nextInt(max - min + 1) + min;
    }

    private List<UTXO> createRandomUtxos(Script owner) {
        List<UTXO> result = new ArrayList<>();

        int howMany = getRandomInt(100,1000);
        for (int i = 1; i <= howMany; i++) {
            Coin randomValue = Coin.valueOf(getRandomInt(10_000, 1_000_000_000));
            result.add(new UTXO(PegTestUtils.createHash(i), 0, randomValue, 0, false, owner));
        }

        return result;
    }

    @Test
    public void test_change_powpeg__from_erpFederation__with_mainnet_powpeg_pre_RSKIP_353__creates_erpFederation() throws Exception {
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop400().forBlock(0);

        Address originalPowpegAddress = Address.fromBase58(bridgeConstants.getBtcParams(), "3DsneJha6CY6X9gU2M9uEc4nSdbYECB4Gh");
        List<UTXO> utxos = createRandomUtxos(ScriptBuilder.createOutputScript(originalPowpegAddress));

        List<Triple<BtcECKey, ECKey, ECKey>> otherKeys = new ArrayList<>();
        otherKeys.add(Triple.of(
                BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
                ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
                ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ));
        otherKeys.add(Triple.of(
                BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
                ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
                ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ));
        otherKeys.add(Triple.of(
                BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
                ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
                ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ));

        testChangePowpeg(
                FederationType.erp,
                getMainnetPowpegKeys(),
                originalPowpegAddress,
                utxos,
                FederationType.erp,
                otherKeys,
                Address.fromBase58(bridgeConstants.getBtcParams(), "3Lqn662zEgbPU4nRYowUo9UY7HNRkbBNgN"),
                bridgeConstants,
                activations,
                10585L // This value was extracted from co.rsk.config.BridgeConstants.fundsMigrationAgeSinceActivationEnd
        );
    }

    @Test
    public void test_change_powpeg__from_erpFederation__with_mainnet_powpeg_post_RSKIP_353__creates_p2shErpFederation() throws Exception {
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop401().forBlock(0);

        Address originalPowpegAddress = Address.fromBase58(bridgeConstants.getBtcParams(), "3DsneJha6CY6X9gU2M9uEc4nSdbYECB4Gh");
        List<UTXO> utxos = createRandomUtxos(ScriptBuilder.createOutputScript(originalPowpegAddress));

        testChangePowpeg(
                FederationType.erp,
                getMainnetPowpegKeys(),
                originalPowpegAddress,
                utxos,
                FederationType.p2sh,
                getMainnetPowpegKeys(), // Using same keys as the original powpeg
                Address.fromBase58(bridgeConstants.getBtcParams(), "3AboaP7AAJs4us95cWHxK4oRELmb4y7Pa7"),
                bridgeConstants,
                activations,
                172_800L // This value was extracted from co.rsk.config.BridgeConstants.specialCaseFundsMigrationAgeSinceActivationEnd
        );
    }

    @Test
    public void test_change_powpeg__from_p2shErpFederation__with_mainnet_powpeg_post_RSKIP_353__creates_p2shErpFederation() throws Exception {
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.hop401().forBlock(0);

        Address originalPowpegAddress = Address.fromBase58(bridgeConstants.getBtcParams(), "3AboaP7AAJs4us95cWHxK4oRELmb4y7Pa7");
        List<UTXO> utxos = createRandomUtxos(ScriptBuilder.createOutputScript(originalPowpegAddress));

        List<Triple<BtcECKey, ECKey, ECKey>> otherKeys = new ArrayList<>();
        otherKeys.add(Triple.of(
                BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
                ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
                ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ));
        otherKeys.add(Triple.of(
                BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
                ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
                ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ));
        otherKeys.add(Triple.of(
                BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
                ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
                ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ));

        testChangePowpeg(
                FederationType.p2sh,
                getMainnetPowpegKeys(),
                originalPowpegAddress,
                utxos,
                FederationType.p2sh,
                otherKeys,
                Address.fromBase58(bridgeConstants.getBtcParams(), "3BqwgR9sxEsKUaApV6zJ5eU7DnabjjCvSU"),
                bridgeConstants,
                activations,
                172_800L // This value was extracted from co.rsk.config.BridgeConstants.specialCaseFundsMigrationAgeSinceActivationEnd
        );
    }

    @Test
    public void test_change_powpeg__from_p2shErpFederation__with_mainnet_powpeg_post_RSKIP_353_with_RSKIP_357_disabled__creates_p2shErpFederation() throws Exception {
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.enableTheseDisableThose(
                ActivationConfigsForTest.getHop401Rskips(),
                Arrays.asList(ConsensusRule.RSKIP357)
        ).forBlock(0);

        Address originalPowpegAddress = Address.fromBase58(bridgeConstants.getBtcParams(), "3AboaP7AAJs4us95cWHxK4oRELmb4y7Pa7");
        List<UTXO> utxos = createRandomUtxos(ScriptBuilder.createOutputScript(originalPowpegAddress));

        List<Triple<BtcECKey, ECKey, ECKey>> otherKeys = new ArrayList<>();
        otherKeys.add(Triple.of(
                BtcECKey.fromPublicOnly(Hex.decode("020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c")),
                ECKey.fromPublicOnly(Hex.decode("0305a99716bcdbb4c0686906e77daf8f7e59e769d1f358a88a23e3552376f14ed2")),
                ECKey.fromPublicOnly(Hex.decode("02be1c54e8582e744d0d5d6a9b8e4a6d810029bcefc30e39b54688c4f1b718c0ee"))
        ));
        otherKeys.add(Triple.of(
                BtcECKey.fromPublicOnly(Hex.decode("0231a395e332dde8688800a0025cccc5771ea1aa874a633b8ab6e5c89d300c7c36")),
                ECKey.fromPublicOnly(Hex.decode("02e3f03aa985357dc356c2a763b44310b22be3b960303a67cde948fcfba97f5309")),
                ECKey.fromPublicOnly(Hex.decode("029963d972f8a4ccac4bad60ed8b20ec83f6a15ca7076e057cccb4a34eed1a14d0"))
        ));
        otherKeys.add(Triple.of(
                BtcECKey.fromPublicOnly(Hex.decode("025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db")),
                ECKey.fromPublicOnly(Hex.decode("02be5d357d62be7b2d42de0343d1297129a0a8b5f6b8bb8c46eefc9504db7b56e1")),
                ECKey.fromPublicOnly(Hex.decode("032706b02f64b38b4ef7c75875aaf65de868c4aa0d2d042f724e16924fa13ffa6c"))
        ));

        testChangePowpeg(
                FederationType.p2sh,
                getMainnetPowpegKeys(),
                originalPowpegAddress,
                utxos,
                FederationType.p2sh,
                otherKeys,
                Address.fromBase58(bridgeConstants.getBtcParams(), "3BqwgR9sxEsKUaApV6zJ5eU7DnabjjCvSU"),
                bridgeConstants,
                activations,
                10585L // This value was extracted from co.rsk.config.BridgeConstants.fundsMigrationAgeSinceActivationEnd
        );
    }

}

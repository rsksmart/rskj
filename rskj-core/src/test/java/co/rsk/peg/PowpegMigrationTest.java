package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.trie.Trie;
import org.apache.commons.lang3.tuple.Triple;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
            ActivationConfig.ForBlock activations) throws Exception {

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

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        /***
         * Creation phase
         */

        Block initialBlock = mock(Block.class);
        when(initialBlock.getNumber()).thenReturn(0L);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(initialBlock)
                .withActivations(activations)
                .withBridgeConstants(bridgeConstants)
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
         * Activation phase
         */

        // Move the required blocks ahead for the new powpeg to become active
        Block activationBlock = mock(Block.class);
        doReturn(initialBlock.getNumber() + bridgeConstants.getFederationActivationAge()).when(activationBlock).getNumber();

        bridgeSupport = new BridgeSupportBuilder()
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(activationBlock)
                .withActivations(activations)
                .withBridgeConstants(bridgeConstants)
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
         * Migration phase
         */

        // Move the required blocks ahead for the new powpeg to start migrating
        Block migrationBlock = mock(Block.class);
        doReturn(activationBlock.getNumber() + bridgeConstants.getFundsMigrationAgeSinceActivationBegin() + 1).when(migrationBlock).getNumber();

        bridgeSupport = new BridgeSupportBuilder()
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(migrationBlock)
                .withActivations(activations)
                .withBridgeConstants(bridgeConstants)
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

        // Move the required blocks ahead for the new powpeg to start migrating
        Block migrationFinishingBlock = mock(Block.class);
        doReturn(migrationBlock.getNumber() + bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations) + 1).when(migrationFinishingBlock).getNumber();

        bridgeSupport = new BridgeSupportBuilder()
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(migrationFinishingBlock)
                .withActivations(activations)
                .withBridgeConstants(bridgeConstants)
                .build();

        // New active powpeg and retiring powpeg is still there
        assertEquals(expectedAddressTo, bridgeSupport.getFederationAddress());

        // The first Update collections after the migration finished should get rid of the retiring powpeg
        updateCollectionsTx = mock(Transaction.class);
        when(updateCollectionsTx.getHash()).thenReturn(Keccak256.ZERO_HASH);
        bridgeSupport.updateCollections(updateCollectionsTx);

        // New active powpeg and retiring powpeg no longer there
        assertEquals(expectedAddressTo, bridgeSupport.getFederationAddress());
        assertNull(bridgeSupport.getRetiringFederationAddress());

    }

    private void attemptToCreateNewFederation(BridgeSupport bridgeSupport, BridgeConstants bridgeConstants, int expectedResult) throws BridgeIllegalArgumentException {
        ABICallSpec createSpec = new ABICallSpec("create", new byte[][]{});
        Transaction voteTx = mock(Transaction.class);
        when(voteTx.getSender()).thenReturn(new RskAddress(bridgeConstants.getFederationChangeAuthorizer().authorizedAddresses.get(0)));
        assertEquals(expectedResult, bridgeSupport.voteFederationChange(voteTx, createSpec).intValue());
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

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP353).forBlock(0);

        Address originalPowpegAddress = Address.fromBase58(bridgeConstants.getBtcParams(), "3DsneJha6CY6X9gU2M9uEc4nSdbYECB4Gh");
        List<UTXO> utxos = createRandomUtxos(ScriptBuilder.createOutputScript(originalPowpegAddress));

        testChangePowpeg(
                FederationType.erp,
                getMainnetPowpegKeys(),
                originalPowpegAddress,
                utxos,
                FederationType.erp,
                getMainnetPowpegKeys(), // Using same keys as the original powpeg
                Address.fromBase58(bridgeConstants.getBtcParams(), "3DsneJha6CY6X9gU2M9uEc4nSdbYECB4Gh"),
                bridgeConstants,
                activations
        );
    }

    @Test
    public void test_change_powpeg__from_erpFederation__with_mainnet_powpeg_post_RSKIP_353__creates_p2shErpFederation() throws Exception {
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.allBut().forBlock(0);

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
                activations
        );
    }

    @Test
    public void test_change_powpeg__from_p2shErpFederation__with_mainnet_powpeg_post_RSKIP_353__creates_p2shErpFederation() throws Exception {
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();

        ActivationConfig.ForBlock activations = ActivationConfigsForTest.allBut().forBlock(0);

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
                otherKeys, // Using same keys as the original powpeg
                Address.fromBase58(bridgeConstants.getBtcParams(), "3BqwgR9sxEsKUaApV6zJ5eU7DnabjjCvSU"),
                bridgeConstants,
                activations
        );
    }

}

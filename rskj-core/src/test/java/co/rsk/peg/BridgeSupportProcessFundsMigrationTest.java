package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BridgeSupportProcessFundsMigrationTest {

    @Test
    void processFundsMigration_in_migration_age_before_rskip_146_activation_testnet() throws IOException {
        test_processFundsMigration(BridgeTestNetConstants.getInstance(), false, false, true);
    }

    @Test
    void processFundsMigration_in_migration_age_after_rskip_146_activation_testnet() throws IOException {
        test_processFundsMigration(BridgeTestNetConstants.getInstance(), true, false, true);
    }

    @Test
    void processFundsMigration_in_migration_age_after_rskip_357_activation_testnet() throws IOException {
        test_processFundsMigration(BridgeTestNetConstants.getInstance(), true, true, true);
    }

    @Test
    void processFundsMigration_in_migration_age_after_rskip_374_activation_testnet() throws IOException {
        test_processFundsMigration(BridgeTestNetConstants.getInstance(), true, true, true, true);
    }

    @Test
    void processFundsMigration_past_migration_age_before_rskip_146_activation_testnet() throws IOException {
        test_processFundsMigration(BridgeTestNetConstants.getInstance(), false, false, false);
    }

    @Test
    void processFundsMigration_past_migration_age_after_rskip_146_activation_testnet() throws IOException {
        test_processFundsMigration(BridgeTestNetConstants.getInstance(), true, false, false);
    }

    @Test
    void processFundsMigration_past_migration_age_after_rskip_357_activation_testnet() throws IOException {
        test_processFundsMigration(BridgeTestNetConstants.getInstance(), true, true, false);
    }

    @Test
    void processFundsMigration_past_migration_age_after_rskip_374_activation_testnet() throws IOException {
        test_processFundsMigration(BridgeTestNetConstants.getInstance(), true, true, true, false);
    }

    @Test
    void processFundsMigration_in_migration_age_before_rskip_146_activation_mainnet() throws IOException {
        test_processFundsMigration(BridgeMainNetConstants.getInstance(), false, false, true);
    }

    @Test
    void processFundsMigration_in_migration_age_after_rskip_146_activation_mainnet() throws IOException {
        test_processFundsMigration(BridgeMainNetConstants.getInstance(), true, false, true);
    }

    @Test
    void processFundsMigration_in_migration_age_after_rskip_357_activation_mainnet() throws IOException {
        test_processFundsMigration(BridgeMainNetConstants.getInstance(), true, true, true);
    }

    @Test
    void processFundsMigration_in_migration_age_after_rskip_374_activation_mainnet() throws IOException {
        test_processFundsMigration(BridgeMainNetConstants.getInstance(), true, true, true, true);
    }

    @Test
    void processFundsMigration_past_migration_age_before_rskip_146_activation_mainnet() throws IOException {
        test_processFundsMigration(BridgeMainNetConstants.getInstance(), false, false, false);
    }

    @Test
    void processFundsMigration_past_migration_age_after_rskip_146_activation_mainnet() throws IOException {
        test_processFundsMigration(BridgeMainNetConstants.getInstance(), true, false, false);
    }

    @Test
    void processFundsMigration_past_migration_age_after_rskip_357_activation_mainnet() throws IOException {
        test_processFundsMigration(BridgeMainNetConstants.getInstance(), true, true, false);
    }

    @Test
    void processFundsMigration_past_migration_age_after_rskip_374_activation_mainnet() throws IOException {
        test_processFundsMigration(BridgeMainNetConstants.getInstance(), true, true, true, false);
    }

    private void test_processFundsMigration(
        BridgeConstants bridgeConstants,
        boolean isRskip146Active,
        boolean isRskip357Active,
        boolean inMigrationAge
    ) throws IOException {
        test_processFundsMigration(bridgeConstants, isRskip146Active, isRskip357Active, false, inMigrationAge);
    }

    private void test_processFundsMigration(
        BridgeConstants bridgeConstants,
        boolean isRskip146Active,
        boolean isRskip357Active,
        boolean isRskip374Active,
        boolean inMigrationAge) throws IOException {

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(isRskip146Active);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(isRskip357Active);
        when(activations.isActive(ConsensusRule.RSKIP374)).thenReturn(isRskip374Active);


        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstants.getGenesisFederation();

        long federationCreationBlockNumber = 5L;
        long federationInMigrationAgeHeight = federationCreationBlockNumber +
            bridgeConstants.getFederationActivationAge() +
            bridgeConstants.getFundsMigrationAgeSinceActivationBegin() + 1;
        long federationPastMigrationAgeHeight = federationCreationBlockNumber +
            bridgeConstants.getFederationActivationAge() +
            bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations) + 1;

        Federation newFederation = new Federation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            federationCreationBlockNumber,
            bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getReleaseTransactionSet()).thenReturn(new ReleaseTransactionSet(Collections.emptySet()));
        when(provider.getOldFederation()).thenReturn(oldFederation);
        when(provider.getNewFederation()).thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();

        long updateCollectionsCallHeight = inMigrationAge ? federationInMigrationAgeHeight : federationPastMigrationAgeHeight;
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(updateCollectionsCallHeight, 1);

        BridgeSupport bridgeSupport = new BridgeSupportBuilder()
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        List<UTXO> sufficientUTXOsForMigration = new ArrayList<>();
        sufficientUTXOsForMigration.add(PegTestUtils.createUTXO(
            0,
            0,
            Coin.COIN,
            oldFederation.getAddress()
        ));
        when(provider.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration);

        Transaction updateCollectionsTx = buildUpdateCollectionsTransaction();
        bridgeSupport.updateCollections(updateCollectionsTx);

        Assertions.assertEquals(isRskip146Active ? 0 : 1, provider.getReleaseTransactionSet().getEntriesWithoutHash().size());
        Assertions.assertEquals(isRskip146Active ? 1 : 0, provider.getReleaseTransactionSet().getEntriesWithHash().size());

        if (isRskip146Active) {
            // Should have been logged with the migrated UTXO
            ReleaseTransactionSet.Entry entry = (ReleaseTransactionSet.Entry) provider.getReleaseTransactionSet()
                .getEntriesWithHash()
                .toArray()[0];
            verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(
                updateCollectionsTx.getHash().getBytes(),
                entry.getTransaction(),
                Coin.COIN
            );
        } else {
            verify(bridgeEventLogger, never()).logReleaseBtcRequested(
                any(byte[].class),
                any(BtcTransaction.class),
                any(Coin.class)
            );
        }
    }

    private Transaction buildUpdateCollectionsTransaction() {
        final String TO_ADDRESS = "0000000000000000000000000000000000000006";
        final BigInteger DUST_AMOUNT = new BigInteger("1");
        final BigInteger NONCE = new BigInteger("0");
        final BigInteger GAS_PRICE = new BigInteger("100");
        final BigInteger GAS_LIMIT = new BigInteger("1000");
        final String DATA = "80af2871";

        return Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
    }
}

package co.rsk.peg;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import co.rsk.peg.feeperkb.*;
import co.rsk.peg.feeperkb.constants.FeePerKbConstants;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static co.rsk.RskTestUtils.createRepository;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessFundsMigrationTest {

    @Test
    void updateCollections_withP2shP2wshActiveFed_withNoRetiringFederation_shouldNotCreateAMigrationTx() throws IOException {
        // Arrange
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        ActivationConfig.ForBlock activationConfig = ActivationConfigsForTest.all().forBlock(0L);
        NetworkParameters networkParameters = bridgeConstants.getBtcParams();
        Repository repository = createRepository();

        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(repository, networkParameters, activationConfig);
        StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
        Coin feePerKb = Coin.valueOf(8_000L);
        bridgeStorageAccessor.saveToRepository(FeePerKbStorageIndexKey.FEE_PER_KB.getKey(), feePerKb, BridgeSerializationUtils::serializeCoin);
        FeePerKbConstants feePerKbConstants = bridgeConstants.getFeePerKbConstants();
        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(bridgeStorageAccessor);
        FeePerKbSupport feePerKbSupport = new FeePerKbSupportImpl(feePerKbConstants, feePerKbStorageProvider);
        FederationStorageProviderImpl federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activationConfig)
            .build();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBridgeConstants(bridgeConstants)
            .withProvider(bridgeStorageProvider)
            .withActivations(activationConfig)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        Federation activeFederation = P2shP2wshErpFederationBuilder.builder().build();
        federationStorageProvider.setNewFederation(activeFederation);

        Transaction updateCollectionsTx = buildUpdateCollectionsTransaction();

        // Act
        bridgeSupport.updateCollections(updateCollectionsTx);

        // Assert
        assertTrue(federationSupport.getRetiringFederation().isEmpty());
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries().isEmpty());
    }

    private Transaction buildUpdateCollectionsTransaction() {
        Transaction tx = Transaction
            .builder()
            .destination(PrecompiledContracts.BRIDGE_ADDR)
            .data(Bridge.UPDATE_COLLECTIONS.encode())
            .chainId(Constants.MAINNET_CHAIN_ID)
            .build();

        tx.sign(RskTestUtils.getEcKeyFromSeed("sender").getPrivKeyBytes());
        return tx;
    }
}

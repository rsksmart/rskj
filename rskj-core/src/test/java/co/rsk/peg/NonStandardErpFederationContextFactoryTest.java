package co.rsk.peg;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.bitcoin.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static co.rsk.peg.FederationFormatVersion.NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NonStandardErpFederationContextFactoryTest {

    ActivationConfig.ForBlock activations;
    NetworkParameters networkParameters;

    @BeforeEach
    void setUp() {
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
    }

    @Test
    void preRSKIP284_returns_builderHardcoded_testnet() {
        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        ErpFederationContext context =
            NonStandardErpFederationContextFactory.getNonStandardErpFederationContext(activations, networkParameters);

        ErpRedeemScriptBuilder builder = context.getRedeemScriptBuilder();
        Assertions.assertTrue(builder instanceof NonStandardErpRedeemScriptBuilderHardcoded);

        int version = context.getFederationFormatVersion();
        Assertions.assertEquals(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, version);
    }

    @Test
    void preRSKIP284_doesnt_return_builderHardcoded_mainnet() {
        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        ErpFederationContext context =
            NonStandardErpFederationContextFactory.getNonStandardErpFederationContext(activations, networkParameters);

        ErpRedeemScriptBuilder builder = context.getRedeemScriptBuilder();
        Assertions.assertFalse(builder instanceof NonStandardErpRedeemScriptBuilderHardcoded);

        int version = context.getFederationFormatVersion();
        Assertions.assertEquals(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, version);
    }

    @ParameterizedTest
    @MethodSource("provideNetworkParameters")
    void postRSKIP284_preRSKIP293_returns_builderWithCsvUnsignedBE(NetworkParameters networkParameters) {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        ErpFederationContext context =
            NonStandardErpFederationContextFactory.getNonStandardErpFederationContext(activations, networkParameters);

        ErpRedeemScriptBuilder builder = context.getRedeemScriptBuilder();
        Assertions.assertTrue(builder instanceof NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE);

        int version = context.getFederationFormatVersion();
        Assertions.assertEquals(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, version);
    }

    @ParameterizedTest
    @MethodSource("provideNetworkParameters")
    void postRSKIP293_returns_builder(NetworkParameters networkParameters) {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        ErpFederationContext context =
            NonStandardErpFederationContextFactory.getNonStandardErpFederationContext(activations, networkParameters);

        ErpRedeemScriptBuilder builder = context.getRedeemScriptBuilder();
        Assertions.assertTrue(builder instanceof NonStandardErpRedeemScriptBuilder);

        int version = context.getFederationFormatVersion();
        Assertions.assertEquals(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, version);
    }

    // network parameters provider
    private static Stream<NetworkParameters> provideNetworkParameters() {
        return Stream.of(NetworkParameters.ID_TESTNET, NetworkParameters.ID_MAINNET)
            .map(NetworkParameters::fromID);
    }
}

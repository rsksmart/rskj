package co.rsk.peg;

import co.rsk.bitcoinj.core.NetworkParameters;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NonStandardErpRedeemScriptBuilderFactoryTest {

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

        ErpRedeemScriptBuilder builder =
            NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(activations, networkParameters);

        Assertions.assertTrue(builder instanceof NonStandardErpRedeemScriptBuilderHardcoded);
    }

    @Test
    void preRSKIP284_doesnt_return_builderHardcoded_mainnet() {
        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        ErpRedeemScriptBuilder builder =
            NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(activations, networkParameters);

        Assertions.assertFalse(builder instanceof NonStandardErpRedeemScriptBuilderHardcoded);
    }

    @Test
    void postRSKIP284_preRSKIP293_returns_builderWithCsvUnsignedBE_testnet() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        ErpRedeemScriptBuilder builder =
            NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(activations, networkParameters);
        Assertions.assertTrue(builder instanceof NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE);
    }

    @Test
    void postRSKIP284_preRSKIP293_returns_builderWithCsvUnsignedBE_mainnet() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        ErpRedeemScriptBuilder builder =
            NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(activations, networkParameters);
        Assertions.assertTrue(builder instanceof NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE);
    }


    @Test
    void postRSKIP293_returns_builder_testnet() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        ErpRedeemScriptBuilder builder =
            NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(activations, networkParameters);
        Assertions.assertTrue(builder instanceof NonStandardErpRedeemScriptBuilder);
    }

    @Test
    void postRSKIP293_returns_builder_mainnet() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        ErpRedeemScriptBuilder builder =
            NonStandardErpRedeemScriptBuilderFactory.getNonStandardErpRedeemScriptBuilder(activations, networkParameters);
        Assertions.assertTrue(builder instanceof NonStandardErpRedeemScriptBuilder);
    }
}

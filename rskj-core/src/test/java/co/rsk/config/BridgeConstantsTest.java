package co.rsk.config;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeConstantsTest {
    private static Stream<Arguments> generatorFundsMigrationAgeSinceActivationEnd() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), false),
            Arguments.of(BridgeTestNetConstants.getInstance(), true),
            Arguments.of(BridgeRegTestConstants.getInstance(), true)
        );
    }

    @ParameterizedTest()
    @MethodSource("generatorFundsMigrationAgeSinceActivationEnd")
    void test_getFundsMigrationAgeSinceActivationEnd(BridgeConstants bridgeConstants, boolean hasSameValueForBothFields) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        // Act
        long fundsMigrationAgeSinceActivationEnd = bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, bridgeConstants.fundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothFields,  fundsMigrationAgeSinceActivationEnd == bridgeConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }

    @ParameterizedTest()
    @MethodSource("generatorFundsMigrationAgeSinceActivationEnd")
    void test_getFundsMigrationAgeSinceActivationEnd_post_RSKIP357(BridgeConstants bridgeConstants, boolean hasSameValueForBothMigrationAges) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(true);

        // Act
        long fundsMigrationAgeSinceActivationEnd = bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, bridgeConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothMigrationAges,  fundsMigrationAgeSinceActivationEnd == bridgeConstants.fundsMigrationAgeSinceActivationEnd);
    }

    @ParameterizedTest()
    @MethodSource("generatorFundsMigrationAgeSinceActivationEnd")
    void test_getFundsMigrationAgeSinceActivationEnd_post_RSKIP374(BridgeConstants bridgeConstants, boolean hasSameValueForBothMigrationAges) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP374)).thenReturn(true);

        // Act
        long fundsMigrationAgeSinceActivationEnd = bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, bridgeConstants.fundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothMigrationAges,  fundsMigrationAgeSinceActivationEnd == bridgeConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }

    private static Stream<Arguments> generatorFederationActivationAge() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), false),
            Arguments.of(BridgeTestNetConstants.getInstance(), true),
            Arguments.of(BridgeRegTestConstants.getInstance(), true)
        );
    }

    @ParameterizedTest()
    @MethodSource("generatorFederationActivationAge")
    void test_getFederationActivationAge(BridgeConstants bridgeConstants, boolean fieldsHasSameValue) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        // Act
        long federationActivationAge = bridgeConstants.getFederationActivationAge(activations);

        // assert
        assertEquals(federationActivationAge, bridgeConstants.federationActivationAgeLegacy);
        assertEquals(fieldsHasSameValue,  federationActivationAge == bridgeConstants.federationActivationAge);
    }

    @ParameterizedTest()
    @MethodSource("generatorFederationActivationAge")
    void test_getFederationActivationAge_post_RSKIP383(BridgeConstants bridgeConstants, boolean fieldsHasSameValue) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP383)).thenReturn(true);

        // Act
        long federationActivationAge = bridgeConstants.getFederationActivationAge(activations);

        // assert
        assertEquals(federationActivationAge, bridgeConstants.federationActivationAge);
        assertEquals(fieldsHasSameValue,  federationActivationAge == bridgeConstants.federationActivationAgeLegacy);
    }
}

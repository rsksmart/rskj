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

    private static Stream<Arguments> generator() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), false),
            Arguments.of(BridgeTestNetConstants.getInstance(), true),
            Arguments.of(BridgeRegTestConstants.getInstance(), true)
        );
    }

    @ParameterizedTest()
    @MethodSource("generator")
    void test_getFundsMigrationAgeSinceActivationEnd(BridgeConstants bridgeConstants, boolean hasSameValueForBothMigrationAges) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        // Act
        long fundsMigrationAgeSinceActivationEnd = bridgeConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, bridgeConstants.fundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothMigrationAges,  fundsMigrationAgeSinceActivationEnd == bridgeConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }

    @ParameterizedTest()
    @MethodSource("generator")
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
    @MethodSource("generator")
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
}

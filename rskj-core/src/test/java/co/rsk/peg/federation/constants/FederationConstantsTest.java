package co.rsk.peg.federation.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.stream.Stream;

import co.rsk.peg.constants.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FederationConstantsTest {
    private static Stream<Arguments> fundsMigrationAgeSinceActivationEndArgsProvider() {
        return Stream.of(
            Arguments.of(FederationMainNetConstants.getInstance(), false),
            Arguments.of(FederationTestNetConstants.getInstance(), true),
            Arguments.of((new BridgeRegTestConstants()).getFederationConstants(), true)
        );
    }

    @ParameterizedTest()
    @MethodSource("fundsMigrationAgeSinceActivationEndArgsProvider")
    void test_getFundsMigrationAgeSinceActivationEnd(FederationConstants federationConstants, boolean hasSameValueForBothFields) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        // Act
        long fundsMigrationAgeSinceActivationEnd = federationConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, federationConstants.fundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothFields,  fundsMigrationAgeSinceActivationEnd == federationConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }

    @ParameterizedTest()
    @MethodSource("fundsMigrationAgeSinceActivationEndArgsProvider")
    void test_getFundsMigrationAgeSinceActivationEnd_post_RSKIP357(FederationConstants federationConstants, boolean hasSameValueForBothMigrationAges) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(true);

        // Act
        long fundsMigrationAgeSinceActivationEnd = federationConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, federationConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothMigrationAges,  fundsMigrationAgeSinceActivationEnd == federationConstants.fundsMigrationAgeSinceActivationEnd);
    }

    @ParameterizedTest()
    @MethodSource("fundsMigrationAgeSinceActivationEndArgsProvider")
    void test_getFundsMigrationAgeSinceActivationEnd_post_RSKIP374(FederationConstants federationConstants, boolean hasSameValueForBothMigrationAges) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP374)).thenReturn(true);

        // Act
        long fundsMigrationAgeSinceActivationEnd = federationConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, federationConstants.fundsMigrationAgeSinceActivationEnd);
        assertEquals(hasSameValueForBothMigrationAges,  fundsMigrationAgeSinceActivationEnd == federationConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }

    private static Stream<Arguments> federationActivationAgeArgProvider() {
        return Stream.of(
            Arguments.of(FederationMainNetConstants.getInstance(), false),
            Arguments.of(FederationTestNetConstants.getInstance(), false),
            Arguments.of((new BridgeRegTestConstants()).getFederationConstants(), false),
            Arguments.of(FederationMainNetConstants.getInstance(), true),
            Arguments.of(FederationTestNetConstants.getInstance(), true),
            Arguments.of((new BridgeRegTestConstants()).getFederationConstants(), true)
        );
    }

    @ParameterizedTest()
    @MethodSource("federationActivationAgeArgProvider")
    void test_getFederationActivationAge(FederationConstants federationConstants, boolean isRSKIP383Active) {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP383)).thenReturn(isRSKIP383Active);
        // Act
        long federationActivationAge = federationConstants.getFederationActivationAge(activations);

        // assert
        if (isRSKIP383Active){
            assertEquals(federationConstants.federationActivationAge, federationActivationAge);
        } else {
            assertEquals(federationConstants.federationActivationAgeLegacy, federationActivationAge);
        }
    }

    private static Stream<Arguments> getGenesisFederationCreationTimeTestProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), Instant.ofEpochMilli(1514948400L)),
            Arguments.of(BridgeTestNetConstants.getInstance(), Instant.ofEpochMilli(1538967600L)),
            Arguments.of(new BridgeRegTestConstants(), Instant.ofEpochSecond(1451606400L)),
            Arguments.of(BridgeDevNetConstants.getInstance(), Instant.ofEpochMilli(1510617600L))
        );
    }

    @ParameterizedTest
    @MethodSource("getGenesisFederationCreationTimeTestProvider")
    void getGenesisFederationCreationTimeTest(FederationConstants federationConstants, Instant expectedGenesisFederationCreationTime){
        Instant actualGenesisFederationCreationTime = federationConstants.getGenesisFederationCreationTime();
        assertEquals(expectedGenesisFederationCreationTime, actualGenesisFederationCreationTime);
    }
}

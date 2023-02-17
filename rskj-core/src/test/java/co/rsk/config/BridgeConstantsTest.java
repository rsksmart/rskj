package co.rsk.config;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeConstantsTest {

    @Test
    void test_getFundsMigrationAgeSinceActivationEnd() {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP374)).thenReturn(true);

        // Act
        BridgeMainNetConstants regTestConstants = BridgeMainNetConstants.getInstance();
        long fundsMigrationAgeSinceActivationEnd = regTestConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, regTestConstants.fundsMigrationAgeSinceActivationEnd);
        assertNotEquals(fundsMigrationAgeSinceActivationEnd, regTestConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }

    @Test
    void test_getFundsMigrationAgeSinceActivationEnd_post_RSKIP357() {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(true);

        // Act
        BridgeMainNetConstants regTestConstants = BridgeMainNetConstants.getInstance();
        long fundsMigrationAgeSinceActivationEnd = regTestConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertNotEquals(fundsMigrationAgeSinceActivationEnd, regTestConstants.fundsMigrationAgeSinceActivationEnd);
        assertEquals(fundsMigrationAgeSinceActivationEnd, regTestConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }

    @Test
    void test_getFundsMigrationAgeSinceActivationEnd_post_RSKIP374() {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP357)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP374)).thenReturn(true);

        // Act
        BridgeMainNetConstants regTestConstants = BridgeMainNetConstants.getInstance();
        long fundsMigrationAgeSinceActivationEnd = regTestConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        // assert
        assertEquals(fundsMigrationAgeSinceActivationEnd, regTestConstants.fundsMigrationAgeSinceActivationEnd);
        assertNotEquals(fundsMigrationAgeSinceActivationEnd, regTestConstants.specialCaseFundsMigrationAgeSinceActivationEnd);
    }
}
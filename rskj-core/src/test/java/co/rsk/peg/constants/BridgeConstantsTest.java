package co.rsk.peg.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.lockingcap.constants.LockingCapConstants;
import co.rsk.peg.lockingcap.constants.LockingCapMainNetConstants;
import co.rsk.peg.lockingcap.constants.LockingCapRegTestConstants;
import co.rsk.peg.lockingcap.constants.LockingCapTestNetConstants;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BridgeConstantsTest {
    private static Stream<Arguments> minimumPeginTxValueArgProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), false),
            Arguments.of(BridgeTestNetConstants.getInstance(), false),
            Arguments.of(new BridgeRegTestConstants(), false),
            Arguments.of(BridgeMainNetConstants.getInstance(), true),
            Arguments.of(BridgeTestNetConstants.getInstance(), true),
            Arguments.of(new BridgeRegTestConstants(), true)
        );
    }

    @ParameterizedTest()
    @MethodSource("minimumPeginTxValueArgProvider")
    void test_getMinimumPeginTxValue(BridgeConstants bridgeConstants, boolean isRSKIP219Active){
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(isRSKIP219Active);
        // Act
        Coin minimumPeginTxValue = bridgeConstants.getMinimumPeginTxValue(activations);

        // assert
        if (isRSKIP219Active){
            assertEquals(bridgeConstants.minimumPeginTxValue, minimumPeginTxValue);
        } else {
            assertEquals(bridgeConstants.legacyMinimumPeginTxValue, minimumPeginTxValue);
        }
    }

    private static Stream<Arguments> getBtcHeightWhenPegoutTxIndexActivatesArgProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), 837589),
            Arguments.of(BridgeTestNetConstants.getInstance(), 2589553),
            Arguments.of(new BridgeRegTestConstants(), 250)
        );
    }

    @ParameterizedTest()
    @MethodSource("getBtcHeightWhenPegoutTxIndexActivatesArgProvider")
    void test_getBtcHeightWhenPegoutTxIndexActivates(BridgeConstants bridgeConstants, int expectedValue){
        // Act
        int btcHeightWhenPegoutTxIndexActivates = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates();

        // assert
        assertEquals(expectedValue, btcHeightWhenPegoutTxIndexActivates);
    }

    private static Stream<Arguments> getPegoutTxIndexGracePeriodInBtcBlocksArgProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), 4_320),
            Arguments.of(BridgeTestNetConstants.getInstance(), 1_440),
            Arguments.of(new BridgeRegTestConstants(), 100)
        );
    }

    @ParameterizedTest()
    @MethodSource("getPegoutTxIndexGracePeriodInBtcBlocksArgProvider")
    void getPegoutTxIndexGracePeriodInBtcBlocks(BridgeConstants bridgeConstants, int expectedValue){
        // Act
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();

        // assert
        assertEquals(expectedValue, pegoutTxIndexGracePeriodInBtcBlocks);
    }

    @ParameterizedTest()
    @MethodSource("getLockingCapConstantsProvider")
    void getLockingCapConstants(BridgeConstants bridgeConstants, LockingCapConstants expectedValue){
        // Act
        LockingCapConstants actualLockingCapConstants = bridgeConstants.getLockingCapConstants();

        // Assert
        assertEquals(expectedValue, actualLockingCapConstants);
        assertInstanceOf(expectedValue.getClass(), actualLockingCapConstants);
    }

    private static Stream<Arguments> getLockingCapConstantsProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), LockingCapMainNetConstants.getInstance()),
            Arguments.of(BridgeTestNetConstants.getInstance(), LockingCapTestNetConstants.getInstance()),
            Arguments.of(new BridgeRegTestConstants(), LockingCapRegTestConstants.getInstance())
        );
    }
}

package co.rsk.peg.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.federation.constants.*;
import co.rsk.peg.feeperkb.constants.*;
import co.rsk.peg.lockingcap.constants.*;
import co.rsk.peg.whitelist.constants.*;
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
    void getMinimumPeginTxValue(BridgeConstants bridgeConstants, boolean isRSKIP219Active){
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
    void getBtcHeightWhenPegoutTxIndexActivates(BridgeConstants bridgeConstants, int expectedValue){
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
    @MethodSource("getFeePerKbConstantsProvider")
    void getFeePerKbConstants(BridgeConstants bridgeConstants, FeePerKbConstants expectedValue) {
        // Act
        FeePerKbConstants actualFeePerKbConstants = bridgeConstants.getFeePerKbConstants();

        // Assert
        assertInstanceOf(expectedValue.getClass(), actualFeePerKbConstants);
    }

    private static Stream<Arguments> getFeePerKbConstantsProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), FeePerKbMainNetConstants.getInstance()),
            Arguments.of(BridgeTestNetConstants.getInstance(), FeePerKbTestNetConstants.getInstance()),
            Arguments.of(new BridgeRegTestConstants(), FeePerKbRegTestConstants.getInstance())
        );
    }

    @ParameterizedTest()
    @MethodSource("getWhitelistConstantsProvider")
    void getWhitelistConstants(BridgeConstants bridgeConstants, WhitelistConstants expectedValue) {
        // Act
        WhitelistConstants actualWhitelistConstants = bridgeConstants.getWhitelistConstants();

        // Assert
        assertInstanceOf(expectedValue.getClass(), actualWhitelistConstants);
    }

    private static Stream<Arguments> getWhitelistConstantsProvider() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), WhitelistMainNetConstants.getInstance()),
            Arguments.of(BridgeTestNetConstants.getInstance(), WhitelistTestNetConstants.getInstance()),
            Arguments.of(new BridgeRegTestConstants(), WhitelistRegTestConstants.getInstance())
        );
    }

    @ParameterizedTest()
    @MethodSource("getFederationConstantsProvider")
    void getFederationConstants(BridgeConstants bridgeConstants, FederationConstants expectedValue) {
        // Act
        FederationConstants actualFederationConstants = bridgeConstants.getFederationConstants();

        // Assert
        assertInstanceOf(expectedValue.getClass(), actualFederationConstants);
    }

    private static Stream<Arguments> getFederationConstantsProvider() {
        BridgeConstants bridgeRegTestConstants = new BridgeRegTestConstants();
        FederationConstants federationRegTestConstants = bridgeRegTestConstants.getFederationConstants();
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance(), FederationMainNetConstants.getInstance()),
            Arguments.of(BridgeTestNetConstants.getInstance(), FederationTestNetConstants.getInstance()),
            Arguments.of(bridgeRegTestConstants, new FederationRegTestConstants(federationRegTestConstants.getGenesisFederationPublicKeys()))
        );
    }

    @ParameterizedTest()
    @MethodSource("getLockingCapConstantsProvider")
    void getLockingCapConstants(BridgeConstants bridgeConstants, LockingCapConstants expectedValue){
        // Act
        LockingCapConstants actualLockingCapConstants = bridgeConstants.getLockingCapConstants();

        // Assert
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

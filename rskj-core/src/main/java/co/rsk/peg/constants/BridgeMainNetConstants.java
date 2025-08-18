package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.feeperkb.constants.FeePerKbMainNetConstants;
import co.rsk.peg.lockingcap.constants.LockingCapMainNetConstants;
import co.rsk.peg.union.constants.UnionBridgeMainNetConstants;
import co.rsk.peg.whitelist.constants.WhitelistMainNetConstants;

public class BridgeMainNetConstants extends BridgeConstants {
    private static final BridgeMainNetConstants instance = new BridgeMainNetConstants();

    private BridgeMainNetConstants() {
        btcParamsString = NetworkParameters.ID_MAINNET;
        feePerKbConstants = FeePerKbMainNetConstants.getInstance();
        whitelistConstants = WhitelistMainNetConstants.getInstance();
        federationConstants = FederationMainNetConstants.getInstance();
        lockingCapConstants = LockingCapMainNetConstants.getInstance();
        unionBridgeConstants = UnionBridgeMainNetConstants.getInstance();

        btc2RskMinimumAcceptableConfirmations = 5;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 10;
        rsk2BtcMinimumAcceptableConfirmations = 10;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValue = Coin.valueOf(1_000_000);
        legacyMinimumPegoutTxValue = Coin.valueOf(800_000);
        minimumPeginTxValue = Coin.valueOf(500_000);
        minimumPegoutTxValue = Coin.valueOf(400_000);

        btcHeightWhenBlockIndexActivates = 696_022;
        maxDepthToSearchBlocksBelowIndexActivation = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes in seconds
        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        maxInputsPerPegoutTransaction = 50;

        numberOfBlocksBetweenPegouts = 60; // 30 minutes of RSK blocks (considering 1 block every 30 seconds)

        btcHeightWhenPegoutTxIndexActivates = 837_589; // Estimated date Wed, 03 Apr 2024 15:00:00 GMT. 832,430 was the block number at time of calculation
        pegoutTxIndexGracePeriodInBtcBlocks = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)

        blockWithTooMuchChainWorkHeight = 849_138;
    }

    public static BridgeMainNetConstants getInstance() {
        return instance;
    }
}

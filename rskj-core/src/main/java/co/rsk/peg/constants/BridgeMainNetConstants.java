package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.feeperkb.constants.FeePerKbMainNetConstants;
import co.rsk.peg.lockingcap.constants.LockingCapMainNetConstants;
import co.rsk.peg.pegout.HistoricalPegoutSelectionsMainNetConstants;
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
        historicalPegoutSelectionsConstants = HistoricalPegoutSelectionsMainNetConstants.getInstance();
        unionBridgeConstants = UnionBridgeMainNetConstants.getInstance();

        updateBridgeExecutionPeriod = THREE_MINUTES_IN_MILLIS;
        minSecondsBetweenCallsReceiveHeader = FIVE_MINUTES_IN_SECONDS;

        btc2RskMinimumAcceptableConfirmations = 100;
        rsk2BtcMinimumAcceptableConfirmations = 4000;

        legacyMinimumPeginTxValue = Coin.valueOf(1_000_000);
        legacyMinimumPegoutTxValue = Coin.valueOf(800_000);
        minimumPeginTxValue = Coin.valueOf(500_000);
        minimumPegoutTxValue = Coin.valueOf(400_000);

        legacyMaxInputsPerMigrationTransaction = 50;
        maxInputsPerMigrationTransaction = 150;
        maxOutputsPerMigrationTransaction = 50;
        migrationValueForMultipleOutputs = Coin.COIN.multiply(20);

        btcHeightWhenBlockIndexActivates = 696_022;
        maxDepthToSearchBlocksBelowIndexActivation = THIRTY_DAYS_IN_BTC_BLOCKS;

        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        numberOfBlocksBetweenPegouts = THREE_HOURS_IN_RSK_BLOCKS;

        btcHeightWhenPegoutTxIndexActivates = 837_589; // Estimated date Wed, 03 Apr 2024 15:00:00 GMT. 832,430 was the block number at time of calculation
        pegoutTxIndexGracePeriodInBtcBlocks = THIRTY_DAYS_IN_BTC_BLOCKS;
        // TODO: estimate the BTC height for the RSKIP643 activation date once it is scheduled
        btcHeightWhenFederationsPendingBtcUTXOsActivates = Integer.MAX_VALUE;

        blockWithTooMuchChainWorkHeight = 849_138;
    }

    public static BridgeMainNetConstants getInstance() {
        return instance;
    }
}

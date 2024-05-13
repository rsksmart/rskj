package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.feeperkb.constants.FeePerKbMainNetConstants;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class BridgeMainNetConstants extends BridgeConstants {
    private static final BridgeMainNetConstants instance = new BridgeMainNetConstants();

    private BridgeMainNetConstants() {
        btcParamsString = NetworkParameters.ID_MAINNET;
        feePerKbConstants = FeePerKbMainNetConstants.getInstance();
        federationConstants = FederationMainNetConstants.getInstance();

        btc2RskMinimumAcceptableConfirmations = 100;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 1000;
        rsk2BtcMinimumAcceptableConfirmations = 4000;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValue = Coin.valueOf(1_000_000);
        legacyMinimumPegoutTxValue = Coin.valueOf(800_000);
        minimumPeginTxValue = Coin.valueOf(500_000);
        minimumPegoutTxValue = Coin.valueOf(400_000);

        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "041a2449e9d63409c5a8ea3a21c4109b1a6634ee88fd57176d45ea46a59713d5e0b688313cf252128a3e49a0b2effb4b413e5a2525a6fa5894d059f815c9d9efa6"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        List<ECKey> increaseLockingCapAuthorizedKeys = Arrays.stream(new String[]{
            "0448f51638348b034995b1fd934fe14c92afde783e69f120a46ee16eb6bdc2e4f6b5e37772094c68c0dea2b1be3d96ea9651a9eebda7304914c8047f4e3e251378",
            "0484c66f75548baf93e322574adac4e4579b6a53f8d11fab640e14c90118e6983ef24b0de349a3e88f72e81e771ae1c897cef446fd7f4da71778c532aee3b6c41b",
            "04bb6435dc1ea12da843ebe213893d136c1624acd681fff82551498ae00bf28e9323164b00daf925fa75177463b8254a2aae8a1713e4d851a84ea369c193e9ce51"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        increaseLockingCapAuthorizer = new AddressBasedAuthorizer(
            increaseLockingCapAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        lockingCapIncrementsMultiplier = 2;
        initialLockingCap = Coin.COIN.multiply(300); // 300 BTC

        btcHeightWhenBlockIndexActivates = 696_022;
        maxDepthToSearchBlocksBelowIndexActivation = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes in seconds
        maxDepthBlockchainAccepted = 25;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        maxInputsPerPegoutTransaction = 50;

        numberOfBlocksBetweenPegouts = 360; // 3 hours of RSK blocks (considering 1 block every 30 seconds)

        btcHeightWhenPegoutTxIndexActivates = 837_589; // Estimated date Wed, 03 Apr 2024 15:00:00 GMT. 832,430 was the block number at time of calculation
        pegoutTxIndexGracePeriodInBtcBlocks = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)
    }

    public static BridgeMainNetConstants getInstance() {
        return instance;
    }
}

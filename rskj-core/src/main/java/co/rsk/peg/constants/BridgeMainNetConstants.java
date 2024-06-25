package co.rsk.peg.constants;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.feeperkb.constants.FeePerKbMainNetConstants;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import com.google.common.collect.Lists;
import java.time.ZonedDateTime;
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

        BtcECKey federator0PublicKey = BtcECKey.fromPublicOnly(Hex.decode("0362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a124"));
        BtcECKey federator1PublicKey = BtcECKey.fromPublicOnly(Hex.decode("03c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db"));
        BtcECKey federator2PublicKey = BtcECKey.fromPublicOnly(Hex.decode("02cd53fc53a07f211641a677d250f6de99caf620e8e77071e811a28b3bcddf0be1"));

        genesisFederationPublicKeys = Lists.newArrayList(
            federator0PublicKey, federator1PublicKey, federator2PublicKey
        );
        genesisFederationCreationTime = ZonedDateTime.parse("1970-01-18T12:49:08.400Z").toInstant();

        btc2RskMinimumAcceptableConfirmations = 5;
        btc2RskMinimumAcceptableConfirmationsOnRsk = 10;
        rsk2BtcMinimumAcceptableConfirmations = 40;

        updateBridgeExecutionPeriod = 3 * 60 * 1000; // 3 minutes

        maxBtcHeadersPerRskBlock = 500;

        legacyMinimumPeginTxValue = Coin.valueOf(1_000_000);
        legacyMinimumPegoutTxValue = Coin.valueOf(800_000);
        minimumPeginTxValue = Coin.valueOf(500_000);
        minimumPegoutTxValue = Coin.valueOf(400_000);

        List<ECKey> federationChangeAuthorizedKeys = Arrays.stream(new String[]{
            "045af2d6d0fdc81d5eeec2460f01b3dbc67f1998d25384e6be18e760046202508eb34c59de6fc7fb484f6df38a3cd7117789fea97710b465c2142e145a9edb31c0",
            "04d58edc18ea1bbddbaa0a44af4d8f20bcd2356ec3a85c1a8c54cc9719439cc69c639b366d12d2fd2de95c6fa909e0557982ee6382a770209d38c1e2806f7e6cc2",
            "0446b927069372ff2f3cafc48d9804b34675e4cf23240302468c6d92501ea5015b69c7fbaeb1a3ec299bf196b1b1ceef13c7f00f7fdb9196832ab974f4faa925dc"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        federationChangeAuthorizer = new AddressBasedAuthorizer(
            federationChangeAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.MAJORITY
        );

        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "041a2449e9d63409c5a8ea3a21c4109b1a6634ee88fd57176d45ea46a59713d5e0b688313cf252128a3e49a0b2effb4b413e5a2525a6fa5894d059f815c9d9efa6"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        federationActivationAgeLegacy = 18500L;
        federationActivationAge = 40320L;

        fundsMigrationAgeSinceActivationBegin = 0L;
        fundsMigrationAgeSinceActivationEnd = 10585L;
        specialCaseFundsMigrationAgeSinceActivationEnd = 172_800L; // 60 days, considering 1 block every 30 seconds

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

        erpFedActivationDelay = 52_560; // 1 year in BTC blocks (considering 1 block every 10 minutes)

        erpFedPubKeysList = Arrays.stream(new String[] {
            "0257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d4",
            "03c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f9",
            "03cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b3",
            "02370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80"
        }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        oldFederationAddress = "35JUi1FxabGdhygLhnNUEFG4AgvpNMgxK1";

        minSecondsBetweenCallsReceiveHeader = 300;  // 5 minutes in seconds
        maxDepthBlockchainAccepted = 2500;

        minimumPegoutValuePercentageToReceiveAfterFee = 80;

        maxInputsPerPegoutTransaction = 50;

        numberOfBlocksBetweenPegouts = 60; // 30 minutes of RSK blocks (considering 1 block every 30 seconds)

        btcHeightWhenPegoutTxIndexActivates = 837_589; // Estimated date Wed, 03 Apr 2024 15:00:00 GMT. 832,430 was the block number at time of calculation
        pegoutTxIndexGracePeriodInBtcBlocks = 4_320; // 30 days in BTC blocks (considering 1 block every 10 minutes)
    }

    public static BridgeMainNetConstants getInstance() {
        return instance;
    }
}

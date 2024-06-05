package co.rsk.peg.whitelist.constants;

import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class WhitelistRegTestConstants extends WhitelistConstants {

    private static final WhitelistRegTestConstants instance = new WhitelistRegTestConstants();

    private WhitelistRegTestConstants() {
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public static WhitelistRegTestConstants getInstance() {
        return instance;
    }
}

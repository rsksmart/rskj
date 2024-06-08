package co.rsk.peg.whitelist.constants;

import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class WhitelistRegTestConstants extends WhitelistConstants {

    private static final WhitelistRegTestConstants instance = new WhitelistRegTestConstants();

    private WhitelistRegTestConstants() {
        ECKey authorizerPublicKey = ECKey.fromPublicOnly(Hex.decode(
            "04641fb250d7ca7a1cb4f530588e978013038ec4294d084d248869dd54d98873e45c61d00ceeaeeb9e35eab19fa5fbd8f07cb8a5f0ddba26b4d4b18349c09199ad"));
        List<ECKey> lockWhitelistAuthorizedKeys = Collections.singletonList(
            authorizerPublicKey);

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public static WhitelistRegTestConstants getInstance() {
        return instance;
    }
}

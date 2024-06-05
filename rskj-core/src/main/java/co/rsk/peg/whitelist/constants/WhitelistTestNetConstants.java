package co.rsk.peg.whitelist.constants;

import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class WhitelistTestNetConstants extends WhitelistConstants {

    private static final WhitelistTestNetConstants instance = new WhitelistTestNetConstants();

    private WhitelistTestNetConstants() {
        List<ECKey> lockWhitelistAuthorizedKeys = Arrays.stream(new String[]{
            "04bf7e3bca7f7c58326382ed9c2516a8773c21f1b806984bb1c5c33bd18046502d97b28c0ea5b16433fbb2b23f14e95b36209f304841e814017f1ede1ecbdcfce3"
        }).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

        lockWhitelistChangeAuthorizer = new AddressBasedAuthorizer(
            lockWhitelistAuthorizedKeys,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public static WhitelistTestNetConstants getInstance() {
        return instance;
    }
}

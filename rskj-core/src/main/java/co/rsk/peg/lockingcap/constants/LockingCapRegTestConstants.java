package co.rsk.peg.lockingcap.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.util.Set;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class LockingCapRegTestConstants extends LockingCapConstants {

    private static final LockingCapRegTestConstants instance = new LockingCapRegTestConstants();

    private LockingCapRegTestConstants() {
        Set<RskAddress> increaseAuthorizedAddresses = Set.of(new RskAddress(ECKey.fromPublicOnly(Hex.decode(
            "04450bbaab83ec48b3cb8fbb077c950ee079733041c039a8c4f1539e5181ca1a27589eeaf0fbf430e49d2909f14c767bf6909ad6845831f683416ee12b832e36ed"
        )).getAddress()));
        increaseAuthorizer = AddressBasedAuthorizerFactory.buildOneOfManyAuthorizer(increaseAuthorizedAddresses);

        initialValue = Coin.COIN.multiply(1_000L); // 1000 BTC
        incrementsMultiplier = 2;
    }

    public static LockingCapRegTestConstants getInstance() {
        return instance;
    }
}

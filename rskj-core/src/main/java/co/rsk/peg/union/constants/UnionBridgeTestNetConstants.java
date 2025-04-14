package co.rsk.peg.union.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class UnionBridgeTestNetConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeTestNetConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeTestNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        // TODO: Replace with actual address
        unionBridgeAddress = new RskAddress("5988645d30cd01e4b3bc2c02cb3909dec991ae31");

        // TODO: Replace with actual initial value and increments multiplier
        initialLockingCap = Coin.COIN.multiply(400L); // 400 BTC
        lockingCapIncrementsMultiplier = 3;

        // TODO: Replace with actual authorizers
        List<ECKey> authorizers = Collections.singletonList(
            // seed: unionBridgeAuthorizer
            ECKey.fromPublicOnly(Hex.decode(
                "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"))
        );
        changeAuthorizer = new AddressBasedAuthorizer(
            authorizers,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}

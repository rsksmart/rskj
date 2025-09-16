package co.rsk.peg.union.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.vote.AddressBasedAuthorizer.MinimumRequiredCalculation;
import co.rsk.peg.vote.AddressBasedAuthorizerFactory;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class UnionBridgeMainNetConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeMainNetConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeMainNetConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

        // TODO: Replace with actual address
        unionBridgeAddress = new RskAddress("0000000000000000000000000000000000000000");

        // TODO: Replace with actual initial locking cap value and increments multiplier
        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(300)); // 300 rbtc
        lockingCapIncrementsMultiplier = 2;

        changeUnionBridgeContractAddressAuthorizer = new AddressBasedAuthorizer(
            Collections.emptyList(),
            MinimumRequiredCalculation.ONE
        );

        // TODO: Replace with actual authorizers
        changeLockingCapAuthorizer = AddressBasedAuthorizerFactory.fromAddress(
            new RskAddress("0000000000000000000000000000000000000000")
        );

        // TODO: Replace with actual authorizers
        changeTransferPermissionsAuthorizer = AddressBasedAuthorizerFactory.fromAddress(
            new RskAddress("0000000000000000000000000000000000000000")
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}

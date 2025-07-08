package co.rsk.peg.union.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.math.BigInteger;
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

        // TODO: Replace with actual authorizers
        List<ECKey> changeUnionBridgeContractAddressAuthorizers = Stream.of(
            "04bd1d5747ca6564ed860df015c1a8779a35ef2a9f184b6f5390bccb51a3dcace02f88a401778be6c8fd8ed61e4d4f1f508075b3394eb6ac0251d4ed6d06ce644d"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();

        changeUnionBridgeContractAddressAuthorizer = new AddressBasedAuthorizer(
            changeUnionBridgeContractAddressAuthorizers,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        // TODO: Replace with actual authorizers
        List<ECKey> changeLockingCapAuthorizers = Stream.of(
            "040162aff21e78665eabe736746ed86ca613f9e628289438697cf820ed8ac800e5fe8cbca350f8cf0b3ee4ec3d8c3edec93820d889565d4ae9b4f6e6d012acec09"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();
        changeLockingCapAuthorizer = new AddressBasedAuthorizer(
            changeLockingCapAuthorizers,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );

        // TODO: Replace with actual authorizers
        List<ECKey> changeTransferPermissionsAuthorizers = Stream.of(
            "0458fdbe66a1eda5b94eaf3b3ef1bc8439a05a0b13d2bb9d5a1c6ea1d98ed5b0405fd002c884eed4aa1102d812c7347acc6dd172ad4828de542e156bd47cd90282"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();
        changeTransferPermissionsAuthorizer = new AddressBasedAuthorizer(
            changeTransferPermissionsAuthorizers,
            AddressBasedAuthorizer.MinimumRequiredCalculation.ONE
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}

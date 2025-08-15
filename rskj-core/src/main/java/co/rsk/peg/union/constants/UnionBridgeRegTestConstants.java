package co.rsk.peg.union.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.vote.AddressBasedAuthorizer.MinimumRequiredCalculation;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;

public class UnionBridgeRegTestConstants extends UnionBridgeConstants {

    private static final UnionBridgeConstants instance = new UnionBridgeRegTestConstants();

    // Private constructor to prevent instantiation
    private UnionBridgeRegTestConstants() {
        btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        // TODO: Replace with actual address
        unionBridgeAddress = new RskAddress("5988645d30cd01e4b3bc2c02cb3909dec991ae31");

        // TODO: Replace with actual initial value and increments multiplier
        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(500)); // 500 rbtc
        lockingCapIncrementsMultiplier = 4;

        // TODO: Replace with actual authorizers
        // seed: unionBridgeAuthorizer
        ECKey changeUnionBridgeContractAddressAuthorizers = ECKey.fromPublicOnly(Hex.decode("041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a"));
        changeUnionBridgeContractAddressAuthorizer = new AddressBasedAuthorizer(
            Collections.singletonList(changeUnionBridgeContractAddressAuthorizers),
            MinimumRequiredCalculation.ONE
        );

        // TODO: Replace with actual authorizers
        // seed: changeLockingCapAuthorizer, changeLockingCapAuthorizer2, changeLockingCapAuthorizer3
        List<ECKey> changeLockingCapAuthorizers = Stream.of(
            "049929eb3c107a65108830f4c221068f42301bd8b054f91bd594944e7fb488fd1c93a8921fb28d3494769598eb271cd2834a31c5bd08fa075170b3da804db00a5b",
            "04c8a5827bfadd2bce6fa782e6c48dd61503d38c86e29381781167cd6371eb56f50bc03c9e9c265ea7e07709b964e0b4b0f3d416955225fcb9202e6763ddd5ca91",
            "0442329d63de5ec5b2f285da7e2f3eb484db3ee5e39066579244211021b81c32d7061922075e2272a8e8a633a5856071eef7e7f800b3d93c9acee91e0f0f37ac2f"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();

        changeLockingCapAuthorizer = new AddressBasedAuthorizer(
            changeLockingCapAuthorizers,
            MinimumRequiredCalculation.MAJORITY
        );

        // TODO: Replace with actual authorizers
        // seed: changeTransferPermissionsAuthorizer1, changeTransferPermissionsAuthorizer2, changeTransferPermissionsAuthorizer3
        List<ECKey> changeTransferPermissionsAuthorizers = Stream.of(
            "04397d368991eac9bbc7593ea25f99d595dc175f598afc6297dbfd4533d26577ce0b3faa9296bdecd4ef7a58738f6b23b7a71a11753ebe417c660eaa7e36f64fd3",
            "04edde251d0e8a91e118ebc4f6ea1c91a21bd1963a383d77751626a0a3a7685ec4bced2751f9c4bd914252d21799297dab159f45b870bbbccf54ed6f3eb5ad504c",
            "049a630a1bef586e5095cf2a877bc2719840f71791bf9337ef35db36c9c4234cb0a5c3809013a5c4d01cdc0d003944ae21f42caae8d0925ca75dc444990749c688"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();

        changeTransferPermissionsAuthorizer = new AddressBasedAuthorizer(
            changeTransferPermissionsAuthorizers,
            MinimumRequiredCalculation.MAJORITY
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}

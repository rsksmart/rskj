package co.rsk.peg.union.constants;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.vote.AddressBasedAuthorizer.MinimumRequiredCalculation;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;
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
        BigInteger oneRbtc = BigInteger.TEN.pow(18); // 1 RBTC = 1000000000000000000 wei
        initialLockingCap = new Coin(oneRbtc).multiply(BigInteger.valueOf(400)); // 400 rbtc
        lockingCapIncrementsMultiplier = 3;

        // TODO: Replace with actual authorizers
        // seed: unionBridgeAuthorizer, unionBridgeAuthorizer2, unionBridgeAuthorizer3
        List<ECKey> changeUnionBridgeContractAddressAuthorizers = Stream.of(
            "041fb6d4b421bb14d95b6fb79823d45b777f0e8fd07fe18d0940c0c113d9667911e354d4e8c8073f198d7ae5867d86e3068caff4f6bd7bffccc6757a3d7ee8024a",
            "041768f38193655b6a4776d287b949f87e21fb3b2e3ed1581dbe7569ef641edef041ab862f4ab228566c5ae49abd478d9d92d870348304df35e23967adea3f6ded",
            "049191c1f944ac723e5807ff771144295e4d6945ef1c6c30bd68927711410e6d40ec81b07a42b03093605d03edd15608a81511faeacd1815ab9341f4f77c212865"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();

        changeUnionBridgeContractAddressAuthorizer = new AddressBasedAuthorizer(
            changeUnionBridgeContractAddressAuthorizers,
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
            MinimumRequiredCalculation.ONE
        );

        // TODO: Replace with actual authorizers
        // seed: changeTransferPermissionsAuthorizers, changeTransferPermissionsAuthorizers2, changeTransferPermissionsAuthorizers3
        List<ECKey> changeTransferPermissionsAuthorizers = Stream.of(
            "04ea24f3943dff3b9b8abc59dbdf1bd2c80ec5b61f5c2c6dfcdc189299115d6d567df34c52b7e678cc9934f4d3d5491b6e53fa41a32f58a71200396f1e11917e8f",
            "04cf42ec9eb287adc7196e8d3d2c288542b1db733681c22887e3a3e31eb98504002825ecbe0cd9b61aff3600ffd0ca4542094c75cb0bac5e93be0c7e00b2ead9ea",
            "043a7510e39f8c406fb682c20d0e74e6f18f6ec6cb4bc9718a3c47f9bda741f3333ed39e9854b9ad89f16fccb52453975ff1039dd913addfa6a6c56bcacbd92ff9"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();

        changeTransferPermissionsAuthorizer = new AddressBasedAuthorizer(
            changeTransferPermissionsAuthorizers,
            MinimumRequiredCalculation.ONE
        );
    }

    public static UnionBridgeConstants getInstance() {
        return instance;
    }
}

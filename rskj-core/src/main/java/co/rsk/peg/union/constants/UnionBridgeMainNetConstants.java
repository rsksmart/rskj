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
            "04bd1d5747ca6564ed860df015c1a8779a35ef2a9f184b6f5390bccb51a3dcace02f88a401778be6c8fd8ed61e4d4f1f508075b3394eb6ac0251d4ed6d06ce644d",
            "040a595228043ec16d64849b8cb82d0fad281480ca01cb9e33da89aeefb180415314f05c83e0b29c51e06465588f724367993550bad1967c929e0a43465746e2d6",
            "049367a75caac20bb0a0a8bae7a5076e2ba4a6cd3f2f963b3f007b7ffde716c93cfcde1b7f7eb01c978b76b9e94556c8d9f2f44ab723ec4ec1e1dd9559f6f927a2"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();

        changeUnionBridgeContractAddressAuthorizer = new AddressBasedAuthorizer(
            changeUnionBridgeContractAddressAuthorizers,
            MinimumRequiredCalculation.MAJORITY
        );

        // TODO: Replace with actual authorizers
        List<ECKey> changeLockingCapAuthorizers = Stream.of(
            "040162aff21e78665eabe736746ed86ca613f9e628289438697cf820ed8ac800e5fe8cbca350f8cf0b3ee4ec3d8c3edec93820d889565d4ae9b4f6e6d012acec09",
            "04ee99364235a33edbd177c0293bd3e13f1c85b2ee6197e66aa7e975fb91183b08b30bf1227468980180e10092decaaeed0ae1c4bcf29d17993569bb3c1b274f83",
            "0462096357f02602ce227580aa36672a5cba4fc461918c9e1697992ee2895dbb6c1fd2be4859135b3be0e42a20b466738aa03f3871390108746d2ecbe7ffb7aad9"
        ).map(hex -> ECKey.fromPublicOnly(Hex.decode(hex))).toList();
        changeLockingCapAuthorizer = new AddressBasedAuthorizer(
            changeLockingCapAuthorizers,
            MinimumRequiredCalculation.MAJORITY
        );

        // TODO: Replace with actual authorizers
        List<ECKey> changeTransferPermissionsAuthorizers = Stream.of(
            "0458fdbe66a1eda5b94eaf3b3ef1bc8439a05a0b13d2bb9d5a1c6ea1d98ed5b0405fd002c884eed4aa1102d812c7347acc6dd172ad4828de542e156bd47cd90282",
            "0486559d73a991df9e5eef1782c41959ecc7e334ef57ddcb6e4ebc500771a50f0c3b889afb9917165db383a9bf9a8e9b4f73abd542109ba06387f016f62df41b0f",
            "048817254d54e9776964e6e1102591474043dd3dae11088919ef6cb37625a66852627b146986cbc3b188ce69ca86468fa11b275a6577e0c7d3a3c6c1b343537e3f"
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

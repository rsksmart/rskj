package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.peg.utils.MerkleTreeUtils;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

public class CoinbaseInformationTest {

    @Test
    public void getWitnessMerkleRoot() {
        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        Sha256Hash witnessRoot = MerkleTreeUtils.combineLeftRight(Sha256Hash.ZERO_HASH, secondHashTx);
        CoinbaseInformation instance = new CoinbaseInformation(witnessRoot);
        Sha256Hash expectedHash = new Sha256Hash(Hex.decode("613cb22535df8d9443fe94b66d807cd60312f982e305e25e825b00e6f429799f"));

        Assert.assertEquals(expectedHash, instance.getWitnessMerkleRoot());
    }
}

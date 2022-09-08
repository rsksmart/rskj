package org.ethereum.validator;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.peg.utils.MerkleTreeUtils;
import co.rsk.validators.Rskip92MerkleProofValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class Rskip92MerkleProofValidatorTests {

    private static final int MAX_MERKLE_PROOF_SIZE = 960;

    @Test
    public void isValid_PassValidHashes_ShouldReturnTrue() {
        List<byte[]> hashList = makeHashList();
        byte[] pmtSerialized = join(hashList);
        Sha256Hash coinbaseHash = Sha256Hash.wrap(hashList.get(0));
        Rskip92MerkleProofValidator rskip92MerkleProofValidator = new Rskip92MerkleProofValidator(pmtSerialized, true);
        Sha256Hash rootHash = hashList.stream().map(Sha256Hash::wrap).reduce(coinbaseHash, MerkleTreeUtils::combineLeftRight);

        boolean actualResult = rskip92MerkleProofValidator.isValid(rootHash, coinbaseHash);

        assertTrue(actualResult);
    }

    @Test
    public void isValid_PassInvalidCoinbaseHash_ShouldReturnFalse() {
        List<byte[]> hashList = makeHashList();
        byte[] pmtSerialized = join(hashList);
        Sha256Hash coinbaseHash = Sha256Hash.wrap(hashList.get(0));
        Rskip92MerkleProofValidator rskip92MerkleProofValidator = new Rskip92MerkleProofValidator(pmtSerialized, true);
        Sha256Hash rootHash = hashList.stream().map(Sha256Hash::wrap).reduce(coinbaseHash, MerkleTreeUtils::combineLeftRight);

        boolean actualResult = rskip92MerkleProofValidator.isValid(rootHash, Sha256Hash.wrap(new byte[Sha256Hash.LENGTH]));

        assertFalse(actualResult);
    }

    @Test
    public void isValid_PassInvalidRootHash_ShouldReturnFalse() {
        List<byte[]> hashList = makeHashList();
        byte[] pmtSerialized = join(hashList);
        Sha256Hash coinbaseHash = Sha256Hash.wrap(hashList.get(0));
        Rskip92MerkleProofValidator rskip92MerkleProofValidator = new Rskip92MerkleProofValidator(pmtSerialized, true);
        Sha256Hash rootHash = Sha256Hash.wrap(new byte[Sha256Hash.LENGTH]);

        boolean actualResult = rskip92MerkleProofValidator.isValid(rootHash, coinbaseHash);

        assertFalse(actualResult);
    }

    @Test
    public void createInstance_PassNullProofAndRskip180Enabled_ShouldThrowIllegalArgumentError() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Rskip92MerkleProofValidator(null, true));
    }

    @Test
    public void createInstance_PassNullProofAndRskip180Disabled_ShouldThrowNullPointerError() {
        Assertions.assertThrows(NullPointerException.class, () -> new Rskip92MerkleProofValidator(null, false));
    }

    @Test
    public void createInstance_PassLargeProofAndRskip180Enabled_ShouldThrowError() {
        byte[] pmtSerialized = new byte[MAX_MERKLE_PROOF_SIZE + 1];
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Rskip92MerkleProofValidator(pmtSerialized, true));
    }

    @Test
    public void createInstance_PassLargeMalformedProofAndRskip180Disabled_ShouldThrowError() {
        byte[] pmtSerialized = new byte[MAX_MERKLE_PROOF_SIZE + 1];
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Rskip92MerkleProofValidator(pmtSerialized, false));
    }

    @Test
    public void createInstance_PassLargeProofAndRskip180Disabled_ShouldNotThrowError() {
        byte[] pmtSerialized = new byte[MAX_MERKLE_PROOF_SIZE + Sha256Hash.LENGTH];
        Rskip92MerkleProofValidator instance = new Rskip92MerkleProofValidator(pmtSerialized, false);
        assertNotNull(instance);
    }

    @Test
    public void createInstance_PassMalformedProofAndRskip180Enabled_ShouldThrowError() {
        byte[] pmtSerialized = new byte[Sha256Hash.LENGTH + 1];
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Rskip92MerkleProofValidator(pmtSerialized, true));
    }

    @Test
    public void createInstance_PassMalformedProofAndRskip180Disabled_ShouldThrowError() {
        byte[] pmtSerialized = new byte[Sha256Hash.LENGTH + 1];
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Rskip92MerkleProofValidator(pmtSerialized, false));
    }

    private static List<byte[]> makeHashList() {
        final int hashCount = 5;
        Random rand = new Random(100);
        List<byte[]> result = new ArrayList<>(hashCount);

        for (int i = 0; i < hashCount; i++) {
            byte[] hash = new byte[Sha256Hash.LENGTH];
            rand.nextBytes(hash);

            result.add(hash);
        }

        return result;
    }

    private static byte[] join(List<byte[]> hashList) {
        byte[] result = new byte[hashList.size() * Sha256Hash.LENGTH];

        for (int i = 0; i < result.length; i++) {
            result[i] = hashList.get(i / Sha256Hash.LENGTH)[i % Sha256Hash.LENGTH];
        }

        return result;
    }
}

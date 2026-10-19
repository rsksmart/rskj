package co.rsk.trie.segbuild;

import co.rsk.core.RskAddress;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class MissKeyDecodeTest {

    private static final String MISS_KEY =
        "00da41108c1d1e6cb26724" +
        "0000000000000000000000000000000001000006" +
        "00" +
        "ffd23c160771058e0100" +
        "01b1c37131c02c513ac1f14bfa7b9b38f25554dd2bc150";

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    @Test
    void decode() {
        byte[] key = hex(MISS_KEY);
        System.out.println("@@@ key length = " + key.length);

        // Candidate: the stripped subkey is a Bitcoin block hash, left-padded to 32 bytes.
        byte[] stripped = hex("01b1c37131c02c513ac1f14bfa7b9b38f25554dd2bc150");
        byte[] subkey = new byte[32];
        System.arraycopy(stripped, 0, subkey, 32 - stripped.length, stripped.length);
        System.out.println("@@@ reconstructed subkey = " + ByteUtil.toHexString(subkey));

        byte[] securePrefix = Arrays.copyOf(Keccak256Helper.keccak256(subkey), TrieKeyMapper.SECURE_KEY_SIZE);
        System.out.println("@@@ keccak256(subkey)[0:10] = " + ByteUtil.toHexString(securePrefix));
        System.out.println("@@@ key's secure prefix     = ffd23c160771058e0100");
        System.out.println("@@@ MATCH = " + ByteUtil.toHexString(securePrefix).equals("ffd23c160771058e0100"));

        // And rebuild the whole trie key the way rskj would, for the Bridge storage slot.
        TrieKeyMapper mapper = new TrieKeyMapper();
        byte[] rebuilt = mapper.getAccountStorageKey(
                new RskAddress("0000000000000000000000000000000001000006"), DataWord.valueOf(subkey));
        System.out.println("@@@ rebuilt trie key = " + ByteUtil.toHexString(rebuilt));
        System.out.println("@@@ FULL KEY MATCH = " + Arrays.equals(rebuilt, key));
    }
}

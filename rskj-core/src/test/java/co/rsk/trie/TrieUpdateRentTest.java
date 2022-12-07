package co.rsk.trie;

import co.rsk.core.RskAddress;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.TrieKeyMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrieUpdateRentTest {

    /**
     * The entire Trie is timestamped (even intermediate nodes)
     * */
    @Test
    public void updateRentTimestampBasicTest() {
        Trie trie = buildTestTrie();
        byte[] key = decode("0a008080");
        trie = trie.updateLastRentPaidTimestamp(TrieKeySlice.fromKey(key), 23);
        List<Trie> nodePath = trie.getNodes(key);

        assertEquals(4, nodePath.size());
        nodePath.stream().forEach(trieNode -> assertEquals(23, trieNode.getLastRentPaidTimestamp()));

        key = decode("0a008000");
        trie = trie.updateLastRentPaidTimestamp(TrieKeySlice.fromKey(key), 27);
        nodePath = trie.getNodes(key);

        assertEquals(4, nodePath.size());
        nodePath.stream().forEach(trieNode -> assertEquals(27, trieNode.getLastRentPaidTimestamp()));

        key = decode("0a808000");
        trie = trie.updateLastRentPaidTimestamp(TrieKeySlice.fromKey(key), 33);
        nodePath = trie.getNodes(key);

        assertEquals(4, nodePath.size());
        nodePath.stream().forEach(trieNode -> assertEquals(33, trieNode.getLastRentPaidTimestamp()));

        key = decode("0a0000");
        trie = trie.updateLastRentPaidTimestamp(TrieKeySlice.fromKey(key), 73);
        nodePath = trie.getNodes(key);

        assertEquals(3, nodePath.size());
        nodePath.stream().forEach(trieNode -> assertEquals(73, trieNode.getLastRentPaidTimestamp()));
    }

    @Test
    public void updateRentTimestampRskBasicTest() {
        TrieKeyMapper mapper = new TrieKeyMapper();
        Trie trie = new Trie();

        RskAddress addr1 = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");
        RskAddress contract = new RskAddress(HashUtil.calcNewAddr(addr1.getBytes(),
                "1".getBytes(StandardCharsets.UTF_8)));

        mapper.getAccountStoragePrefixKey(contract);

        trie = trie.put(mapper.getAccountKey(addr1), new AccountState().getEncoded());
        trie = trie.put(mapper.getAccountStoragePrefixKey(contract), new byte[] { 0x01 });
        trie = trie.put(mapper.getCodeKey(contract), "somecode".getBytes(StandardCharsets.UTF_8));

        trie = trie.updateLastRentPaidTimestamp(TrieKeySlice.fromKey(mapper.getAccountKey(addr1)), 34);

        List<Trie> nodesAccountKey = trie.getNodes(mapper.getAccountKey(addr1));
        nodesAccountKey.forEach(n -> assertEquals(34, n.getLastRentPaidTimestamp()));

        trie = trie.updateLastRentPaidTimestamp(TrieKeySlice.fromKey(mapper.getAccountStoragePrefixKey(contract)), 35);

        List<Trie> nodesAccountStoragePrefixKey = trie.getNodes(mapper.getAccountStoragePrefixKey(contract));
        nodesAccountStoragePrefixKey.forEach(n -> assertEquals(35, n.getLastRentPaidTimestamp()));

        trie = trie.updateLastRentPaidTimestamp(TrieKeySlice.fromKey(mapper.getCodeKey(contract)), 36);

        List<Trie> codeKey = trie.getNodes(mapper.getCodeKey(contract));
        codeKey.forEach(n -> assertEquals(36, n.getLastRentPaidTimestamp()));
        assertTrue(codeKey.stream().anyMatch(n -> n.getValue() == null));
    }

    /**
     * @return the following tree
     *
     *       6
     *      / \
     *     /   \
     *    /     7
     *   2       \
     *  / \       \
     * 1   \       8
     *      4     /
     *     / \   9
     *    3   5
     */
    private static Trie buildTestTrie() {
        Trie trie = new Trie();
        trie = trie.put(decode("0a"), new byte[] { 0x06 });
        trie = trie.put(decode("0a00"), new byte[] { 0x02 });
        trie = trie.put(decode("0a80"), new byte[] { 0x07 });
        trie = trie.put(decode("0a0000"), new byte[] { 0x01 });
        trie = trie.put(decode("0a0080"), new byte[] { 0x04 });
        trie = trie.put(decode("0a008000"), new byte[] { 0x03 });
        trie = trie.put(decode("0a008080"), new byte[] { 0x05 });
        trie = trie.put(decode("0a8080"), new byte[] { 0x08 });
        trie = trie.put(decode("0a808000"), new byte[] { 0x09 });

        return trie;
    }
}
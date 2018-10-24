package co.rsk.db;

/**
 * This is a glue between the Trie and the domain objects.
 * By using a trie mapper we are able to store key-value pairs in a dictionary instead of an actual trie.
 */
public interface TrieMapper {
    // Account-related

    TrieKey addressToAccountKey(Address address);

    TrieValue accountToValue(Coin value, Nonce nonce);

    Account valueToAccount(TrieValue value);


    // Code-related

    TrieKey accountToCodeKey(Account account);

    TrieValue codeToValue(EvmBytecode code);

    EvmBytecode valueToCode(TrieValue currentCode);
}

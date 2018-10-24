package co.rsk.db;

/**
 * An immutable view of the account information.
 */
public interface Account {
    // getAddress?

    Coin getBalance();

    Nonce getNonce();

    // codeHash??

    // the code and storage values are stored in a separate trie node.
    // we will want to make it explicit if we provide those values

//    // TODO @Nullable
//    EvmBytecode getCode();
//
//    org.ethereum.vm.DataWord getStorageValue(co.rsk.core.RskAddress to, org.ethereum.vm.DataWord key);
}

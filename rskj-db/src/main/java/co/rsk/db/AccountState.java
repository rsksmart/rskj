package co.rsk.db;

/**
 * An immutable view of the account information.
 */
public class AccountState {
    private final Coin balance;
    private final Nonce nonce;

    AccountState(Coin balance, Nonce nonce) {
        this.balance = balance;
        this.nonce = nonce;
    }

    public static AccountState emptyAccount() {
        return new AccountState(Coin.ZERO, Nonce.ZERO);
    }

    public Coin getBalance() {
        return balance;
    }

    public Nonce getNonce() {
        return nonce;
    }
}
    // getAddress?

    // codeHash??

    // the code and storage values are stored in a separate trie node.
    // we will want to make it explicit if we provide those values

//    // TODO @Nullable
//    EvmBytecode getCode();
//
//    org.ethereum.vm.DataWord getStorageValue(co.rsk.core.RskAddress to, org.ethereum.vm.DataWord key);

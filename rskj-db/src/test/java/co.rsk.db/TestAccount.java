package co.rsk.db;

class TestAccount implements Account {
    private final Coin balance;
    private final Nonce nonce;

    TestAccount(Coin balance, Nonce nonce) {
        this.balance = balance;
        this.nonce = nonce;
    }

    @Override
    public Coin getBalance() {
        return balance;
    }

    @Override
    public Nonce getNonce() {
        return nonce;
    }
}

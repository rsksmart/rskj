package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.db.RepositorySnapshot;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.AccountState;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Objects;

public class ClaimTransactionInfoHolder {
    @Nonnull
    private final Transaction tx;

    @Nonnull
    private final RepositorySnapshot repositorySnapshot;

    @Nonnull
    private final SignatureCache signatureCache;

    @Nonnull
    private final Constants constants;

    @Nullable
    private final AccountState accountState;

    @Nullable
    private final ActivationConfig.ForBlock activation;

    private Coin txUserBalance;

    public ClaimTransactionInfoHolder(@Nonnull Transaction tx,
                                      @Nonnull RepositorySnapshot repositorySnapshot,
                                      @Nonnull SignatureCache signatureCache,
                                      @Nonnull Constants constants,
                                      @Nullable ActivationConfig.ForBlock activation) {
        this.tx = Objects.requireNonNull(tx);
        this.repositorySnapshot = Objects.requireNonNull(repositorySnapshot);
        this.signatureCache = Objects.requireNonNull(signatureCache);
        this.constants = Objects.requireNonNull(constants);
        this.accountState = repositorySnapshot.getAccountState(signatureCache.getSender(tx));
        this.activation = activation;
    }

    @Nonnull
    private Coin getSenderBalance() {
        return accountState == null ? Coin.ZERO : accountState.getBalance();
    }

    @Nonnull
    private Coin getTxUserBalance() {
        if (txUserBalance == null) {
            txUserBalance = repositorySnapshot.getBalance(tx.getSender(signatureCache));
        }
        return txUserBalance;
    }

    public boolean accountExists() {
        return accountState != null;
    }

    @Nonnull
    public BigInteger getAccountNonce() {
        return accountState == null ? BigInteger.ZERO : accountState.getNonce();
    }

    public boolean canPayForTx() {
        Coin txCost = tx.getGasPrice().multiply(tx.getGasLimitAsInteger());

        Coin senderB = getSenderBalance();
        if (senderB.compareTo(txCost) >= 0) {
            return true;
        }

        Coin txUserB = getTxUserBalance();
        return txUserB.add(senderB).compareTo(txCost) >= 0;
    }

    @Nonnull
    public Transaction getTx() {
        return tx;
    }

    @Nonnull
    public RepositorySnapshot getRepositorySnapshot() {
        return repositorySnapshot;
    }

    @Nonnull
    public SignatureCache getSignatureCache() {
        return signatureCache;
    }

    @Nonnull
    public Constants getConstants() {
        return constants;
    }

    @Nullable
    public AccountState getAccountState() {
        return accountState;
    }

    @Nullable
    public ActivationConfig.ForBlock getActivation() {
        return activation;
    }
}

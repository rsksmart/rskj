package org.ethereum.core;

import co.rsk.db.RepositorySnapshot;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

import javax.annotation.Nullable;

public class ValidationArgs {

    @Nullable
    private final AccountState accountState;

    @Nullable
    private final RepositorySnapshot repositorySnapshot;

    @Nullable
    private final ActivationConfig.ForBlock activationConfig;

    public ValidationArgs(@Nullable AccountState accountState, @Nullable RepositorySnapshot repositorySnapshot, @Nullable ActivationConfig.ForBlock activationConfig) {
        this.accountState = accountState;
        this.repositorySnapshot = repositorySnapshot;
        this.activationConfig = activationConfig;
    }

    @Nullable
    public AccountState getAccountState() {
        return accountState;
    }

    @Nullable
    public RepositorySnapshot getRepositorySnapshot() {
        return repositorySnapshot;
    }

    @Nullable
    public ActivationConfig.ForBlock getActivationConfig() {
        return activationConfig;
    }
}

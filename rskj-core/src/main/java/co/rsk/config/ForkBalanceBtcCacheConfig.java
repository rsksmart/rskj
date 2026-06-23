/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.config;

/**
 * Configuration for the fork-balance Bitcoin block cache and bitcoind JSON-RPC client.
 */
public final class ForkBalanceBtcCacheConfig {

    public static final int DEFAULT_MAX_HEIGHTS = 12;
    public static final long DEFAULT_RPC_REQUEST_DELAY_MS = 500L;
    public static final long DEFAULT_RPC_TIMEOUT_MS = 10_000L;
    public static final int DEFAULT_RPC_RETRY_ATTEMPTS = 3;
    public static final long DEFAULT_RPC_RETRY_BACKOFF_MS = 250L;
    public static final long DEFAULT_RPC_RETRY_MAX_BUDGET_MS = 3_000L;
    public static final long DEFAULT_WORK_BUILD_RETRY_INTERVAL_MS = 1_500L;
    /** Empty by default: validating nodes do not need Bitcoin RPC. Miners must set {@code miner.forkBalance.btcRpc.url}. */
    public static final String DEFAULT_BTC_RPC_URL = "";

    private final int maxHeights;
    private final String btcRpcUrl;
    private final long rpcRequestDelayMs;
    private final long rpcTimeoutMs;
    private final int rpcRetryAttempts;
    private final long rpcRetryBackoffMs;
    private final long rpcRetryMaxBudgetMs;
    private final long workBuildRetryIntervalMs;

    public ForkBalanceBtcCacheConfig(
            int maxHeights,
            String btcRpcUrl,
            long rpcRequestDelayMs,
            long rpcTimeoutMs) {
        this(maxHeights, btcRpcUrl, rpcRequestDelayMs, rpcTimeoutMs,
                DEFAULT_RPC_RETRY_ATTEMPTS,
                DEFAULT_RPC_RETRY_BACKOFF_MS,
                DEFAULT_RPC_RETRY_MAX_BUDGET_MS,
                DEFAULT_WORK_BUILD_RETRY_INTERVAL_MS);
    }

    public ForkBalanceBtcCacheConfig(
            int maxHeights,
            String btcRpcUrl,
            long rpcRequestDelayMs,
            long rpcTimeoutMs,
            int rpcRetryAttempts,
            long rpcRetryBackoffMs,
            long rpcRetryMaxBudgetMs,
            long workBuildRetryIntervalMs) {
        if (maxHeights < 1) {
            throw new IllegalArgumentException("maxHeights must be >= 1");
        }
        this.maxHeights = maxHeights;
        this.btcRpcUrl = btcRpcUrl != null ? btcRpcUrl.trim() : "";
        this.rpcRequestDelayMs = Math.max(0, rpcRequestDelayMs);
        this.rpcTimeoutMs = rpcTimeoutMs > 0 ? rpcTimeoutMs : DEFAULT_RPC_TIMEOUT_MS;
        this.rpcRetryAttempts = Math.max(1, rpcRetryAttempts);
        this.rpcRetryBackoffMs = Math.max(0, rpcRetryBackoffMs);
        this.rpcRetryMaxBudgetMs = rpcRetryMaxBudgetMs > 0 ? rpcRetryMaxBudgetMs : DEFAULT_RPC_RETRY_MAX_BUDGET_MS;
        this.workBuildRetryIntervalMs = workBuildRetryIntervalMs > 0
                ? workBuildRetryIntervalMs
                : DEFAULT_WORK_BUILD_RETRY_INTERVAL_MS;
    }

    public int getMaxHeights() {
        return maxHeights;
    }

    /** Bitcoin Core JSON-RPC endpoint (empty for validating nodes). */
    public String getBtcRpcUrl() {
        return btcRpcUrl;
    }

    public boolean isBtcRpcEnabled() {
        return !btcRpcUrl.isEmpty();
    }

    public long getRpcRequestDelayMs() {
        return rpcRequestDelayMs;
    }

    public long getRpcTimeoutMs() {
        return rpcTimeoutMs;
    }

    public int getRpcRetryAttempts() {
        return rpcRetryAttempts;
    }

    public long getRpcRetryBackoffMs() {
        return rpcRetryBackoffMs;
    }

    public long getRpcRetryMaxBudgetMs() {
        return rpcRetryMaxBudgetMs;
    }

    public long getWorkBuildRetryIntervalMs() {
        return workBuildRetryIntervalMs;
    }
}

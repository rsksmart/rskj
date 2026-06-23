/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.mine;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Bitcoin Core JSON-RPC client for fork-balance parent block lookup on mining nodes.
 */
public final class BitcoinBlockRpcClient {

    private static final Logger logger = LoggerFactory.getLogger("minerserver");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ForkBalanceBtcCacheConfig config;
    private final HttpClient httpClient;
    private long lastRequestTimeMs;

    public BitcoinBlockRpcClient(ForkBalanceBtcCacheConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getRpcTimeoutMs()))
                .build();
    }

    public boolean isConfigured() {
        return config.isBtcRpcEnabled();
    }

    /**
     * Fetches the hash of the current Bitcoin chain tip ({@code getbestblockhash}), with bounded retries.
     */
    public Optional<Sha256Hash> fetchBestBlockHash() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        return retryOptional(this::fetchBestBlockHashOnce);
    }

    private Optional<Sha256Hash> fetchBestBlockHashOnce() {
        try {
            enforceRequestDelay();
            String payload = "{\"jsonrpc\":\"1.0\",\"id\":\"rskj-fac\",\"method\":\"getbestblockhash\",\"params\":[]}";
            JsonNode response = sendRpc(payload);
            if (response == null || response.has("error") && !response.get("error").isNull()) {
                return Optional.empty();
            }
            JsonNode result = response.get("result");
            if (result == null || !result.isTextual()) {
                return Optional.empty();
            }
            return Optional.of(Sha256Hash.wrap(Hex.decode(result.asText())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while fetching BTC best block hash via RPC");
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            logger.warn("Failed to fetch BTC best block hash via RPC: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches a full Bitcoin block and its chain height via JSON-RPC, with bounded retries.
     */
    public Optional<FetchedBtcBlock> fetchBlock(Sha256Hash blockHash, NetworkParameters params) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        return retryOptional(() -> fetchBlockOnce(blockHash, params));
    }

    private Optional<FetchedBtcBlock> fetchBlockOnce(Sha256Hash blockHash, NetworkParameters params) {
        try {
            enforceRequestDelay();
            Optional<Integer> height = fetchBlockHeight(blockHash);
            enforceRequestDelay();
            Optional<byte[]> rawBlock = fetchRawBlock(blockHash);
            if (rawBlock.isEmpty()) {
                return Optional.empty();
            }
            BtcBlock block = params.getDefaultSerializer().makeBlock(rawBlock.get());
            int resolvedHeight = height.orElse(0);
            return Optional.of(new FetchedBtcBlock(block, resolvedHeight));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while fetching BTC block {} via RPC", blockHash);
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            logger.warn("Failed to fetch BTC block {} via RPC: {}", blockHash, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Integer> fetchBlockHeight(Sha256Hash blockHash) throws IOException, InterruptedException {
        String payload = String.format(
                "{\"jsonrpc\":\"1.0\",\"id\":\"rskj-fac\",\"method\":\"getblockheader\",\"params\":[\"%s\",true]}",
                blockHash.toString());
        JsonNode response = sendRpc(payload);
        if (response == null || response.has("error") && !response.get("error").isNull()) {
            return Optional.empty();
        }
        JsonNode result = response.get("result");
        if (result == null || !result.has("height")) {
            return Optional.empty();
        }
        return Optional.of(result.get("height").asInt());
    }

    private Optional<byte[]> fetchRawBlock(Sha256Hash blockHash) throws IOException, InterruptedException {
        String payload = String.format(
                "{\"jsonrpc\":\"1.0\",\"id\":\"rskj-fac\",\"method\":\"getblock\",\"params\":[\"%s\",0]}",
                blockHash.toString());
        JsonNode response = sendRpc(payload);
        if (response == null || response.has("error") && !response.get("error").isNull()) {
            return Optional.empty();
        }
        JsonNode result = response.get("result");
        if (result == null || !result.isTextual()) {
            return Optional.empty();
        }
        return Optional.of(Hex.decode(result.asText()));
    }

    @Nullable
    private JsonNode sendRpc(String payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBtcRpcUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofMillis(config.getRpcTimeoutMs()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP status " + response.statusCode());
        }
        return MAPPER.readTree(response.body());
    }

    private synchronized void enforceRequestDelay() throws InterruptedException {
        long delay = config.getRpcRequestDelayMs();
        if (delay <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTimeMs;
        if (elapsed < delay) {
            Thread.sleep(delay - elapsed);
        }
        lastRequestTimeMs = System.currentTimeMillis();
    }

    private <T> Optional<T> retryOptional(Supplier<Optional<T>> operation) {
        int attempts = config.getRpcRetryAttempts();
        long backoffMs = config.getRpcRetryBackoffMs();
        long budgetStartMs = System.currentTimeMillis();
        long maxBudgetMs = config.getRpcRetryMaxBudgetMs();

        for (int attempt = 0; attempt < attempts; attempt++) {
            Optional<T> result = operation.get();
            if (result.isPresent()) {
                return result;
            }
            if (attempt >= attempts - 1) {
                break;
            }
            long waitMs = backoffMs * (1L << attempt);
            long elapsedMs = System.currentTimeMillis() - budgetStartMs;
            if (elapsedMs + waitMs > maxBudgetMs) {
                break;
            }
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public record FetchedBtcBlock(BtcBlock block, int height) {
    }
}

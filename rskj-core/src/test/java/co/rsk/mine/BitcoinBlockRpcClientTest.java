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
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.ForkBalanceBtcCacheConfig;
import co.rsk.core.bc.BtcBlockFacCache;
import co.rsk.test.mine.BitcoinRpcStubServer;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static co.rsk.test.mine.BitcoinRpcStubServer.rpcError;
import static co.rsk.test.mine.BitcoinRpcStubServer.rpcOk;

/**
 * JSON-RPC behavior for {@link BitcoinBlockRpcClient} using a local HTTP stub.
 */
class BitcoinBlockRpcClientTest {

    private static final RegTestParams BTC_PARAMS = RegTestParams.get();

    @Test
    void isConfigured_falseWhenUrlEmpty() {
        BitcoinBlockRpcClient client = clientWithUrl("");
        Assertions.assertFalse(client.isConfigured());
        Assertions.assertTrue(client.fetchBestBlockHash().isEmpty());
    }

    @Test
    void fetchBestBlockHash_returnsTipFromRpc() throws IOException {
        BtcBlock block = sampleBtcBlock();
        String hashHex = block.getHash().toString();

        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onMethod("getbestblockhash", rpcOk("\"" + hashHex + "\""))
                .start()) {
            BitcoinBlockRpcClient client = clientWithUrl(server.url());

            Optional<Sha256Hash> tip = client.fetchBestBlockHash();

            Assertions.assertTrue(tip.isPresent());
            Assertions.assertEquals(block.getHash(), tip.get());
            Assertions.assertEquals(1, server.requestCount("getbestblockhash"));
        }
    }

    @Test
    void fetchBestBlockHash_emptyOnRpcError() throws IOException {
        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onMethod("getbestblockhash", rpcError(-1, "not found"))
                .start()) {
            BitcoinBlockRpcClient client = clientWithUrl(server.url());

            Assertions.assertTrue(client.fetchBestBlockHash().isEmpty());
        }
    }

    @Test
    void fetchBestBlockHash_emptyOnHttpError() throws IOException {
        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.httpStatus(503).start()) {
            BitcoinBlockRpcClient client = clientWithUrl(server.url());

            Assertions.assertTrue(client.fetchBestBlockHash().isEmpty());
        }
    }

    @Test
    void fetchBlock_returnsBlockAndHeight() throws IOException {
        BtcBlock block = sampleBtcBlock();
        String blockHex = Hex.toHexString(block.bitcoinSerialize());

        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onMethod("getblockheader", rpcOk("{\"height\":123}"))
                .onMethod("getblock", rpcOk("\"" + blockHex + "\""))
                .start()) {
            BitcoinBlockRpcClient client = clientWithUrl(server.url());

            Optional<BitcoinBlockRpcClient.FetchedBtcBlock> fetched =
                    client.fetchBlock(block.getHash(), BTC_PARAMS);

            Assertions.assertTrue(fetched.isPresent());
            Assertions.assertEquals(123, fetched.get().height());
            Assertions.assertEquals(block.getHash(), fetched.get().block().getHash());
            Assertions.assertEquals(1, server.requestCount("getblockheader"));
            Assertions.assertEquals(1, server.requestCount("getblock"));
        }
    }

    @Test
    void fetchBlock_emptyWhenRawBlockMissing() throws IOException {
        BtcBlock block = sampleBtcBlock();

        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onMethod("getblockheader", rpcOk("{\"height\":1}"))
                .onMethod("getblock", rpcError(-5, "Block not found"))
                .start()) {
            BitcoinBlockRpcClient client = clientWithUrl(server.url());

            Assertions.assertTrue(client.fetchBlock(block.getHash(), BTC_PARAMS).isEmpty());
        }
    }

    @Test
    void fetchBlock_usesHeightZeroWhenHeaderUnavailable() throws IOException {
        BtcBlock block = sampleBtcBlock();
        String blockHex = Hex.toHexString(block.bitcoinSerialize());

        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onMethod("getblockheader", rpcError(-5, "header missing"))
                .onMethod("getblock", rpcOk("\"" + blockHex + "\""))
                .start()) {
            BitcoinBlockRpcClient client = clientWithUrl(server.url());

            Optional<BitcoinBlockRpcClient.FetchedBtcBlock> fetched =
                    client.fetchBlock(block.getHash(), BTC_PARAMS);

            Assertions.assertTrue(fetched.isPresent());
            Assertions.assertEquals(0, fetched.get().height());
            Assertions.assertEquals(block.getHash(), fetched.get().block().getHash());
        }
    }

    @Test
    void btcBlockFacCache_resolveParent_fetchesFromRpcWhenNotCached() throws IOException {
        BtcBlock block = sampleBtcBlock();
        String blockHex = Hex.toHexString(block.bitcoinSerialize());

        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onMethod("getblockheader", rpcOk("{\"height\":7}"))
                .onMethod("getblock", rpcOk("\"" + blockHex + "\""))
                .start()) {
            ForkBalanceBtcCacheConfig cacheConfig = new ForkBalanceBtcCacheConfig(12, server.url(), 0, 5_000);
            BtcBlockFacCache cache = new BtcBlockFacCache(
                    cacheConfig,
                    BTC_PARAMS,
                    new BitcoinBlockRpcClient(cacheConfig),
                    ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144));

            Assertions.assertTrue(cache.resolveParent(block.getHash()).isPresent());
            Assertions.assertEquals(1, server.requestCount("getblock"));
        }
    }

    @Test
    void enforcesMinimumDelayBetweenRpcCalls() throws IOException {
        BtcBlock block = sampleBtcBlock();
        String blockHex = Hex.toHexString(block.bitcoinSerialize());
        long delayMs = 200L;

        try (BitcoinRpcStubServer server = BitcoinRpcStubServer.ok()
                .onMethod("getblockheader", rpcOk("{\"height\":1}"))
                .onMethod("getblock", rpcOk("\"" + blockHex + "\""))
                .start()) {
            ForkBalanceBtcCacheConfig config = new ForkBalanceBtcCacheConfig(12, server.url(), delayMs, 5_000);
            BitcoinBlockRpcClient client = new BitcoinBlockRpcClient(config);

            long start = System.currentTimeMillis();
            client.fetchBlock(block.getHash(), BTC_PARAMS);
            long elapsed = System.currentTimeMillis() - start;

            Assertions.assertTrue(
                    elapsed >= delayMs,
                    "expected at least " + delayMs + "ms between getblockheader and getblock, got " + elapsed);
        }
    }

    private static BtcBlock sampleBtcBlock() {
        return RegtestBtcMergeMiningHelper.mineParentWithTwoTransactions(
                BTC_PARAMS,
                RegtestBtcMergeMiningHelper.neutralCoinbase(BTC_PARAMS, (byte) 0x42));
    }

    private static BitcoinBlockRpcClient clientWithUrl(String url) {
        return new BitcoinBlockRpcClient(new ForkBalanceBtcCacheConfig(12, url, 0, 5_000));
    }
}

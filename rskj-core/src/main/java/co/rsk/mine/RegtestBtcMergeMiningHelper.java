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
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.script.ScriptOpCodes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds synthetic regtest Bitcoin blocks with a valid parent-child link for fork-balance proofs.
 */
public final class RegtestBtcMergeMiningHelper {

    private RegtestBtcMergeMiningHelper() {
    }

    /**
     * Mines a regtest parent with two transactions so RSKIP-92 coinbase merkle proofs are non-empty.
     */
    public static BtcBlock mineParentWithTwoTransactions(
            NetworkParameters params,
            BtcTransaction parentCoinbase) {
        BtcTransaction tx2 = new BtcTransaction(params);
        tx2.addInput(new TransactionInput(
                params,
                tx2,
                new byte[0],
                new TransactionOutPoint(params, 0, parentCoinbase.getHash())));
        tx2.addOutput(new TransactionOutput(
                params,
                tx2,
                Coin.valueOf(0, 0),
                new byte[]{ScriptOpCodes.OP_RETURN, (byte) 0x7d}));
        List<BtcTransaction> txs = new ArrayList<>();
        txs.add(parentCoinbase);
        txs.add(tx2);
        BtcBlock block = new BtcBlock(
                params,
                params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT),
                Sha256Hash.ZERO_HASH,
                null,
                (System.currentTimeMillis() / 1000) - 1_000_000,
                Utils.encodeCompactBits(params.getMaxTarget()),
                0,
                txs);
        findRegtestNonce(block, params.getMaxTarget());
        return block;
    }

    private static void findRegtestNonce(BtcBlock block, BigInteger target) {
        long nonce = 0;
        while (nonce < Long.MAX_VALUE) {
            block.setNonce(nonce++);
            if (block.getHash().toBigInteger().compareTo(target) <= 0) {
                return;
            }
        }
        throw new IllegalStateException("Could not find regtest nonce for synthetic BTC parent block");
    }

    public static BtcBlock buildChildOnParent(
            NetworkParameters params,
            BtcBlock parent,
            BtcTransaction childCoinbase) {
        BtcTransaction tx2 = new BtcTransaction(params);
        tx2.addInput(new TransactionInput(
                params,
                tx2,
                new byte[0],
                new TransactionOutPoint(params, 0, childCoinbase.getHash())));
        tx2.addOutput(new TransactionOutput(
                params,
                tx2,
                Coin.valueOf(0, 0),
                new byte[]{ScriptOpCodes.OP_RETURN, (byte) 0x7e}));
        List<BtcTransaction> txs = new ArrayList<>();
        txs.add(childCoinbase);
        txs.add(tx2);
        return new BtcBlock(
                params,
                params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT),
                parent.getHash(),
                null,
                parent.getTimeSeconds() + 600,
                parent.getDifficultyTarget(),
                0,
                txs);
    }

    public static BtcTransaction neutralCoinbase(NetworkParameters params, byte marker) {
        BtcTransaction coinbase = new BtcTransaction(params);
        coinbase.addInput(new TransactionInput(params, coinbase, new byte[]{marker}));
        coinbase.addOutput(new TransactionOutput(
                params, coinbase, Coin.valueOf(0, 0), new byte[]{ScriptOpCodes.OP_RETURN, marker}));
        return coinbase;
    }
}

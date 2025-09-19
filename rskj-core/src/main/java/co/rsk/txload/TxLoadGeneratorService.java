/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.txload;

import co.rsk.config.InternalService;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.core.CallTransaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.core.Blockchain;
import org.ethereum.util.RLP;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Submits synthetic transactions to fill blocks close to target gas.
 * Profiles: intrinsic (no-op), cpu (GasBurner loop), writes (SSTORE-heavy), reads (SLOAD-heavy), mixed.
 */
public class TxLoadGeneratorService implements InternalService {
    private static final Logger logger = LoggerFactory.getLogger("txload");

    private final RskSystemProperties config;
    private final Ethereum ethereum;
    private final Wallet wallet;
    private final TransactionPool transactionPool;
    private final Blockchain blockchain;

    private final long blockTargetGas;
    private final long primaryTxGas;
    private final String profile;
    private final String[] rotateProfiles = new String[]{"intrinsic","cpu","writes","reads","mixed","calldata"};
    private long lastSeenBlock = -1L;
    private int rotateIndex = 0;
    private final String senderSeed;
    private volatile long nextAllowedSubmitAtMs = 0L;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TxLoadGenerator");
        t.setDaemon(true);
        return t;
    });

    private Account senderAccount;
    private byte[] cpuContract;
    private byte[] writesContract;
    private byte[] readsContract;
    private byte[] writesRandomContract;
    // Compiled creation bytecode for persistent SSTORE random writes
    private static final String RANDOM_WRITES_CREATION_HEX = "608060405234801561000f575f80fd5b506101818061001d5f395ff3fe608060405234801561000f575f80fd5b5060043610610034575f3560e01c806323c62778146100385780633dcf015914610054575b5f80fd5b610052600480360381019061004d919061010d565b610070565b005b61006e6004803603810190610069919061010d565b61009f565b005b815b815a111561009a5780600d1b811890508060071c811890508060111b811890505a8155610072565b505050565b805f5b838110156100d05781600d1b821891508160071c821891508160111b821891505a82556001810190506100a2565b50505050565b5f80fd5b5f819050919050565b6100ec816100da565b81146100f6575f80fd5b50565b5f81359050610107816100e3565b92915050565b5f8060408385031215610123576101226100d6565b5b5f610130858286016100f9565b9250506020610141858286016100f9565b915050925092905056fea26469706673582212208192f606f9266c8315c4fb5918dd4e76e78d66c927080e249eb43afaa567bfb964736f6c63430008180033";

    public TxLoadGeneratorService(RskSystemProperties config,
                                  Ethereum ethereum,
                                  Wallet wallet,
                                  TransactionPool transactionPool,
                                  Blockchain blockchain) {
        this.config = config;
        this.ethereum = ethereum;
        this.wallet = wallet;
        this.transactionPool = transactionPool;
        this.blockchain = blockchain;
        this.blockTargetGas = config.txLoadBlockTargetGas();
        this.primaryTxGas = config.txLoadPrimaryTxGas();
        this.profile = config.txLoadProfile();
        this.senderSeed = config.txLoadSenderSeed();
    }

    @Override
    public void start() {
        if (running.getAndSet(true)) return;
        if (!config.txLoadEnabled()) return;

        // prepare sender account: prefer local coinbase (from miner.coinbase.secret) if available
        Account coinbaseAcc = config.localCoinbaseAccount();
        if (coinbaseAcc != null) {
            wallet.addAccount(coinbaseAcc);
            senderAccount = wallet.getAccount(coinbaseAcc.getAddress());
        }

        if (senderAccount == null) {
            byte[] pk = HashUtil.keccak256(senderSeed.getBytes(StandardCharsets.UTF_8));
            RskAddress sender = new RskAddress(wallet.addAccountWithPrivateKey(pk));
            senderAccount = wallet.getAccount(sender);
        }
        logger.info("TxLoadGenerator enabled. profile={}, primaryTxGas={}, blockTargetGas={}", profile, primaryTxGas, blockTargetGas);

        executor.execute(this::pumpLoop);
    }

    private void pumpLoop() {
        while (running.get()) {
            try {
                // wait until at least one block is mined so coinbase exists with balance
                if (blockchain.getBestBlock().getNumber() == 0) {
                    Thread.sleep(200);
                    continue;
                }
                // simple backoff to avoid spamming when pool rejects with nonce window errors
                long now = System.currentTimeMillis();
                if (now < nextAllowedSubmitAtMs) {
                    Thread.sleep(Math.min(250, (int)(nextAllowedSubmitAtMs - now)));
                    continue;
                }
                maybeAdvanceProfileOnNewBlock();
                ensureContractsDeployed();
                fillMempoolForNextBlock();
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("txload error", e);
            }
        }
    }

    private void ensureContractsDeployed() {
        if (cpuContract != null && writesContract != null && readsContract != null && writesRandomContract != null) {
            return;
        }

        BigInteger baseNonce = transactionPool.getPendingState().getNonce(senderAccount.getAddress());

        // Deploy GasBurner (CPU)
        if (cpuContract == null) {
            String gasBurnerCreation = "6080604052348015600f57600080fd5b5060ac8061001e6000396000f3fe6080604052348015600f57600080fd5b506004361060285760003560e01c8063b9554c5914602d575b600080fd5b6033604b565b60405180821515815260200191505060405180910390f35b600080600090505b6127105a1115606e5760008190505080806001019150506053565b50600190509056fea264697066735822122026bfffa9a996f0df8d2ed7759d8bc2ef51c61f30824877454f324e954a6a93d764736f6c63430007030033";
            Transaction tx = buildCreationTx(baseNonce, gasBurnerCreation, primaryTxGas);
            tx.sign(senderAccount.getEcKey().getPrivKeyBytes());
            ethereum.submitTransaction(tx);
            cpuContract = computeContractAddress(senderAccount.getAddress().getBytes(), baseNonce);
            baseNonce = baseNonce.add(BigInteger.ONE);
        }

        // Deploy TstoreLoopUntilOutOfGas (Writes)
        if (writesContract == null) {
            String tstoreCreation = "6080604052348015600e575f80fd5b5060868061001b5f395ff3fe6080604052348015600e575f80fd5b50600436106026575f3560e01c80637b17abde14602a575b5f80fd5b60306032565b005b5f5b620f4240811015604d575a5a5d80806001019150506034565b5056fea2646970667358221220460f7f44d313897b5627f933b4969a97d228645b5447a4ea286119f6cc66155964736f6c63430008180033";
            Transaction tx = buildCreationTx(baseNonce, tstoreCreation, Math.max(primaryTxGas, 1_000_000L));
            tx.sign(senderAccount.getEcKey().getPrivKeyBytes());
            ethereum.submitTransaction(tx);
            writesContract = computeContractAddress(senderAccount.getAddress().getBytes(), baseNonce);
            baseNonce = baseNonce.add(BigInteger.ONE);
        }

        // Deploy TstoreAndTloadLoopUntilOutOfGas (Reads+Writes)
        if (readsContract == null) {
            String tstoreTloadCreation = "6080604052348015600e575f80fd5b5060888061001b5f395ff3fe6080604052348015600e575f80fd5b50600436106026575f3560e01c8063ae1978d714602a575b5f80fd5b60306032565b005b5f5b620f4240811015604f575a5a815d5080806001019150506034565b5056fea2646970667358221220db37ed7fb5e1de866d127a5da76e02d895a6014e4de7965e57f66ae94ad5d1d764736f6c63430008180033";
            Transaction tx = buildCreationTx(baseNonce, tstoreTloadCreation, Math.max(primaryTxGas, 1_000_000L));
            tx.sign(senderAccount.getEcKey().getPrivKeyBytes());
            ethereum.submitTransaction(tx);
            readsContract = computeContractAddress(senderAccount.getAddress().getBytes(), baseNonce);
            baseNonce = baseNonce.add(BigInteger.ONE);
        }

        // Deploy RandomWrites persistent SSTORE implementation
        if (writesRandomContract == null) {
            Transaction tx = buildCreationTx(baseNonce, RANDOM_WRITES_CREATION_HEX, Math.max(primaryTxGas, 1_000_000L));
            tx.sign(senderAccount.getEcKey().getPrivKeyBytes());
            ethereum.submitTransaction(tx);
            writesRandomContract = computeContractAddress(senderAccount.getAddress().getBytes(), baseNonce);
        }
    }

    private Transaction buildCreationTx(BigInteger nonce, String creationBytecodeHex, long gasLimit) {
        TransactionArguments ta = new TransactionArguments();
        ta.setNonce(nonce);
        ta.setGasPrice(BigInteger.ONE);
        ta.setGasLimit(BigInteger.valueOf(Math.max(gasLimit, 1_000_000L)));
        ta.setTo((byte[]) null);
        ta.setValue(BigInteger.ZERO);
        ta.setData(normalizeHex(creationBytecodeHex));
        return Transaction.builder().withTransactionArguments(ta).build();
    }

    private byte[] computeContractAddress(byte[] sender, BigInteger nonce) {
        byte[] enc = RLP.encodeList(RLP.encodeElement(sender), RLP.encodeBigInteger(nonce));
        byte[] h = HashUtil.keccak256(enc);
        byte[] out = new byte[20];
        System.arraycopy(h, 12, out, 0, 20);
        return out;
    }

    private void maybeAdvanceProfileOnNewBlock() {
        if (!"rotate".equalsIgnoreCase(profile)) {
            return;
        }
        long best = blockchain.getBestBlock().getNumber();
        if (best != lastSeenBlock) {
            lastSeenBlock = best;
            rotateIndex = (rotateIndex + 1) % rotateProfiles.length;
        }
    }

    private void fillMempoolForNextBlock() {
        // adapt to current block gas limit (consensus only allows gradual changes)
        long blockGasLimit = getCurrentBlockGasLimit();
        long safetyMargin = 100_000L; // leave room for remasc/overheads
        long effectiveTarget = Math.max(0L, Math.min(blockTargetGas, blockGasLimit - safetyMargin));
        if (effectiveTarget < 50_000L) {
            return;
        }

        long effectivePrimary = Math.max(21_000L, Math.min(primaryTxGas, effectiveTarget));

        // get current pending nonce
        BigInteger nonce = transactionPool.getPendingState().getNonce(senderAccount.getAddress());

        long remaining = effectiveTarget;
        // one large primary transaction
        if (effectivePrimary > 0) {
            if (submitSyntheticTx(nonce, effectivePrimary)) {
                remaining -= effectivePrimary;
                nonce = nonce.add(BigInteger.ONE);
            } else {
                // if rejected due to nonce window, stop early to avoid spamming
                return;
            }
        }

        // fill remainder with near-intrinsic or small profile txs until just below target
        long smallGas = Math.max(21_000L, Math.min(200_000L, remaining));
        while (remaining > 0) {
            long gas = Math.min(smallGas, remaining);
            if (submitSyntheticTx(nonce, gas)) {
                remaining -= gas;
                nonce = nonce.add(BigInteger.ONE);
            } else {
                // stop on nonce window/full mempool condition
                break;
            }
        }
    }

    private long getCurrentBlockGasLimit() {
        org.ethereum.core.Block best = blockchain.getBestBlock();
        if (best == null || best.getGasLimit() == null) {
            return 0L;
        }
        return new java.math.BigInteger(1, best.getGasLimit()).longValue();
    }

    private boolean submitSyntheticTx(BigInteger nonce, long gasLimit) {
        Transaction tx;
        String effectiveProfile = profile;
        if ("rotate".equalsIgnoreCase(profile)) {
            effectiveProfile = rotateProfiles[rotateIndex];
        }
        switch (effectiveProfile.toLowerCase()) {
            case "cpu":
                tx = buildCpuTx(nonce, gasLimit);
                break;
            case "writes":
                tx = buildWritesTx(nonce, gasLimit);
                break;
            case "reads":
                tx = buildReadsTx(nonce, gasLimit);
                break;
            case "mixed":
                tx = buildMixedTx(nonce, gasLimit);
                break;
            case "calldata":
                tx = buildCalldataOnlyTx(nonce, gasLimit);
                break;
            case "intrinsic":
            default:
                tx = buildIntrinsicTx(nonce, gasLimit);
        }
        tx.sign(senderAccount.getEcKey().getPrivKeyBytes());
        TransactionPoolAddResult res = ethereum.submitTransaction(tx);
        if (!res.transactionsWereAdded()) {
            logger.trace("tx not added: {}", res.getErrorMessage());
            // backoff on nonce window errors to let mining catch up
            String err = res.getErrorMessage();
            if (err != null && err.contains("nonce too high")) {
                logger.trace("Nonce too high, backing off for 100ms, nonce: {}", nonce);
                // wait either next block or short cooldown
                nextAllowedSubmitAtMs = System.currentTimeMillis() + 100L;
                return false;
            }
            return true;
        }
        // success
        nextAllowedSubmitAtMs = 0L;
        return true;
    }

    private Transaction buildIntrinsicTx(BigInteger nonce, long gasLimit) {
        // Use intrinsic gas by attaching large non-zero data payload in a CALL to sender address
        long base = 21000L;
        long perNonZero = 68L;
        long budget = Math.max(0L, gasLimit - base);
        long bytes = Math.max(0L, budget / perNonZero);
        // Cap payload to avoid giant tx objects
        bytes = Math.min(bytes, 256_000L);

        StringBuilder data = new StringBuilder((int) bytes * 2);
        for (int i = 0; i < bytes; i++) {
            data.append("01");
        }

        TransactionArguments ta = new TransactionArguments();
        ta.setNonce(nonce);
        ta.setGasPrice(BigInteger.ONE);
        ta.setGasLimit(BigInteger.valueOf(gasLimit));
        ta.setTo(senderAccount.getAddress().getBytes());
        ta.setValue(BigInteger.ZERO);
        ta.setData(data.toString());
        return Transaction.builder().withTransactionArguments(ta).build();
    }

    private Transaction buildCpuTx(BigInteger nonce, long gasLimit) {
        // call GasBurner.burnGas(uint256) with lowerLimit=0
        byte[] burner = cpuContract;
        TransactionArguments ta = new TransactionArguments();
        ta.setNonce(nonce);
        ta.setGasPrice(BigInteger.ONE);
        ta.setGasLimit(BigInteger.valueOf(gasLimit));
        ta.setTo(burner);
        ta.setValue(BigInteger.ZERO);
        String selectorAndArg = "b9554c59" + leftPad64("0");
        ta.setData(selectorAndArg);
        logger.trace("Submitting CPU tx with data (hex, no 0x): {}", ta.getData());
        return Transaction.builder().withTransactionArguments(ta).build();
    }

    private Transaction buildWritesTx(BigInteger nonce, long gasLimit) {
        // Prefer persistent RandomWrites.runRandomWritesUntilOutOfGas(seed, minGasLeft)
        if (writesRandomContract != null) {
            byte[] addr = writesRandomContract;
            long minGasLeft = 30000L;
            BigInteger seed = BigInteger.valueOf(System.nanoTime());
            CallTransaction.Function f = CallTransaction.Function.fromSignature(
                    "runRandomWritesUntilOutOfGas",
                    new String[]{"uint256","uint256"}, new String[]{});
            byte[] data = f.encode(seed, BigInteger.valueOf(minGasLeft));
            TransactionArguments ta = baseCallArgs(nonce, gasLimit, addr, data);
            return Transaction.builder().withTransactionArguments(ta).build();
        }
        // fallback to transient
        byte[] addr = writesContract;
        TransactionArguments ta = baseCallArgs(nonce, gasLimit, addr, "7b17abde");
        return Transaction.builder().withTransactionArguments(ta).build();
    }

    private Transaction buildReadsTx(BigInteger nonce, long gasLimit) {
        // call TstoreAndTloadLoopUntilOutOfGas.runTstoreAndTloadUntilOutOfGas() -> 0xae1978d7
        byte[] addr = readsContract;
        TransactionArguments ta = baseCallArgs(nonce, gasLimit, addr, "ae1978d7");
        return Transaction.builder().withTransactionArguments(ta).build();
    }

    private Transaction buildMixedTx(BigInteger nonce, long gasLimit) {
        byte[] addr = dummyContractAddress("mixed");
        String pattern = repeat("54", 16) + repeat("55", 16); // some reads and writes
        TransactionArguments ta = baseCallArgs(nonce, gasLimit, addr, pattern);
        return Transaction.builder().withTransactionArguments(ta).build();
    }

    // Build a transaction that spends almost all gas in calldata cost
    // Gas cost: base 21000 + 68 per non-zero byte (16 per zero). Use non-zero to maximize.
    // Respect pool TX_MAX_SIZE (=128KB). We'll cap payload to not exceed it.
    private Transaction buildCalldataOnlyTx(BigInteger nonce, long gasLimit) {
        final long base = 21000L;
        final long perNonZero = 68L;
        long budget = Math.max(0L, gasLimit - base);
        long nonZeroBytes = Math.max(0L, budget / perNonZero);
        // Hard cap to TX_MAX_SIZE (128KB). Keep some headroom for RLP overhead; cap at 120KB
        long maxBytes = 120_000L;
        if (nonZeroBytes > maxBytes) nonZeroBytes = maxBytes;

        StringBuilder data = new StringBuilder((int) nonZeroBytes * 2);
        for (int i = 0; i < nonZeroBytes; i++) {
            data.append("01");
        }

        TransactionArguments ta = new TransactionArguments();
        ta.setNonce(nonce);
        ta.setGasPrice(BigInteger.ONE);
        ta.setGasLimit(BigInteger.valueOf(gasLimit));
        // Send to self so it executes as a CALL with data (no contract needed)
        ta.setTo(senderAccount.getAddress().getBytes());
        ta.setValue(BigInteger.ZERO);
        ta.setData(data.toString());
        return Transaction.builder().withTransactionArguments(ta).build();
    }

    private TransactionArguments baseCallArgs(BigInteger nonce, long gasLimit, byte[] to, String data) {
        TransactionArguments ta = new TransactionArguments();
        ta.setNonce(nonce);
        ta.setGasPrice(BigInteger.ONE);
        ta.setGasLimit(BigInteger.valueOf(gasLimit));
        ta.setTo(to);
        ta.setValue(BigInteger.ZERO);
        ta.setData(normalizeHex(data));
        return ta;
    }

    private TransactionArguments baseCallArgs(BigInteger nonce, long gasLimit, byte[] to, byte[] data) {
        TransactionArguments ta = new TransactionArguments();
        ta.setNonce(nonce);
        ta.setGasPrice(BigInteger.ONE);
        ta.setGasLimit(BigInteger.valueOf(gasLimit));
        ta.setTo(to);
        ta.setValue(BigInteger.ZERO);
        ta.setData(Hex.toHexString(data));
        return ta;
    }

    private byte[] dummyContractAddress(String salt) {
        // Not deploying, just sending to a deterministic address; EVM will throw but still consume gas
        byte[] h = HashUtil.keccak256((senderSeed + ":" + salt).getBytes(StandardCharsets.UTF_8));
        // Take first 20 bytes (40 hex chars) for address
        return Hex.decode(Hex.toHexString(h).substring(0, 40));
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    private static String leftPad64(String hexNoPrefix) {
        String s = hexNoPrefix;
        if (s.startsWith("0x")) s = s.substring(2);
        while (s.length() < 64) s = "0" + s;
        return s;
    }

    private static String normalizeHex(String hex) {
        if (hex == null) return null;
        String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if ((s.length() & 1) == 1) s = "0" + s;
        return s;
    }

    @Override
    public void stop() {
        if (!running.getAndSet(false)) return;
        executor.shutdownNow();
    }
}

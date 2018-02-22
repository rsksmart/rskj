/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Precompiled contract that manages the 2 way peg between bitcoin and RSK.
 * This class is just a wrapper, actual functionality is found in BridgeSupport.
 * @author Oscar Guindzberg
 */
public class Bridge extends PrecompiledContracts.PrecompiledContract {

    private static final Logger logger = LoggerFactory.getLogger("bridge");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    // No parameters
    public static final CallTransaction.Function UPDATE_COLLECTIONS = CallTransaction.Function.fromSignature("updateCollections", new String[]{}, new String[]{});
    // Parameters: an array of bitcoin blocks serialized with the bitcoin wire protocol format
    public static final CallTransaction.Function RECEIVE_HEADERS = CallTransaction.Function.fromSignature("receiveHeaders", new String[]{"bytes[]"}, new String[]{});
    // Parameters:
    // - A bitcoin tx, serialized with the bitcoin wire protocol format
    // - The bitcoin block height that contains the tx
    // - A merkle tree that shows the tx is included in that block, serialized with the bitcoin wire protocol format.
    public static final CallTransaction.Function REGISTER_BTC_TRANSACTION = CallTransaction.Function.fromSignature("registerBtcTransaction", new String[]{"bytes", "int", "bytes"}, new String[]{});
    // No parameters, the current rsk tx is used as input.
    public static final CallTransaction.Function RELEASE_BTC = CallTransaction.Function.fromSignature("releaseBtc", new String[]{}, new String[]{});
    // Parameters:
    // Federator public key.
    // Transaction signature array, one for each btc tx input.
    // Rsk tx hash of the tx that required the release of funds.
    public static final CallTransaction.Function ADD_SIGNATURE = CallTransaction.Function.fromSignature("addSignature", new String[]{"bytes","bytes[]","bytes"}, new String[]{});
    // Returns a StateForFederator encoded in RLP
    public static final CallTransaction.Function GET_STATE_FOR_BTC_RELEASE_CLIENT = CallTransaction.Function.fromSignature("getStateForBtcReleaseClient", new String[]{}, new String[]{"bytes"});
    // Returns a BridgeState encoded in RLP
    public static final CallTransaction.Function GET_STATE_FOR_DEBUGGING = CallTransaction.Function.fromSignature("getStateForDebugging", new String[]{}, new String[]{"bytes"});
    // Return the bitcoin blockchain best chain height know by the bridge contract
    public static final CallTransaction.Function GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT = CallTransaction.Function.fromSignature("getBtcBlockchainBestChainHeight", new String[]{}, new String[]{"int"});
    // Return the height of the initial block stored in the bridge's bitcoin blockchain
    public static final CallTransaction.Function GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT = CallTransaction.Function.fromSignature("getBtcBlockchainInitialBlockHeight", new String[]{}, new String[]{"int"});
    // Returns an array of block hashes known by the bridge contract.
    // Protocol inspired by bitcoin sync protocol, see block locator in https://en.bitcoin.it/wiki/Protocol_documentation#getheaders
    // Method now deprecated in favor of getBtcBlockchainBlockHashAtDepth
    public static final CallTransaction.Function GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR = CallTransaction.Function.fromSignature("getBtcBlockchainBlockLocator", new String[]{}, new String[]{"string[]"});
    // Returns the block hash of the bridge contract's best chain at the given depth, meaning depth zero will
    // yield the best chain head hash and depth one will yield its parent hash, and so on and so forth.
    // Federators use this to find what is the latest block in the mainchain the bridge has
    // (replacing the need for getBtcBlockchainBlockLocator).
    // The goal of this function is to help synchronize bridge and federators blockchains.
    public static final CallTransaction.Function GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH = CallTransaction.Function.fromSignature("getBtcBlockchainBlockHashAtDepth", new String[]{"int256"}, new String[]{"bytes"});
    // Returns the minimum amount of satoshis a user should send to the federation.
    public static final CallTransaction.Function GET_MINIMUM_LOCK_TX_VALUE = CallTransaction.Function.fromSignature("getMinimumLockTxValue", new String[]{}, new String[]{"int"});

    // Returns whether a given btc tx hash was already processed by the bridge
    public static final CallTransaction.Function IS_BTC_TX_HASH_ALREADY_PROCESSED = CallTransaction.Function.fromSignature("isBtcTxHashAlreadyProcessed", new String[]{"string"}, new String[]{"bool"});
    // Returns whether a given btc tx hash was already processed by the bridge
    public static final CallTransaction.Function GET_BTC_TX_HASH_PROCESSED_HEIGHT = CallTransaction.Function.fromSignature("getBtcTxHashProcessedHeight", new String[]{"string"}, new String[]{"int64"});

    // Returns the federation bitcoin address
    public static final CallTransaction.Function GET_FEDERATION_ADDRESS = CallTransaction.Function.fromSignature("getFederationAddress", new String[]{}, new String[]{"string"});
    // Returns the number of federates in the currently active federation
    public static final CallTransaction.Function GET_FEDERATION_SIZE = CallTransaction.Function.fromSignature("getFederationSize", new String[]{}, new String[]{"int256"});
    // Returns the number of minimum required signatures from the currently active federation
    public static final CallTransaction.Function GET_FEDERATION_THRESHOLD = CallTransaction.Function.fromSignature("getFederationThreshold", new String[]{}, new String[]{"int256"});
    // Returns the public key of the federator at the specified index
    public static final CallTransaction.Function GET_FEDERATOR_PUBLIC_KEY = CallTransaction.Function.fromSignature("getFederatorPublicKey", new String[]{"int256"}, new String[]{"bytes"});
    // Returns the creation time of the federation
    public static final CallTransaction.Function GET_FEDERATION_CREATION_TIME = CallTransaction.Function.fromSignature("getFederationCreationTime", new String[]{}, new String[]{"int256"});
    // Returns the block number of the creation of the federation
    public static final CallTransaction.Function GET_FEDERATION_CREATION_BLOCK_NUMBER = CallTransaction.Function.fromSignature("getFederationCreationBlockNumber", new String[]{}, new String[]{"int256"});

    // Returns the retiring federation bitcoin address
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_ADDRESS = CallTransaction.Function.fromSignature("getRetiringFederationAddress", new String[]{}, new String[]{"string"});
    // Returns the number of federates in the retiring federation
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_SIZE = CallTransaction.Function.fromSignature("getRetiringFederationSize", new String[]{}, new String[]{"int256"});
    // Returns the number of minimum required signatures from the retiring federation
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_THRESHOLD = CallTransaction.Function.fromSignature("getRetiringFederationThreshold", new String[]{}, new String[]{"int256"});
    // Returns the public key of the retiring federation's federator at the specified index
    public static final CallTransaction.Function GET_RETIRING_FEDERATOR_PUBLIC_KEY = CallTransaction.Function.fromSignature("getRetiringFederatorPublicKey", new String[]{"int256"}, new String[]{"bytes"});
    // Returns the creation time of the retiring federation
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_CREATION_TIME = CallTransaction.Function.fromSignature("getRetiringFederationCreationTime", new String[]{}, new String[]{"int256"});
    // Returns the block number of the creation of the retiring federation
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER = CallTransaction.Function.fromSignature("getRetiringFederationCreationBlockNumber", new String[]{}, new String[]{"int256"});

    // Creates a new pending federation and returns its id
    public static final CallTransaction.Function CREATE_FEDERATION = CallTransaction.Function.fromSignature("createFederation", new String[]{}, new String[]{"int256"});
    // Adds the given key to the current pending federation
    public static final CallTransaction.Function ADD_FEDERATOR_PUBLIC_KEY = CallTransaction.Function.fromSignature("addFederatorPublicKey", new String[]{"bytes"}, new String[]{"int256"});
    // Commits the currently pending federation
    public static final CallTransaction.Function COMMIT_FEDERATION = CallTransaction.Function.fromSignature("commitFederation", new String[]{"bytes"}, new String[]{"int256"});
    // Rolls back the currently pending federation
    public static final CallTransaction.Function ROLLBACK_FEDERATION = CallTransaction.Function.fromSignature("rollbackFederation", new String[]{}, new String[]{"int256"});

    // Returns the current pending federation's hash
    public static final CallTransaction.Function GET_PENDING_FEDERATION_HASH = CallTransaction.Function.fromSignature("getPendingFederationHash", new String[]{}, new String[]{"bytes"});
    // Returns the number of federates in the current pending federation
    public static final CallTransaction.Function GET_PENDING_FEDERATION_SIZE = CallTransaction.Function.fromSignature("getPendingFederationSize", new String[]{}, new String[]{"int256"});
    // Returns the public key of the federator at the specified index for the current pending federation
    public static final CallTransaction.Function GET_PENDING_FEDERATOR_PUBLIC_KEY = CallTransaction.Function.fromSignature("getPendingFederatorPublicKey", new String[]{"int256"}, new String[]{"bytes"});

    // Returns the lock whitelist size
    public static final CallTransaction.Function GET_LOCK_WHITELIST_SIZE = CallTransaction.Function.fromSignature("getLockWhitelistSize", new String[]{}, new String[]{"int256"});
    // Returns the lock whitelist address stored at the specified index
    public static final CallTransaction.Function GET_LOCK_WHITELIST_ADDRESS = CallTransaction.Function.fromSignature("getLockWhitelistAddress", new String[]{"int256"}, new String[]{"string"});
    // Adds the given address to the lock whitelist
    public static final CallTransaction.Function ADD_LOCK_WHITELIST_ADDRESS = CallTransaction.Function.fromSignature("addLockWhitelistAddress", new String[]{"string", "int256"}, new String[]{"int256"});
    // Adds the given address to the lock whitelist
    public static final CallTransaction.Function REMOVE_LOCK_WHITELIST_ADDRESS = CallTransaction.Function.fromSignature("removeLockWhitelistAddress", new String[]{"string"}, new String[]{"int256"});

    public static final CallTransaction.Function SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY = CallTransaction.Function.fromSignature("setLockWhitelistDisableBlockDelay", new String[]{"int256"}, new String[]{"int256"});

    // Returns the current fee per kb
    public static final CallTransaction.Function GET_FEE_PER_KB = CallTransaction.Function.fromSignature("getFeePerKb", new String[]{}, new String[]{"int256"});
    // Adds the given key to the current pending federation
    public static final CallTransaction.Function VOTE_FEE_PER_KB = CallTransaction.Function.fromSignature("voteFeePerKbChange", new String[]{"int256"}, new String[]{"int256"});

    // Log topics used by Bridge Contract
    public static final DataWord RELEASE_BTC_TOPIC = new DataWord("release_btc_topic".getBytes(StandardCharsets.UTF_8));
    public static final DataWord UPDATE_COLLECTIONS_TOPIC = new DataWord("update_collections_topic".getBytes(StandardCharsets.UTF_8));
    public static final DataWord ADD_SIGNATURE_TOPIC = new DataWord("add_signature_topic".getBytes(StandardCharsets.UTF_8));
    public static final DataWord COMMIT_FEDERATION_TOPIC = new DataWord("commit_federation_topic".getBytes(StandardCharsets.UTF_8));

    private Map<ByteArrayWrapper, CallTransaction.Function> functions = new HashMap<>();
    private static Map<CallTransaction.Function, Long> functionCostMap = new HashMap<>();

    private RskSystemProperties config;
    private BlockchainNetConfig blockchainNetConfig;
    private BlockchainConfig blockchainConfig;
    private BridgeConstants bridgeConstants;

    private org.ethereum.core.Transaction rskTx;
    private org.ethereum.core.Block rskExecutionBlock;
    private Repository repository;
    private List<LogInfo> logs;

    private BridgeSupport bridgeSupport;

    public Bridge(RskSystemProperties config, RskAddress contractAddress) {
        this.contractAddress = contractAddress;

        this.config = config;
        this.blockchainNetConfig = config.getBlockchainConfig();
        this.bridgeConstants = blockchainNetConfig.getCommonConstants().getBridgeConstants();
    }

    @Override
    public long getGasForData(byte[] data) {
        // If being called from a contract pre the RFS-90 fork, this would throw a null pointer exception
        // Mimic the same behavior
        if (!blockchainConfig.isRfs90()) {
            byte[] senderCode = repository.getCode(rskTx.getSender());
            if (senderCode != null && senderCode.length > 0) {
                throw new RuntimeException("Trying to estimate gas from contract pre RFS-90 fork");
            }
        }

        if (BridgeUtils.isFreeBridgeTx(config, rskTx, rskExecutionBlock.getNumber())) {
            return 0;
        }

        BridgeParsedData bridgeParsedData = parseData(data);

        Long functionCost;
        Long totalCost;
        if (bridgeParsedData == null) {
            functionCost = functionCostMap.get(Bridge.RELEASE_BTC);
            totalCost = functionCost;
        } else {
            functionCost = functionCostMap.get(bridgeParsedData.function);

            if (functionCost == null) {
                throw new IllegalStateException();
            }

            int dataCost = data == null ? 0 : data.length * 2;

            totalCost = functionCost + dataCost;
        }

        return totalCost;
    }

    @VisibleForTesting
    BridgeParsedData parseData(byte[] data) {
        BridgeParsedData bridgeParsedData = new BridgeParsedData();

        if (data != null && (data.length >= 1 && data.length <= 3)) {
            logger.warn("Invalid function signature {}.", Hex.toHexString(data));
            return null;
        }

        if (data == null || data.length == 0) {
            bridgeParsedData.function = RELEASE_BTC;
            bridgeParsedData.args = new Object[]{};
        } else {
            byte[] functionSignature = Arrays.copyOfRange(data, 0, 4);
            bridgeParsedData.function = functions.get(new ByteArrayWrapper(functionSignature));
            if (bridgeParsedData.function == null) {
                logger.warn("Invalid function signature {}.", Hex.toHexString(functionSignature));
                return null;
            }
            try {
                bridgeParsedData.args = bridgeParsedData.function.decode(data);
            } catch (Exception e) {
                logger.warn("Invalid function arguments {} for function {}.", Hex.toHexString(data), Hex.toHexString(functionSignature));
                return null;
            }
        }
        return bridgeParsedData;
    }

    // Parsed rsk transaction data field
    private static class BridgeParsedData {
        CallTransaction.Function function;
        Object[] args;
    }

    @Override
    public void init(Transaction rskTx, Block rskExecutionBlock, Repository repository, BlockStore rskBlockStore, ReceiptStore rskReceiptStore, List<LogInfo> logs) {
        this.rskTx = rskTx;
        this.rskExecutionBlock = rskExecutionBlock;
        this.repository = repository;
        this.logs = logs;
        this.blockchainConfig = blockchainNetConfig.getConfigForBlock(rskExecutionBlock.getNumber());
        configureFunctions();
    }

    private void configureFunctions() {
        List<Pair<CallTransaction.Function, Long>> functionsAndCosts = new ArrayList<>(Arrays.asList(
            Pair.of(UPDATE_COLLECTIONS, 48000L),
            Pair.of(RECEIVE_HEADERS, 22000L),
            Pair.of(REGISTER_BTC_TRANSACTION, 22000L),
            Pair.of(RELEASE_BTC, 23000L),
            Pair.of(ADD_SIGNATURE, 70000L),
            Pair.of(GET_STATE_FOR_BTC_RELEASE_CLIENT, 4000L),
            Pair.of(GET_STATE_FOR_DEBUGGING, 3_000_000L),
            Pair.of(GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT, 19000L),
            Pair.of(GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR, 76000L),
            Pair.of(GET_MINIMUM_LOCK_TX_VALUE, 2000L),
            Pair.of(IS_BTC_TX_HASH_ALREADY_PROCESSED, 23000L),
            Pair.of(GET_BTC_TX_HASH_PROCESSED_HEIGHT, 22000L),
            Pair.of(GET_FEDERATION_ADDRESS, 11000L),
            Pair.of(GET_FEDERATION_SIZE, 10000L),
            Pair.of(GET_FEDERATION_THRESHOLD, 11000L),
            Pair.of(GET_FEDERATOR_PUBLIC_KEY, 10000L),
            Pair.of(GET_FEDERATION_CREATION_TIME, 10000L),
            Pair.of(GET_FEDERATION_CREATION_BLOCK_NUMBER, 10000L),
            Pair.of(GET_RETIRING_FEDERATION_ADDRESS, 3000L),
            Pair.of(GET_RETIRING_FEDERATION_SIZE, 3000L),
            Pair.of(GET_RETIRING_FEDERATION_THRESHOLD, 3000L),
            Pair.of(GET_RETIRING_FEDERATOR_PUBLIC_KEY, 3000L),
            Pair.of(GET_RETIRING_FEDERATION_CREATION_TIME, 3000L),
            Pair.of(GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER, 3000L),
            Pair.of(CREATE_FEDERATION, 11000L),
            Pair.of(ADD_FEDERATOR_PUBLIC_KEY, 13000L),
            Pair.of(COMMIT_FEDERATION, 38000L),
            Pair.of(ROLLBACK_FEDERATION, 12000L),
            Pair.of(GET_PENDING_FEDERATION_HASH, 3000L),
            Pair.of(GET_PENDING_FEDERATION_SIZE, 3000L),
            Pair.of(GET_PENDING_FEDERATOR_PUBLIC_KEY, 3000L),
            Pair.of(GET_LOCK_WHITELIST_SIZE, 16000L),
            Pair.of(GET_LOCK_WHITELIST_ADDRESS, 16000L),
            Pair.of(ADD_LOCK_WHITELIST_ADDRESS, 25000L),
            Pair.of(REMOVE_LOCK_WHITELIST_ADDRESS, 24000L),
            Pair.of(SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY, 24000L),
            Pair.of(GET_FEE_PER_KB, 2000L),
            Pair.of(VOTE_FEE_PER_KB, 10000L)
        ));

        if (blockchainConfig.isRfs55()) {
            functionsAndCosts.add(Pair.of(GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT, 20000L));
            functionsAndCosts.add(Pair.of(GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH, 20000L));
        }

        functionsAndCosts.stream().forEach((Object obj) -> {
            Pair<CallTransaction.Function, Long> spec = (Pair<CallTransaction.Function, Long>) obj;
            CallTransaction.Function func = spec.getLeft();
            Long cost = spec.getRight();
            functions.put(new ByteArrayWrapper(func.encodeSignature()),  func);
            functionCostMap.put(func, cost);
        });
    }

    @Override
    public byte[] execute(byte[] data) {
        try
        {
            BridgeParsedData bridgeParsedData = parseData(data);

            if (bridgeParsedData == null) {
                return null;
            }

            if (blockchainConfig.isRfs94() && GET_STATE_FOR_DEBUGGING.equals(bridgeParsedData.function)) {
                throw new NoSuchMethodException(GET_STATE_FOR_DEBUGGING.name + " call is not supported after Bamboo");
            }

            this.bridgeSupport = setup();

            // bridgeParsedData.function should be one of the CallTransaction.Function declared above.
            // If the user tries to call an non-existent function, parseData() will return null.
            Method m = this.getClass().getMethod(bridgeParsedData.function.name, Object[].class);

            Object result = null;

            try {
                result = m.invoke(this, new Object[]{bridgeParsedData.args});
            } catch (InvocationTargetException ite) {
                if (ite.getTargetException() instanceof BridgeIllegalArgumentException) {
                    logger.warn(ite.getTargetException().getMessage(), ite.getTargetException());
                    return null;
                } else {
                    throw ite;
                }
            }

            teardown();

            byte[] encodedResult = null;
            if (result != null) {
                encodedResult = bridgeParsedData.function.encodeOutputs(result);
            }
            return encodedResult;
        }
        catch(Exception ex) {
            logger.error(ex.getMessage(), ex);
            panicProcessor.panic("bridgeexecute", ex.getMessage());
            throw new RuntimeException("Exception executing bridge", ex);
        }
    }

    private BridgeSupport setup() throws Exception {
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(this.bridgeConstants, this.logs);
        return new BridgeSupport(this.config, repository, eventLogger, contractAddress, rskExecutionBlock);
    }

    private void teardown() throws IOException {
        bridgeSupport.save();
    }

    public void updateCollections(Object[] args)
    {
        logger.trace("updateCollections");

        try {
            bridgeSupport.updateCollections(rskTx);
        } catch (Exception e) {
            logger.warn("Exception onBlock", e);
            throw new RuntimeException("Exception onBlock", e);
        }
    }

    public void receiveHeaders(Object[] args)
    {
        logger.trace("receiveHeaders");

        Object[] btcBlockSerializedArray = (Object[]) args[0];
        BtcBlock[] btcBlockArray = new BtcBlock[btcBlockSerializedArray.length];
        for (int i = 0; i < btcBlockSerializedArray.length; i++) {
            byte[] btcBlockSerialized = (byte[]) btcBlockSerializedArray[i];
            try {
                BtcBlock header = bridgeConstants.getBtcParams().getDefaultSerializer().makeBlock(btcBlockSerialized);
                btcBlockArray[i] = header;
            } catch (ProtocolException e) {
                throw new BridgeIllegalArgumentException("Block " + i + " could not be parsed " + Hex.toHexString(btcBlockSerialized), e);
            }
        }
        try {
            bridgeSupport.receiveHeaders(btcBlockArray);
        } catch (Exception e) {
            logger.warn("Exception adding header", e);
            throw new RuntimeException("Exception adding header", e);
        }
    }

    public void registerBtcTransaction(Object[] args)
    {
        logger.trace("registerBtcTransaction");

        byte[] btcTxSerialized = (byte[]) args[0];
        BtcTransaction btcTx;
        try {
            btcTx = new BtcTransaction(bridgeConstants.getBtcParams(),btcTxSerialized);
        } catch (ProtocolException e) {
            throw new BridgeIllegalArgumentException("Transaction could not be parsed " + Hex.toHexString(btcTxSerialized), e);
        }
        int height = ((BigInteger)args[1]).intValue();

        byte[] pmtSerialized = (byte[]) args[2];
        PartialMerkleTree pmt;
        try {
            pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(),pmtSerialized, 0);
        } catch (ProtocolException e) {
            throw new BridgeIllegalArgumentException("PartialMerkleTree could not be parsed " + Hex.toHexString(pmtSerialized), e);
        }
        try {
            pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        } catch (VerificationException e) {
            throw new BridgeIllegalArgumentException("PartialMerkleTree could not be parsed " + Hex.toHexString(pmtSerialized), e);
        }
        try {
            bridgeSupport.registerBtcTransaction(rskTx, btcTx, height, pmt);
        } catch (Exception e) {
            logger.warn("Exception in registerBtcTransaction", e);
            throw new RuntimeException("Exception in registerBtcTransaction", e);
        }
    }

    public void releaseBtc(Object[] args)
    {
        logger.trace("releaseBtc");

        try {
            bridgeSupport.releaseBtc(rskTx);
        } catch (Program.OutOfGasException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Exception in releaseBtc", e);
            throw new RuntimeException("Exception in releaseBtc", e);
        }
    }

    public void addSignature(Object[] args)
    {
        logger.trace("addSignature");

        byte[] federatorPublicKeySerialized = (byte[]) args[0];
        BtcECKey federatorPublicKey;
        try {
            federatorPublicKey = BtcECKey.fromPublicOnly(federatorPublicKeySerialized);
        } catch (Exception e) {
            throw new BridgeIllegalArgumentException("Public key could not be parsed " + Hex.toHexString(federatorPublicKeySerialized), e);
        }
        Object[] signaturesObjectArray = (Object[]) args[1];
        if (signaturesObjectArray.length == 0) {
            throw new BridgeIllegalArgumentException("Signatures array is empty");
        }
        List<byte[]> signatures = new ArrayList<>();
        for (Object signatureObject : signaturesObjectArray) {
            byte[] signatureByteArray = (byte[])signatureObject;
            try {
                BtcECKey.ECDSASignature.decodeFromDER((byte[])signatureObject);
            } catch (Exception e) {
                throw new BridgeIllegalArgumentException("Signature could not be parsed " + Hex.toHexString(signatureByteArray), e);
            }
            signatures.add(signatureByteArray);
        }
        byte[] rskTxHash = (byte[]) args[2];
        if (rskTxHash.length!=32) {
            throw new BridgeIllegalArgumentException("Invalid rsk tx hash " + Hex.toHexString(rskTxHash));
        }
        try {
            bridgeSupport.addSignature(rskExecutionBlock.getNumber(), federatorPublicKey, signatures, rskTxHash);
        } catch (BridgeIllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Exception in addSignature", e);
            throw new RuntimeException("Exception in addSignature", e);
        }
    }

    public byte[] getStateForBtcReleaseClient(Object[] args)
    {
        logger.trace("getStateForBtcReleaseClient");

        try {
            return bridgeSupport.getStateForBtcReleaseClient();
        } catch (Exception e) {
            logger.warn("Exception in getStateForBtcReleaseClient", e);
            throw new RuntimeException("Exception in getStateForBtcReleaseClient", e);
        }
    }

    public byte[] getStateForDebugging(Object[] args)
    {
        logger.trace("getStateForDebugging");

        try {
            return bridgeSupport.getStateForDebugging();
        } catch (Exception e) {
            logger.warn("Exception in getStateForDebugging", e);
            throw new RuntimeException("Exception in getStateForDebugging", e);
        }
    }

    public Integer getBtcBlockchainBestChainHeight(Object[] args)
    {
        logger.trace("getBtcBlockchainBestChainHeight");

        try {
            return bridgeSupport.getBtcBlockchainBestChainHeight();
        } catch (Exception e) {
            logger.warn("Exception in getBtcBlockchainBestChainHeight", e);
            throw new RuntimeException("Exception in getBtcBlockchainBestChainHeight", e);
        }
    }

    public Integer getBtcBlockchainInitialBlockHeight(Object[] args)
    {
        logger.trace("getBtcBlockchainInitialBlockHeight");

        return bridgeSupport.getBtcBlockchainInitialBlockHeight();
    }

    @Deprecated
    public Object[] getBtcBlockchainBlockLocator(Object[] args)
    {
        logger.trace("getBtcBlockchainBlockLocator");

        try {
            List<Sha256Hash> blockLocatorList = bridgeSupport.getBtcBlockchainBlockLocator();
            Object[] blockLocatorArray = new Object[blockLocatorList.size()];
            int i = 0;
            for (Sha256Hash blockHash: blockLocatorList) {
                blockLocatorArray[i] = blockHash.toString();
                i++;
            }
            return blockLocatorArray;
        } catch (Exception e) {
            logger.warn("Exception in getBtcBlockchainBlockLocator", e);
            throw new RuntimeException("Exception in getBtcBlockchainBlockLocator", e);
        }
    }

    public byte[] getBtcBlockchainBlockHashAtDepth(Object[] args)
    {
        logger.trace("getBtcBlockchainBlockHashAtDepth");

        int depth = ((BigInteger) args[0]).intValue();
        Sha256Hash blockHash = null;
        try {
            blockHash = bridgeSupport.getBtcBlockchainBlockHashAtDepth(depth);
        } catch (BlockStoreException e) {
            logger.warn("Exception in getBtcBlockchainBlockHashAtDepth", e);
            throw new RuntimeException("Exception in getBtcBlockchainBlockHashAtDepth", e);
        }

        return blockHash.getBytes();
    }

    public Long getMinimumLockTxValue(Object[] args)
    {
        logger.trace("getMinimumLockTxValue");
        return bridgeSupport.getMinimumLockTxValue().getValue();
    }

    public Boolean isBtcTxHashAlreadyProcessed(Object[] args)
    {
        logger.trace("isBtcTxHashAlreadyProcessed");

        try {
            Sha256Hash btcTxHash = Sha256Hash.wrap((String) args[0]);
            return bridgeSupport.isBtcTxHashAlreadyProcessed(btcTxHash);
        } catch (Exception e) {
            logger.warn("Exception in isBtcTxHashAlreadyProcessed", e);
            throw new RuntimeException("Exception in isBtcTxHashAlreadyProcessed", e);
        }
    }

    public Long getBtcTxHashProcessedHeight(Object[] args)
    {
        logger.trace("getBtcTxHashProcessedHeight");

        try {
            Sha256Hash btcTxHash = Sha256Hash.wrap((String) args[0]);
            return bridgeSupport.getBtcTxHashProcessedHeight(btcTxHash);
        } catch (Exception e) {
            logger.warn("Exception in getBtcTxHashProcessedHeight", e);
            throw new RuntimeException("Exception in getBtcTxHashProcessedHeight", e);
        }
    }

    public String getFederationAddress(Object[] args)
    {
        logger.trace("getFederationAddress");

        return bridgeSupport.getFederationAddress().toString();
    }

    public Integer getFederationSize(Object[] args)
    {
        logger.trace("getFederationSize");

        return bridgeSupport.getFederationSize();
    }

    public Integer getFederationThreshold(Object[] args)
    {
        logger.trace("getFederationThreshold");

        return bridgeSupport.getFederationThreshold();
    }

    public byte[] getFederatorPublicKey(Object[] args)
    {
        logger.trace("getFederatorPublicKey");

        int index = ((BigInteger) args[0]).intValue();
        return bridgeSupport.getFederatorPublicKey(index);
    }

    public Long getFederationCreationTime(Object[] args)
    {
        logger.trace("getFederationCreationTime");

        // Return the creation time in milliseconds from the epoch
        return bridgeSupport.getFederationCreationTime().toEpochMilli();
    }

    public long getFederationCreationBlockNumber(Object[] args) {
        logger.trace("getFederationCreationBlockNumber");
        return bridgeSupport.getFederationCreationBlockNumber();
    }

    public String getRetiringFederationAddress(Object[] args)
    {
        logger.trace("getRetiringFederationAddress");

        Address address = bridgeSupport.getRetiringFederationAddress();

        if (address == null) {
            // When there's no address, empty string is returned
            return "";
        }

        return address.toString();
    }

    public Integer getRetiringFederationSize(Object[] args)
    {
        logger.trace("getRetiringFederationSize");

        return bridgeSupport.getRetiringFederationSize();
    }

    public Integer getRetiringFederationThreshold(Object[] args)
    {
        logger.trace("getRetiringFederationThreshold");

        return bridgeSupport.getRetiringFederationThreshold();
    }

    public byte[] getRetiringFederatorPublicKey(Object[] args)
    {
        logger.trace("getRetiringFederatorPublicKey");

        int index = ((BigInteger) args[0]).intValue();
        byte[] publicKey = bridgeSupport.getRetiringFederatorPublicKey(index);

        if (publicKey == null) {
            // Empty array is returned when public key is not found or there's no retiring federation
            return new byte[]{};
        }

        return publicKey;
    }

    public Long getRetiringFederationCreationTime(Object[] args)
    {
        logger.trace("getRetiringFederationCreationTime");

        Instant creationTime = bridgeSupport.getRetiringFederationCreationTime();

        if (creationTime == null) {
            // -1 is returned when no retiring federation
            return -1L;
        }

        // Return the creation time in milliseconds from the epoch
        return creationTime.toEpochMilli();
    }

    public long getRetiringFederationCreationBlockNumber(Object[] args) {
        logger.trace("getRetiringFederationCreationBlockNumber");
        return bridgeSupport.getRetiringFederationCreationBlockNumber();
    }

    public Integer createFederation(Object[] args)
    {
        logger.trace("createFederation");

        return bridgeSupport.voteFederationChange(
                rskTx,
                new ABICallSpec("create", new byte[][]{})
        );
    }

    public Integer addFederatorPublicKey(Object[] args)
    {
        logger.trace("addFederatorPublicKey");

        byte[] publicKeyBytes;
        try {
            publicKeyBytes = (byte[]) args[0];
        } catch (Exception e) {
            logger.warn("Exception in addFederatorPublicKey: {}", e.getMessage());
            return -10;
        }

        return bridgeSupport.voteFederationChange(
                rskTx,
                new ABICallSpec("add", new byte[][]{ publicKeyBytes })
        );
    }

    public Integer commitFederation(Object[] args)
    {
        logger.trace("commitFederation");

        byte[] hash;
        try {
            hash = (byte[]) args[0];
        } catch (Exception e) {
            logger.warn("Exception in commitFederation: {}", e.getMessage());
            return -10;
        }

        return bridgeSupport.voteFederationChange(
                rskTx,
                new ABICallSpec("commit", new byte[][]{ hash })
        );
    }

    public Integer rollbackFederation(Object[] args)
    {
        logger.trace("rollbackFederation");

        return bridgeSupport.voteFederationChange(
                rskTx,
                new ABICallSpec("rollback", new byte[][]{})
        );
    }

    public byte[] getPendingFederationHash(Object[] args)
    {
        logger.trace("getPendingFederationHash");

        byte[] hash = bridgeSupport.getPendingFederationHash();

        if (hash == null) {
            // Empty array is returned when pending federation is not present
            return new byte[]{};
        }

        return hash;
    }

    public Integer getPendingFederationSize(Object[] args)
    {
        logger.trace("getPendingFederationSize");

        return bridgeSupport.getPendingFederationSize();
    }

    public byte[] getPendingFederatorPublicKey(Object[] args)
    {
        logger.trace("getPendingFederatorPublicKey");

        int index = ((BigInteger) args[0]).intValue();
        byte[] publicKey = bridgeSupport.getPendingFederatorPublicKey(index);

        if (publicKey == null) {
            // Empty array is returned when public key is not found
            return new byte[]{};
        }

        return publicKey;
    }

    public Integer getLockWhitelistSize(Object[] args)
    {
        logger.trace("getLockWhitelistSize");

        return bridgeSupport.getLockWhitelistSize();
    }

    public String getLockWhitelistAddress(Object[] args)
    {
        logger.trace("getLockWhitelistAddress");

        int index = ((BigInteger) args[0]).intValue();
        String address = bridgeSupport.getLockWhitelistAddress(index);

        if (address == null) {
            // Empty string is returned when address is not found
            return "";
        }

        return address;
    }

    public Integer addLockWhitelistAddress(Object[] args)
    {
        logger.trace("addLockWhitelistAddress");

        String addressBase58;
        BigInteger maxTransferValue;
        try {
            addressBase58 = (String) args[0];
            maxTransferValue = (BigInteger) args[1];
        } catch (Exception e) {
            logger.warn("Exception in addLockWhitelistAddress: {}", e.getMessage());
            return 0;
        }

        return bridgeSupport.addLockWhitelistAddress(rskTx, addressBase58, maxTransferValue);
    }

    public Integer removeLockWhitelistAddress(Object[] args)
    {
        logger.trace("removeLockWhitelistAddress");

        String addressBase58;
        try {
            addressBase58 = (String) args[0];
        } catch (Exception e) {
            logger.warn("Exception in removeLockWhitelistAddress: {}", e.getMessage());
            return 0;
        }

        return bridgeSupport.removeLockWhitelistAddress(rskTx, addressBase58);
    }

    public Integer setLockWhitelistDisableBlockDelay(Object[] args) {
        logger.trace("setLockWhitelistDisableBlockDelay");
        BigInteger lockWhitelistDisableBlockDelay = (BigInteger) args[0];
        return bridgeSupport.setLockWhitelistDisableBlockDelay(rskTx, lockWhitelistDisableBlockDelay);
    }

    public Integer voteFeePerKbChange(Object[] args)
    {
        logger.trace("voteFeePerKbChange");

        Coin feePerKb;
        try {
            feePerKb = Coin.valueOf(((BigInteger) args[0]).longValueExact());
        } catch (Exception e) {
            logger.warn("Exception in voteFeePerKbChange: {}", e);
            return -10;
        }

        return bridgeSupport.voteFeePerKbChange(rskTx, feePerKb);
    }

    public long getFeePerKb(Object[] args)
    {
        logger.trace("getFeePerKb");

        return bridgeSupport.getFeePerKb().getValue();
    }
}

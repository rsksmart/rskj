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

import co.rsk.config.BridgeConstants;
import co.rsk.panic.PanicProcessor;
import com.google.common.annotations.VisibleForTesting;
import co.rsk.bitcoinj.core.*;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
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
    // Returns an array of block hashes known by the bridge contract. Federators can use this to find what is the latest block in the mainchain the bridge has.
    // The goal of this function is to help synchronize bridge and federators blockchains.
    // Protocol inspired by bitcoin sync protocol, see block locator in https://en.bitcoin.it/wiki/Protocol_documentation#getheaders
    public static final CallTransaction.Function GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR = CallTransaction.Function.fromSignature("getBtcBlockchainBlockLocator", new String[]{}, new String[]{"string[]"});
    // Returns an array of btc txs hashes the bridge already knows about
    public static final CallTransaction.Function GET_BTC_TX_HASHES_ALREADY_PROCESSED = CallTransaction.Function.fromSignature("getBtcTxHashesAlreadyProcessed", new String[]{}, new String[]{"string[]"});
    // Returns the federation bitcoin address
    public static final CallTransaction.Function GET_FEDERATION_ADDRESS = CallTransaction.Function.fromSignature("getFederationAddress", new String[]{}, new String[]{"string"});
    // Returns the minimum amount of satoshis a user should send to the federation.
    public static final CallTransaction.Function GET_MINIMUM_LOCK_TX_VALUE = CallTransaction.Function.fromSignature("getMinimumLockTxValue", new String[]{}, new String[]{"int"});

    private static Map<CallTransaction.Function, Long> functionCostMap = new HashMap<>();

    private Map<ByteArrayWrapper, CallTransaction.Function> functions;

    private BridgeConstants bridgeConstants;

    private org.ethereum.core.Transaction rskTx;
    private org.ethereum.core.Block rskExecutionBlock;
    private org.ethereum.db.BlockStore rskBlockStore;
    private ReceiptStore rskReceiptStore;
    private Repository repository;
    private List<LogInfo> logs;

    private BridgeSupport bridgeSupport;

    public Bridge(String contractAddress) {
        this.contractAddress = contractAddress;

        this.functions = new HashMap<>();
        this.functions.put(new ByteArrayWrapper(UPDATE_COLLECTIONS.encodeSignature()),  UPDATE_COLLECTIONS);
        this.functions.put(new ByteArrayWrapper(RECEIVE_HEADERS.encodeSignature()),     RECEIVE_HEADERS);
        this.functions.put(new ByteArrayWrapper(REGISTER_BTC_TRANSACTION.encodeSignature()), REGISTER_BTC_TRANSACTION);
        this.functions.put(new ByteArrayWrapper(RELEASE_BTC.encodeSignature()),     RELEASE_BTC);
        this.functions.put(new ByteArrayWrapper(ADD_SIGNATURE.encodeSignature()),   ADD_SIGNATURE);
        this.functions.put(new ByteArrayWrapper(GET_STATE_FOR_BTC_RELEASE_CLIENT.encodeSignature()), GET_STATE_FOR_BTC_RELEASE_CLIENT);
        this.functions.put(new ByteArrayWrapper(GET_STATE_FOR_DEBUGGING.encodeSignature()), GET_STATE_FOR_DEBUGGING);
        this.functions.put(new ByteArrayWrapper(GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT.encodeSignature()), GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT);
        this.functions.put(new ByteArrayWrapper(GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.encodeSignature()),     GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR);
        this.functions.put(new ByteArrayWrapper(GET_BTC_TX_HASHES_ALREADY_PROCESSED.encodeSignature()),  GET_BTC_TX_HASHES_ALREADY_PROCESSED);
        this.functions.put(new ByteArrayWrapper(GET_FEDERATION_ADDRESS.encodeSignature()),      GET_FEDERATION_ADDRESS);
        this.functions.put(new ByteArrayWrapper(GET_MINIMUM_LOCK_TX_VALUE.encodeSignature()),   GET_MINIMUM_LOCK_TX_VALUE);

        bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();

        functionCostMap.put(UPDATE_COLLECTIONS,                    50001L);
        functionCostMap.put(RECEIVE_HEADERS,                       50002L);
        functionCostMap.put(REGISTER_BTC_TRANSACTION,              50003L);
        functionCostMap.put(RELEASE_BTC,                           50004L);
        functionCostMap.put(ADD_SIGNATURE,                         50005L);
        functionCostMap.put(GET_STATE_FOR_BTC_RELEASE_CLIENT,      50006L);
        functionCostMap.put(GET_STATE_FOR_DEBUGGING,               50007L);
        functionCostMap.put(GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT,  50008L);
        functionCostMap.put(GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR,      50009L);
        functionCostMap.put(GET_BTC_TX_HASHES_ALREADY_PROCESSED,   50010L);
        functionCostMap.put(GET_FEDERATION_ADDRESS,                50011L);
        functionCostMap.put(GET_MINIMUM_LOCK_TX_VALUE,             50012L);
    }

    @Override
    public long getGasForData(byte[] data) {
        // Required gas is arbitrary
        if (BridgeUtils.isFreeBridgeTx(rskTx, rskExecutionBlock.getNumber())) {
            return 0;
        }
        BridgeParsedData bridgeParsedData = parseData(data);

        Long functionCost;
        Long totalCost;
        if (bridgeParsedData == null) {
            functionCost = 50000L;
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
    public void init(org.ethereum.core.Transaction rskTx, org.ethereum.core.Block rskExecutionBlock, Repository repository, org.ethereum.db.BlockStore rskBlockStore, ReceiptStore rskReceiptStore, List<LogInfo> logs) {
        this.rskTx = rskTx;
        this.rskExecutionBlock = rskExecutionBlock;
        this.rskBlockStore = rskBlockStore;
        this.rskReceiptStore = rskReceiptStore;
        this.repository = repository;
        this.logs = logs;
    }

    @Override
    public byte[] execute(byte[] data) {
        try
        {
            BridgeParsedData bridgeParsedData = parseData(data);

            if (bridgeParsedData == null)
                return null;

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
        return new BridgeSupport(repository, contractAddress, rskExecutionBlock, rskReceiptStore, rskBlockStore, logs);
    }

    private void teardown() throws IOException {
        bridgeSupport.save();
    }

    public void updateCollections(Object[] args)
    {
        logger.trace("updateCollections");

        try {
            bridgeSupport.updateCollections();
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
            bridgeSupport.registerBtcTransaction(btcTx, height, pmt);
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
            logger.warn("Exception in releaseBtc", e);
            throw new RuntimeException("Exception in releaseBtc", e);
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

    public Object[] getBtcTxHashesAlreadyProcessed(Object[] args)
    {
        logger.trace("getBtcTxHashesAlreadyProcessed");

        try {
            Set<Sha256Hash> txHashSet = bridgeSupport.getBtcTxHashesAlreadyProcessed();
            Object[] txHashArray = new Object[txHashSet.size()];
            int i = 0;
            for (Sha256Hash txHash: txHashSet) {
                txHashArray[i] = txHash.toString();
                i++;
            }
            return txHashArray;
        } catch (Exception e) {
            logger.warn("Exception in getBtcTxHashesAlreadyProcessed", e);
            throw new RuntimeException("Exception in getBtcTxHashesAlreadyProcessed", e);
        }
    }

    public String getFederationAddress(Object[] args)
    {
        logger.trace("getFederationAddress");
        return bridgeSupport.getFederationAddress().toString();
    }


    public Long getMinimumLockTxValue(Object[] args)
    {
        logger.trace("getMinimumLockTxValue");
        return bridgeSupport.getMinimumLockTxValue().getValue();
    }

}

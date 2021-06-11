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
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.utils.BtcTransactionFormatUtils;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.Program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Precompiled contract that manages the 2 way peg between bitcoin and RSK.
 * This class is just a wrapper, actual functionality is found in BridgeSupport.
 * @author Oscar Guindzberg
 */
public class Bridge extends PrecompiledContracts.PrecompiledContract {

    private static final Logger logger = LoggerFactory.getLogger("bridge");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    // No parameters
    public static final CallTransaction.Function UPDATE_COLLECTIONS = BridgeMethods.UPDATE_COLLECTIONS.getFunction();
    // Parameters: an array of bitcoin blocks serialized with the bitcoin wire protocol format
    public static final CallTransaction.Function RECEIVE_HEADERS = BridgeMethods.RECEIVE_HEADERS.getFunction();
    // Parameters: a header of bitcoin blocks serialized with the bitcoin wire protocol format
    public static final CallTransaction.Function RECEIVE_HEADER = BridgeMethods.RECEIVE_HEADER.getFunction();
    // Parameters:
    // - A bitcoin tx, serialized with the bitcoin wire protocol format
    // - The bitcoin block height that contains the tx
    // - A merkle tree that shows the tx is included in that block, serialized with the bitcoin wire protocol format.
    public static final CallTransaction.Function REGISTER_BTC_TRANSACTION = BridgeMethods.REGISTER_BTC_TRANSACTION.getFunction();
    // No parameters, the current rsk tx is used as input.
    public static final CallTransaction.Function RELEASE_BTC = BridgeMethods.RELEASE_BTC.getFunction();
    // Parameters:
    // Federator public key.
    // Transaction signature array, one for each btc tx input.
    // Rsk tx hash of the tx that required the release of funds.
    public static final CallTransaction.Function ADD_SIGNATURE = BridgeMethods.ADD_SIGNATURE.getFunction();
    // Returns a StateForFederator encoded in RLP
    public static final CallTransaction.Function GET_STATE_FOR_BTC_RELEASE_CLIENT = BridgeMethods.GET_STATE_FOR_BTC_RELEASE_CLIENT.getFunction();
    // Returns a BridgeState encoded in RLP
    public static final CallTransaction.Function GET_STATE_FOR_DEBUGGING = BridgeMethods.GET_STATE_FOR_DEBUGGING.getFunction();
    // Return the bitcoin blockchain best chain height know by the bridge contract
    public static final CallTransaction.Function GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT = BridgeMethods.GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT.getFunction();
    // Returns an array of block hashes known by the bridge contract. Federators can use this to find what is the latest block in the mainchain the bridge has.
    // The goal of this function is to help synchronize bridge and federators blockchains.
    // Protocol inspired by bitcoin sync protocol, see block locator in https://en.bitcoin.it/wiki/Protocol_documentation#getheaders
    public static final CallTransaction.Function GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR = BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR.getFunction();
    // Return the height of the initial block stored in the bridge's bitcoin blockchain
    public static final CallTransaction.Function GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT = BridgeMethods.GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT.getFunction();
    // Returns the block hash of the bridge contract's best chain at the given depth, meaning depth zero will
    // yield the best chain head hash and depth one will yield its parent hash, and so on and so forth.
    // Federators use this to find what is the latest block in the mainchain the bridge has
    // (replacing the need for getBtcBlockchainBlockLocator).
    // The goal of this function is to help synchronize bridge and federators blockchains.
    public static final CallTransaction.Function GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH = BridgeMethods.GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH.getFunction();
    // Returns the confirmations number of the block for the given transaction. If its not valid or its not part of the main chain it returns a negative number
    // The goal of this function is to help contracts can use this to validate BTC transactions
    public static final CallTransaction.Function GET_BTC_TRANSACTION_CONFIRMATIONS = BridgeMethods.GET_BTC_TRANSACTION_CONFIRMATIONS.getFunction();
    // Returns the minimum amount of satoshis a user should send to the federation.
    public static final CallTransaction.Function GET_MINIMUM_LOCK_TX_VALUE = BridgeMethods.GET_MINIMUM_LOCK_TX_VALUE.getFunction();

    // Returns whether a given btc tx hash was already processed by the bridge
    public static final CallTransaction.Function IS_BTC_TX_HASH_ALREADY_PROCESSED = BridgeMethods.IS_BTC_TX_HASH_ALREADY_PROCESSED.getFunction();
    // Returns whether a given btc tx hash was already processed by the bridge
    public static final CallTransaction.Function GET_BTC_TX_HASH_PROCESSED_HEIGHT = BridgeMethods.GET_BTC_TX_HASH_PROCESSED_HEIGHT.getFunction();

    // Returns the federation bitcoin address
    public static final CallTransaction.Function GET_FEDERATION_ADDRESS = BridgeMethods.GET_FEDERATION_ADDRESS.getFunction();
    // Returns the number of federates in the currently active federation
    public static final CallTransaction.Function GET_FEDERATION_SIZE = BridgeMethods.GET_FEDERATION_SIZE.getFunction();
    // Returns the number of minimum required signatures from the currently active federation
    public static final CallTransaction.Function GET_FEDERATION_THRESHOLD = BridgeMethods.GET_FEDERATION_THRESHOLD.getFunction();
    // Returns the public key of the federator at the specified index
    public static final CallTransaction.Function GET_FEDERATOR_PUBLIC_KEY = BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction();
    // Returns the public key of given type of the federator at the specified index
    public static final CallTransaction.Function GET_FEDERATOR_PUBLIC_KEY_OF_TYPE = BridgeMethods.GET_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction();
    // Returns the creation time of the federation
    public static final CallTransaction.Function GET_FEDERATION_CREATION_TIME = BridgeMethods.GET_FEDERATION_CREATION_TIME.getFunction();
    // Returns the block number of the creation of the federation
    public static final CallTransaction.Function GET_FEDERATION_CREATION_BLOCK_NUMBER = BridgeMethods.GET_FEDERATION_CREATION_BLOCK_NUMBER.getFunction();

    // Returns the retiring federation bitcoin address
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_ADDRESS = BridgeMethods.GET_RETIRING_FEDERATION_ADDRESS.getFunction();
    // Returns the number of federates in the retiring federation
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_SIZE = BridgeMethods.GET_RETIRING_FEDERATION_SIZE.getFunction();
    // Returns the number of minimum required signatures from the retiring federation
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_THRESHOLD = BridgeMethods.GET_RETIRING_FEDERATION_THRESHOLD.getFunction();
    // Returns the public key of the retiring federation's federator at the specified index
    public static final CallTransaction.Function GET_RETIRING_FEDERATOR_PUBLIC_KEY = BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY.getFunction();
    // Returns the public key of given type of the retiring federation's federator at the specified index
    public static final CallTransaction.Function GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE = BridgeMethods.GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction();
    // Returns the creation time of the retiring federation
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_CREATION_TIME = BridgeMethods.GET_RETIRING_FEDERATION_CREATION_TIME.getFunction();
    // Returns the block number of the creation of the retiring federation
    public static final CallTransaction.Function GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER = BridgeMethods.GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER.getFunction();

    // Creates a new pending federation and returns its id
    public static final CallTransaction.Function CREATE_FEDERATION = BridgeMethods.CREATE_FEDERATION.getFunction();
    // Adds the given key to the current pending federation
    public static final CallTransaction.Function ADD_FEDERATOR_PUBLIC_KEY = BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY.getFunction();
    // Adds the given key to the current pending federation (multiple-key version)
    public static final CallTransaction.Function ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY = BridgeMethods.ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY.getFunction();
    // Commits the currently pending federation
    public static final CallTransaction.Function COMMIT_FEDERATION = BridgeMethods.COMMIT_FEDERATION.getFunction();
    // Rolls back the currently pending federation
    public static final CallTransaction.Function ROLLBACK_FEDERATION = BridgeMethods.ROLLBACK_FEDERATION.getFunction();

    // Returns the current pending federation's hash
    public static final CallTransaction.Function GET_PENDING_FEDERATION_HASH = BridgeMethods.GET_PENDING_FEDERATION_HASH.getFunction();
    // Returns the number of federates in the current pending federation
    public static final CallTransaction.Function GET_PENDING_FEDERATION_SIZE = BridgeMethods.GET_PENDING_FEDERATION_SIZE.getFunction();
    // Returns the public key of the federator at the specified index for the current pending federation
    public static final CallTransaction.Function GET_PENDING_FEDERATOR_PUBLIC_KEY = BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY.getFunction();
    // Returns the public key of given type the federator at the specified index for the current pending federation
    public static final CallTransaction.Function GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE = BridgeMethods.GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE.getFunction();

    // Returns the lock whitelist size
    public static final CallTransaction.Function GET_LOCK_WHITELIST_SIZE = BridgeMethods.GET_LOCK_WHITELIST_SIZE.getFunction();
    // Returns the lock whitelist address stored at the specified index
    public static final CallTransaction.Function GET_LOCK_WHITELIST_ADDRESS = BridgeMethods.GET_LOCK_WHITELIST_ADDRESS.getFunction();
    // Returns the lock whitelist entry stored at the specified address
    public static final CallTransaction.Function GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS = BridgeMethods.GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS.getFunction();
    // Adds the given address to the lock whitelist
    public static final CallTransaction.Function ADD_LOCK_WHITELIST_ADDRESS = BridgeMethods.ADD_LOCK_WHITELIST_ADDRESS.getFunction();
    // Adds the given address to the lock whitelist in "one-off" mode
    public static final CallTransaction.Function ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS = BridgeMethods.ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS.getFunction();
    // Adds the given address to the lock whitelist in "unlimited" mode
    public static final CallTransaction.Function ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS = BridgeMethods.ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS.getFunction();
    // Adds the given address to the lock whitelist
    public static final CallTransaction.Function REMOVE_LOCK_WHITELIST_ADDRESS = BridgeMethods.REMOVE_LOCK_WHITELIST_ADDRESS.getFunction();

    public static final CallTransaction.Function SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY = BridgeMethods.SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY.getFunction();

    // Returns the current fee per kb
    public static final CallTransaction.Function GET_FEE_PER_KB = BridgeMethods.GET_FEE_PER_KB.getFunction();
    // Adds the given key to the current pending federation
    public static final CallTransaction.Function VOTE_FEE_PER_KB = BridgeMethods.VOTE_FEE_PER_KB.getFunction();

    // Increases the peg locking cap
    public static final CallTransaction.Function INCREASE_LOCKING_CAP = BridgeMethods.INCREASE_LOCKING_CAP.getFunction();
    // Gets the peg locking cap
    public static final CallTransaction.Function GET_LOCKING_CAP = BridgeMethods.GET_LOCKING_CAP.getFunction();
    public static final CallTransaction.Function REGISTER_BTC_COINBASE_TRANSACTION = BridgeMethods.REGISTER_BTC_COINBASE_TRANSACTION.getFunction();
    public static final CallTransaction.Function HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION = BridgeMethods.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.getFunction();
    public static final CallTransaction.Function REGISTER_FAST_BRIDGE_BTC_TRANSACTION = BridgeMethods.REGISTER_FAST_BRIDGE_BTC_TRANSACTION.getFunction();

    public static final int LOCK_WHITELIST_UNLIMITED_MODE_CODE = 0;
    public static final int LOCK_WHITELIST_ENTRY_NOT_FOUND_CODE = -1;
    public static final int LOCK_WHITELIST_INVALID_ADDRESS_FORMAT_ERROR_CODE = -2;

    // Log topics used by Bridge Contract pre RSKIP146
    public static final DataWord RELEASE_BTC_TOPIC = DataWord.fromString("release_btc_topic");
    public static final DataWord UPDATE_COLLECTIONS_TOPIC = DataWord.fromString("update_collections_topic");
    public static final DataWord ADD_SIGNATURE_TOPIC = DataWord.fromString("add_signature_topic");
    public static final DataWord COMMIT_FEDERATION_TOPIC = DataWord.fromString("commit_federation_topic");
    
    private static final Integer RECEIVE_HEADER_ERROR_SIZE_MISTMATCH = -20;

    private final Constants constants;
    private final BridgeConstants bridgeConstants;
    private final ActivationConfig activationConfig;

    private ActivationConfig.ForBlock activations;
    private org.ethereum.core.Transaction rskTx;

    private BridgeSupport bridgeSupport;
    private BridgeSupportFactory bridgeSupportFactory;

    public Bridge(RskAddress contractAddress, Constants constants, ActivationConfig activationConfig,
                  BridgeSupportFactory bridgeSupportFactory) {
        this.bridgeSupportFactory = bridgeSupportFactory;
        this.contractAddress = contractAddress;
        this.constants = constants;
        this.bridgeConstants = constants.getBridgeConstants();
        this.activationConfig = activationConfig;
    }

    @Override
    public long getGasForData(byte[] data) {
        if (!activations.isActive(ConsensusRule.RSKIP88) && BridgeUtils.isContractTx(rskTx)) {
            logger.warn("Call from contract before Orchid");
            throw new NullPointerException();
        }

        if (BridgeUtils.isFreeBridgeTx(rskTx, constants, activations)) {
            return 0;
        }

        BridgeParsedData bridgeParsedData = parseData(data);

        long functionCost;
        long totalCost;
        if (bridgeParsedData == null) {
            functionCost = BridgeMethods.RELEASE_BTC.getCost(this, activations, new Object[0]);
            totalCost = functionCost;
        } else {
            functionCost = bridgeParsedData.bridgeMethod.getCost(this, activations, bridgeParsedData.args);
            int dataCost = data == null ? 0 : data.length * 2;

            totalCost = functionCost + dataCost;
        }

        return totalCost;
    }

    @VisibleForTesting
    BridgeParsedData parseData(byte[] data) {
        BridgeParsedData bridgeParsedData = new BridgeParsedData();

        if (data != null && (data.length >= 1 && data.length <= 3)) {
            logger.warn("Invalid function signature {}.", ByteUtil.toHexString(data));
            return null;
        }

        if (data == null || data.length == 0) {
            bridgeParsedData.bridgeMethod = BridgeMethods.RELEASE_BTC;
            bridgeParsedData.args = new Object[]{};
        } else {
            byte[] functionSignature = Arrays.copyOfRange(data, 0, 4);
            Optional<BridgeMethods> invokedMethod = BridgeMethods.findBySignature(functionSignature);
            if (!invokedMethod.isPresent()) {
                logger.warn("Invalid function signature {}.", ByteUtil.toHexString(functionSignature));
                return null;
            }
            bridgeParsedData.bridgeMethod = invokedMethod.get();
            try {
                bridgeParsedData.args = bridgeParsedData.bridgeMethod.getFunction().decode(data);
            } catch (Exception e) {
                logger.warn("Invalid function arguments {} for function {}.", ByteUtil.toHexString(data), ByteUtil.toHexString(functionSignature));
                return null;
            }
        }

        if (!bridgeParsedData.bridgeMethod.isEnabled(activations)) {
            logger.warn("'{}' is not enabled to run", bridgeParsedData.bridgeMethod.name());
            return null;
        }

        return bridgeParsedData;
    }

    // Parsed rsk transaction data field
    private static class BridgeParsedData {
        public BridgeMethods bridgeMethod;
        public Object[] args;
    }

    @Override
    public void init(Transaction rskTx, Block rskExecutionBlock, Repository repository, BlockStore rskBlockStore, ReceiptStore rskReceiptStore, List<LogInfo> logs) {
        this.activations = activationConfig.forBlock(rskExecutionBlock.getNumber());
        this.rskTx = rskTx;

        this.bridgeSupport = bridgeSupportFactory.newInstance(
                repository,
                rskExecutionBlock,
                contractAddress,
                logs);
    }

    @Override
    public List<ProgramSubtrace> getSubtraces() {
        return this.bridgeSupport.getSubtraces();
    }

    @Override
    public byte[] execute(byte[] data) throws VMException {
        try {
            // Preliminary validation: the transaction on which we execute cannot be null
            if (rskTx == null) {
                throw new VMException("Rsk Transaction is null");
            }

            BridgeParsedData bridgeParsedData = parseData(data);

            // Function parsing from data returned null => invalid function selected, halt!
            if (bridgeParsedData == null) {
                String errorMessage = String.format("Invalid data given: %s.", ByteUtil.toHexString(data));
                logger.info(errorMessage);
                if (activations.isActive(ConsensusRule.RSKIP88)) {
                    throw new BridgeIllegalArgumentException(errorMessage);
                }

                return null;
            }

            // If this is not a local call, then first check whether the function
            // allows for non-local calls
            if (activations.isActive(ConsensusRule.RSKIP88) &&
                !isLocalCall() &&
                bridgeParsedData.bridgeMethod.onlyAllowsLocalCalls(this, bridgeParsedData.args)) {

                String errorMessage = String.format("Non-local-call to %s. Returning without execution.", bridgeParsedData.bridgeMethod.getFunction().name);
                logger.info(errorMessage);
                throw new BridgeIllegalArgumentException(errorMessage);
            }

            Optional<?> result;
            try {
                // bridgeParsedData.function should be one of the CallTransaction.Function declared above.
                // If the user tries to call an non-existent function, parseData() will return null.
                result = bridgeParsedData.bridgeMethod.getExecutor().execute(this, bridgeParsedData.args);
            } catch (BridgeIllegalArgumentException ex) {
                String errorMessage = String.format("Error executing: %s", bridgeParsedData.bridgeMethod);
                logger.warn(errorMessage, ex);
                if (activations.isActive(ConsensusRule.RSKIP88)) {
                    throw new BridgeIllegalArgumentException(errorMessage);
                }

                return null;
            }

            teardown();

            return result.map(bridgeParsedData.bridgeMethod.getFunction()::encodeOutputs).orElse(null);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            panicProcessor.panic("bridgeexecute", ex.getMessage());
            throw new VMException(String.format("Exception executing bridge: %s", ex.getMessage()), ex);
        }
    }

    private void teardown() throws IOException {
        bridgeSupport.save();
    }

    public void updateCollections(Object[] args) throws VMException {
        logger.trace("updateCollections");

        try {
            bridgeSupport.updateCollections(rskTx);
        } catch (Exception e) {
            logger.warn("Exception onBlock", e);
            throw new VMException("Exception onBlock", e);
        }
    }

    public boolean receiveHeadersIsPublic() {
        return (activations.isActive(ConsensusRule.RSKIP124)
                && (!activations.isActive(ConsensusRule.RSKIP200)));
    }

    public long receiveHeadersGetCost(Object[] args) {
        // Old, private method fixed cost. Only applies before the corresponding RSKIP
        if (!activations.isActive(ConsensusRule.RSKIP124)) {
            return 22_000L;
        }

        final long BASE_COST = activations.isActive(ConsensusRule.RSKIP132) ? 25_000L : 66_000L;
        if (args == null) {
            return BASE_COST;
        }

        final int numberOfHeaders = ((Object[]) args[0]).length;

        if (numberOfHeaders == 0) {
            return BASE_COST;
        }
        // Dynamic cost based on the number of headers
        // We add each additional header times 1650 to the base cost
        final long COST_PER_ADDITIONAL_HEADER = activations.isActive(ConsensusRule.RSKIP132) ? 3_500 : 1_650;
        return BASE_COST + (numberOfHeaders - 1) * COST_PER_ADDITIONAL_HEADER;
    }

    public void receiveHeaders(Object[] args) throws VMException {
        logger.trace("receiveHeaders");

        Object[] btcBlockSerializedArray = (Object[]) args[0];

        // Before going and actually deserializing and calling the underlying function,
        // check that all block headers passed in are actually block headers doing
        // a simple size check. If this check fails, just fail.
        if (Arrays.stream(btcBlockSerializedArray).anyMatch(bytes -> !BtcTransactionFormatUtils.isBlockHeaderSize(((byte[]) bytes).length, activations))) {
            // This exception type bypasses bridge teardown, signalling no work done
            // and preventing the overhead of saving bridge storage
            logger.warn("Unexpected BTC header(s) received (size mismatch). Aborting processing.");
            throw new BridgeIllegalArgumentException("Unexpected BTC header(s) received (size mismatch). Aborting processing.");
        }

        BtcBlock[] btcBlockArray = new BtcBlock[btcBlockSerializedArray.length];
        for (int i = 0; i < btcBlockSerializedArray.length; i++) {
            byte[] btcBlockSerialized = (byte[]) btcBlockSerializedArray[i];
            try {
                BtcBlock header = bridgeConstants.getBtcParams().getDefaultSerializer().makeBlock(btcBlockSerialized);
                btcBlockArray[i] = header;
            } catch (ProtocolException e) {
                throw new BridgeIllegalArgumentException("Block " + i + " could not be parsed " + ByteUtil.toHexString(btcBlockSerialized), e);
            }
        }
        try {
            bridgeSupport.receiveHeaders(btcBlockArray);
        } catch (Exception e) {
            logger.warn("Exception adding header", e);
            throw new VMException("Exception adding header", e);
        }
    }

    public boolean registerBtcTransactionIsPublic() {
        return activations.isActive(ConsensusRule.RSKIP199);
    }

    public int receiveHeader(Object[] args) throws VMException {
        logger.trace("receiveHeader");

        byte[] headerArg = (byte[]) args[0];

        if (!BtcTransactionFormatUtils.isBlockHeaderSize(headerArg.length, activations)) {
            logger.warn("Unexpected BTC header received (size mismatch). Aborting processing.");
            return RECEIVE_HEADER_ERROR_SIZE_MISTMATCH;
        }

        BtcBlock header = bridgeConstants.getBtcParams().getDefaultSerializer().makeBlock(headerArg);

        try {
            return bridgeSupport.receiveHeader(header);
        } catch (Exception e) {
            String errorMessage = "Exception adding header in receiveHeader";
            logger.warn(errorMessage, e);
            throw new VMException(errorMessage, e);
        }
    }

    public void registerBtcTransaction(Object[] args) throws VMException {
        logger.trace("registerBtcTransaction");

        byte[] btcTxSerialized = (byte[]) args[0];
        int height = ((BigInteger)args[1]).intValue();

        byte[] pmtSerialized = (byte[]) args[2];
        try {
            bridgeSupport.registerBtcTransaction(rskTx, btcTxSerialized, height, pmtSerialized);
        } catch (IOException | BlockStoreException e) {
            logger.warn("Exception in registerBtcTransaction", e);
            throw new VMException("Exception in registerBtcTransaction", e);
        }
    }

    public void releaseBtc(Object[] args) throws VMException {
        logger.trace("releaseBtc");

        try {
            bridgeSupport.releaseBtc(rskTx);
        } catch (Program.OutOfGasException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Exception in releaseBtc", e);
            throw new VMException("Exception in releaseBtc", e);
        }
    }

    public void addSignature(Object[] args) throws VMException {
        logger.trace("addSignature");

        byte[] federatorPublicKeySerialized = (byte[]) args[0];
        BtcECKey federatorPublicKey;
        try {
            federatorPublicKey = BtcECKey.fromPublicOnly(federatorPublicKeySerialized);
        } catch (Exception e) {
            throw new BridgeIllegalArgumentException("Public key could not be parsed " + ByteUtil.toHexString(federatorPublicKeySerialized), e);
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
                throw new BridgeIllegalArgumentException("Signature could not be parsed " + ByteUtil.toHexString(signatureByteArray), e);
            }
            signatures.add(signatureByteArray);
        }
        byte[] rskTxHash = (byte[]) args[2];
        if (rskTxHash.length!=32) {
            throw new BridgeIllegalArgumentException("Invalid rsk tx hash " + ByteUtil.toHexString(rskTxHash));
        }
        try {
            bridgeSupport.addSignature(federatorPublicKey, signatures, rskTxHash);
        } catch (BridgeIllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Exception in addSignature", e);
            throw new VMException("Exception in addSignature", e);
        }
    }

    public byte[] getStateForBtcReleaseClient(Object[] args) throws VMException {
        logger.trace("getStateForBtcReleaseClient");

        try {
            return bridgeSupport.getStateForBtcReleaseClient();
        } catch (Exception e) {
            logger.warn("Exception in getStateForBtcReleaseClient", e);
            throw new VMException("Exception in getStateForBtcReleaseClient", e);
        }
    }

    public byte[] getStateForDebugging(Object[] args) throws VMException {
        logger.trace("getStateForDebugging");

        try {
            return bridgeSupport.getStateForDebugging();
        } catch (Exception e) {
            logger.warn("Exception in getStateForDebugging", e);
            throw new VMException("Exception in getStateForDebugging", e);
        }
    }

    public Integer getBtcBlockchainBestChainHeight(Object[] args) throws VMException {
        logger.trace("getBtcBlockchainBestChainHeight");

        try {
            return bridgeSupport.getBtcBlockchainBestChainHeight();
        } catch (Exception e) {
            logger.warn("Exception in getBtcBlockchainBestChainHeight", e);
            throw new VMException("Exception in getBtcBlockchainBestChainHeight", e);
        }
    }

    public boolean getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls(Object[] args) {
        return !activations.isActive(ConsensusRule.RSKIP220);
    }

    public Integer getBtcBlockchainInitialBlockHeight(Object[] args) throws VMException {
        logger.trace("getBtcBlockchainInitialBlockHeight");

        try {
            return bridgeSupport.getBtcBlockchainInitialBlockHeight();
        } catch (Exception e) {
            logger.warn("Exception in getBtcBlockchainInitialBlockHeight", e);
            throw new VMException("Exception in getBtcBlockchainInitialBlockHeight", e);
        }
    }

    /**
     * @deprecated
     * @param args
     * @return
     */
    @Deprecated
    public Object[] getBtcBlockchainBlockLocator(Object[] args) throws VMException {
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
            throw new VMException("Exception in getBtcBlockchainBlockLocator", e);
        }
    }

    public byte[] getBtcBlockchainBlockHashAtDepth(Object[] args) throws VMException {
        logger.trace("getBtcBlockchainBlockHashAtDepth");

        int depth = ((BigInteger) args[0]).intValue();
        Sha256Hash blockHash = null;
        try {
            blockHash = bridgeSupport.getBtcBlockchainBlockHashAtDepth(depth);
        } catch (Exception e) {
            logger.warn("Exception in getBtcBlockchainBlockHashAtDepth", e);
            throw new VMException("Exception in getBtcBlockchainBlockHashAtDepth", e);
        }

        return blockHash.getBytes();
    }

    public long getBtcTransactionConfirmationsGetCost(Object[] args) {
        return bridgeSupport.getBtcTransactionConfirmationsGetCost(args);
    }

    public int getBtcTransactionConfirmations(Object[] args) throws VMException {
        logger.trace("getBtcTransactionConfirmations");
        try {
            Sha256Hash btcTxHash = Sha256Hash.wrap((byte[]) args[0]);
            Sha256Hash btcBlockHash = Sha256Hash.wrap((byte[]) args[1]);

            int merkleBranchPath = ((BigInteger) args[2]).intValue();

            Object[] merkleBranchHashesArray = (Object[]) args[3];
            List<Sha256Hash> merkleBranchHashes = Arrays.stream(merkleBranchHashesArray)
                    .map(hash -> Sha256Hash.wrap((byte[]) hash)).collect(Collectors.toList());

            MerkleBranch merkleBranch = new MerkleBranch(merkleBranchHashes, merkleBranchPath);

            return bridgeSupport.getBtcTransactionConfirmations(btcTxHash, btcBlockHash, merkleBranch);
        } catch (Exception e) {
            logger.warn("Exception in getBtcTransactionConfirmations", e);
            throw new VMException("Exception in getBtcTransactionConfirmations", e);
        }
    }

    public Long getMinimumLockTxValue(Object[] args) {
        logger.trace("getMinimumLockTxValue");
        return bridgeSupport.getMinimumPeginTxValue().getValue();
    }

    public Boolean isBtcTxHashAlreadyProcessed(Object[] args) throws VMException {
        logger.trace("isBtcTxHashAlreadyProcessed");

        try {
            Sha256Hash btcTxHash = Sha256Hash.wrap((String) args[0]);
            return bridgeSupport.isBtcTxHashAlreadyProcessed(btcTxHash);
        } catch (Exception e) {
            logger.warn("Exception in isBtcTxHashAlreadyProcessed", e);
            throw new VMException("Exception in isBtcTxHashAlreadyProcessed", e);
        }
    }

    public Long getBtcTxHashProcessedHeight(Object[] args) throws VMException {
        logger.trace("getBtcTxHashProcessedHeight");

        try {
            Sha256Hash btcTxHash = Sha256Hash.wrap((String) args[0]);
            return bridgeSupport.getBtcTxHashProcessedHeight(btcTxHash);
        } catch (Exception e) {
            logger.warn("Exception in getBtcTxHashProcessedHeight", e);
            throw new VMException("Exception in getBtcTxHashProcessedHeight", e);
        }
    }

    public String getFederationAddress(Object[] args) {
        logger.trace("getFederationAddress");

        return bridgeSupport.getFederationAddress().toBase58();
    }

    public Integer getFederationSize(Object[] args) {
        logger.trace("getFederationSize");

        return bridgeSupport.getFederationSize();
    }

    public Integer getFederationThreshold(Object[] args) {
        logger.trace("getFederationThreshold");

        return bridgeSupport.getFederationThreshold();
    }

    public byte[] getFederatorPublicKey(Object[] args) {
        logger.trace("getFederatorPublicKey");

        int index = ((BigInteger) args[0]).intValue();
        return bridgeSupport.getFederatorPublicKey(index);
    }

    public byte[] getFederatorPublicKeyOfType(Object[] args) throws VMException {
        logger.trace("getFederatorPublicKeyOfType");

        int index = ((BigInteger) args[0]).intValue();

        FederationMember.KeyType keyType;
        try {
            keyType = FederationMember.KeyType.byValue((String) args[1]);
        } catch (Exception e) {
            logger.warn("Exception in getFederatorPublicKeyOfType", e);
            throw new VMException("Exception in getFederatorPublicKeyOfType", e);
        }

        return bridgeSupport.getFederatorPublicKeyOfType(index, keyType);
    }

    public Long getFederationCreationTime(Object[] args) {
        logger.trace("getFederationCreationTime");

        // Return the creation time in milliseconds from the epoch
        return bridgeSupport.getFederationCreationTime().toEpochMilli();
    }

    public long getFederationCreationBlockNumber(Object[] args) {
        logger.trace("getFederationCreationBlockNumber");
        return bridgeSupport.getFederationCreationBlockNumber();
    }

    public String getRetiringFederationAddress(Object[] args) {
        logger.trace("getRetiringFederationAddress");

        Address address = bridgeSupport.getRetiringFederationAddress();

        if (address == null) {
            // When there's no address, empty string is returned
            return "";
        }

        return address.toBase58();
    }

    public Integer getRetiringFederationSize(Object[] args) {
        logger.trace("getRetiringFederationSize");

        return bridgeSupport.getRetiringFederationSize();
    }

    public Integer getRetiringFederationThreshold(Object[] args) {
        logger.trace("getRetiringFederationThreshold");

        return bridgeSupport.getRetiringFederationThreshold();
    }

    public byte[] getRetiringFederatorPublicKey(Object[] args) {
        logger.trace("getRetiringFederatorPublicKey");

        int index = ((BigInteger) args[0]).intValue();
        byte[] publicKey = bridgeSupport.getRetiringFederatorPublicKey(index);

        if (publicKey == null) {
            // Empty array is returned when public key is not found or there's no retiring federation
            return new byte[]{};
        }

        return publicKey;
    }

    public byte[] getRetiringFederatorPublicKeyOfType(Object[] args) throws VMException {
        logger.trace("getRetiringFederatorPublicKeyOfType");

        int index = ((BigInteger) args[0]).intValue();

        FederationMember.KeyType keyType;
        try {
            keyType = FederationMember.KeyType.byValue((String) args[1]);
        } catch (Exception e) {
            logger.warn("Exception in getRetiringFederatorPublicKeyOfType", e);
            throw new VMException("Exception in getRetiringFederatorPublicKeyOfType", e);
        }

        byte[] publicKey = bridgeSupport.getRetiringFederatorPublicKeyOfType(index, keyType);

        if (publicKey == null) {
            // Empty array is returned when public key is not found or there's no retiring federation
            return new byte[]{};
        }

        return publicKey;
    }

    public Long getRetiringFederationCreationTime(Object[] args) {
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

    public Integer createFederation(Object[] args) throws BridgeIllegalArgumentException {
        logger.trace("createFederation");

        return bridgeSupport.voteFederationChange(
                rskTx,
                new ABICallSpec("create", new byte[][]{})
        );
    }

    public Integer addFederatorPublicKey(Object[] args) throws BridgeIllegalArgumentException {
        logger.trace("addFederatorPublicKey");

        byte[] publicKeyBytes;
        try {
            publicKeyBytes = (byte[]) args[0];
        } catch (Exception e) {
            logger.warn("Exception in addFederatorPublicKey", e);
            return -10;
        }

        return bridgeSupport.voteFederationChange(
                rskTx,
                new ABICallSpec("add", new byte[][]{ publicKeyBytes })
        );
    }

    public Integer addFederatorPublicKeyMultikey(Object[] args) throws BridgeIllegalArgumentException {
        logger.trace("addFederatorPublicKeyMultikey");

        byte[] btcPublicKeyBytes = (byte[]) args[0];
        byte[] rskPublicKeyBytes = (byte[]) args[1];
        byte[] mstPublicKeyBytes = (byte[]) args[2];

        return bridgeSupport.voteFederationChange(
                rskTx,
                new ABICallSpec("add-multi", new byte[][]{ btcPublicKeyBytes, rskPublicKeyBytes, mstPublicKeyBytes })
        );
    }

    public Integer commitFederation(Object[] args) throws BridgeIllegalArgumentException {
        logger.trace("commitFederation");

        byte[] hash;
        try {
            hash = (byte[]) args[0];
        } catch (Exception e) {
            logger.warn("Exception in commitFederation", e);
            return -10;
        }

        return bridgeSupport.voteFederationChange(
                rskTx,
                new ABICallSpec("commit", new byte[][]{ hash })
        );
    }

    public Integer rollbackFederation(Object[] args) throws BridgeIllegalArgumentException {
        logger.trace("rollbackFederation");

        return bridgeSupport.voteFederationChange(
                rskTx,
                new ABICallSpec("rollback", new byte[][]{})
        );
    }

    public byte[] getPendingFederationHash(Object[] args) {
        logger.trace("getPendingFederationHash");

        byte[] hash = bridgeSupport.getPendingFederationHash();

        if (hash == null) {
            // Empty array is returned when pending federation is not present
            return new byte[]{};
        }

        return hash;
    }

    public Integer getPendingFederationSize(Object[] args) {
        logger.trace("getPendingFederationSize");

        return bridgeSupport.getPendingFederationSize();
    }

    public byte[] getPendingFederatorPublicKey(Object[] args) {
        logger.trace("getPendingFederatorPublicKey");

        int index = ((BigInteger) args[0]).intValue();
        byte[] publicKey = bridgeSupport.getPendingFederatorPublicKey(index);

        if (publicKey == null) {
            // Empty array is returned when public key is not found
            return new byte[]{};
        }

        return publicKey;
    }

    public byte[] getPendingFederatorPublicKeyOfType(Object[] args) throws VMException {
        logger.trace("getPendingFederatorPublicKeyOfType");

        int index = ((BigInteger) args[0]).intValue();

        FederationMember.KeyType keyType;
        try {
            keyType = FederationMember.KeyType.byValue((String) args[1]);
        } catch (Exception e) {
            logger.warn("Exception in getPendingFederatorPublicKeyOfType", e);
            throw new VMException("Exception in getPendingFederatorPublicKeyOfType", e);
        }

        byte[] publicKey = bridgeSupport.getPendingFederatorPublicKeyOfType(index, keyType);

        if (publicKey == null) {
            // Empty array is returned when public key is not found
            return new byte[]{};
        }

        return publicKey;
    }

    public Integer getLockWhitelistSize(Object[] args) {
        logger.trace("getLockWhitelistSize");

        return bridgeSupport.getLockWhitelistSize();
    }

    public String getLockWhitelistAddress(Object[] args) {
        logger.trace("getLockWhitelistAddress");

        int index = ((BigInteger) args[0]).intValue();
        LockWhitelistEntry entry = bridgeSupport.getLockWhitelistEntryByIndex(index);

        if (entry == null) {
            // Empty string is returned when address is not found
            return "";
        }

        return entry.address().toBase58();
    }

    public long getLockWhitelistEntryByAddress(Object[] args) {
        logger.trace("getLockWhitelistEntryByAddress");

        String addressBase58;
        try {
            addressBase58 = (String) args[0];
        } catch (Exception e) {
            logger.warn("Exception in getLockWhitelistEntryByAddress", e);
            return LOCK_WHITELIST_INVALID_ADDRESS_FORMAT_ERROR_CODE;
        }

        LockWhitelistEntry entry = bridgeSupport.getLockWhitelistEntryByAddress(addressBase58);

        if (entry == null) {
            // Empty string is returned when address is not found
            return LOCK_WHITELIST_ENTRY_NOT_FOUND_CODE;
        }

        return entry.getClass() == OneOffWhiteListEntry.class ?
                ((OneOffWhiteListEntry)entry).maxTransferValue().getValue() :
                LOCK_WHITELIST_UNLIMITED_MODE_CODE;
    }

    public Integer addOneOffLockWhitelistAddress(Object[] args) {
        logger.trace("addOneOffLockWhitelistAddress");

        String addressBase58;
        BigInteger maxTransferValue;
        try {
            addressBase58 = (String) args[0];
            maxTransferValue = (BigInteger) args[1];
        } catch (Exception e) {
            logger.warn("Exception in addOneOffLockWhitelistAddress", e);
            return 0;
        }

        return bridgeSupport.addOneOffLockWhitelistAddress(rskTx, addressBase58, maxTransferValue);
    }

    public Integer addUnlimitedLockWhitelistAddress(Object[] args) {
        logger.trace("addUnlimitedLockWhitelistAddress");

        String addressBase58;
        try {
            addressBase58 = (String) args[0];
        } catch (Exception e) {
            logger.warn("Exception in addUnlimitedLockWhitelistAddress", e);
            return 0;
        }

        return bridgeSupport.addUnlimitedLockWhitelistAddress(rskTx, addressBase58);
    }

    public Integer removeLockWhitelistAddress(Object[] args) {
        logger.trace("removeLockWhitelistAddress");

        String addressBase58;
        try {
            addressBase58 = (String) args[0];
        } catch (Exception e) {
            logger.warn("Exception in removeLockWhitelistAddress", e);
            return 0;
        }

        return bridgeSupport.removeLockWhitelistAddress(rskTx, addressBase58);
    }

    public Integer setLockWhitelistDisableBlockDelay(Object[] args) throws IOException, BlockStoreException {
        logger.trace("setLockWhitelistDisableBlockDelay");
        BigInteger lockWhitelistDisableBlockDelay = (BigInteger) args[0];
        return bridgeSupport.setLockWhitelistDisableBlockDelay(rskTx, lockWhitelistDisableBlockDelay);
    }

    public Integer voteFeePerKbChange(Object[] args) {
        logger.trace("voteFeePerKbChange");

        Coin feePerKb;
        try {
            feePerKb = Coin.valueOf(((BigInteger) args[0]).longValueExact());
        } catch (Exception e) {
            logger.warn("Exception in voteFeePerKbChange", e);
            return -10;
        }

        return bridgeSupport.voteFeePerKbChange(rskTx, feePerKb);
    }

    public long getFeePerKb(Object[] args) {
        logger.trace("getFeePerKb");

        return bridgeSupport.getFeePerKb().getValue();
    }

    public long getLockingCap(Object[] args) {
        logger.trace("getLockingCap");

        Coin lockingCap = bridgeSupport.getLockingCap();

        return lockingCap.getValue();
    }

    public boolean increaseLockingCap(Object[] args) throws BridgeIllegalArgumentException {
        logger.trace("increaseLockingCap");

        Coin newLockingCap = BridgeUtils.getCoinFromBigInteger((BigInteger) args[0]);
        if (newLockingCap.getValue() <= 0) {
            throw new BridgeIllegalArgumentException("Locking cap must be bigger than zero");
        }

        return bridgeSupport.increaseLockingCap(rskTx, newLockingCap);
    }

    public void registerBtcCoinbaseTransaction(Object[] args) throws VMException {
        logger.trace("registerBtcCoinbaseTransaction");

        byte[] btcTxSerialized = (byte[]) args[0];
        Sha256Hash blockHash = Sha256Hash.wrap((byte[]) args[1]);
        byte[] pmtSerialized = (byte[]) args[2];
        Sha256Hash witnessMerkleRoot = Sha256Hash.wrap((byte[]) args[3]);
        byte[] witnessReservedValue = (byte[]) args[4];
        bridgeSupport.registerBtcCoinbaseTransaction(btcTxSerialized, blockHash, pmtSerialized, witnessMerkleRoot, witnessReservedValue);
    }

    public boolean hasBtcBlockCoinbaseTransactionInformation(Object[] args) {
        logger.trace("hasBtcBlockCoinbaseTransactionInformation");
        Sha256Hash blockHash = Sha256Hash.wrap((byte[]) args[0]);

        return bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(blockHash);
    }

    public long getActiveFederationCreationBlockHeight(Object[] args) {
        logger.trace("getActiveFederationCreationBlockHeight");

        return bridgeSupport.getActiveFederationCreationBlockHeight();
    }

    public long registerFastBridgeBtcTransaction(Object[] args) {
        logger.trace("registerFastBridgeBtcTransaction");

        try {
            byte[] btcTxSerialized = (byte[]) args[0];
            int height = ((BigInteger) args[1]).intValue();
            byte[] pmtSerialized = (byte[]) args[2];
            Keccak256 derivationArgumentsHash = new Keccak256((byte[]) args[3]);
            // Parse data to create BTC user refund address with version and hash
            byte[] refundAddressInfo = (byte[]) args[4];
            Address userRefundAddress = new Address(
                bridgeConstants.getBtcParams(),
                BridgeUtils.extractAddressVersionFromBytes(refundAddressInfo),
                BridgeUtils.extractHash160FromBytes(refundAddressInfo)
            );
            // A DataWord cast is used because a SolidityType "address" is decoded using this specific type.
            RskAddress lbcAddress = new RskAddress((DataWord) args[5]);
            // Parse data to create BTC liquidity provider address with version and hash
            byte[] lpAddressInfo = (byte[]) args[6];
            Address lpBtcAddress = new Address(
                bridgeConstants.getBtcParams(),
                BridgeUtils.extractAddressVersionFromBytes(lpAddressInfo),
                BridgeUtils.extractHash160FromBytes(lpAddressInfo)
            );
            boolean shouldTransferToContract = ((boolean) args[7]);

            return bridgeSupport.registerFastBridgeBtcTransaction(
                rskTx,
                btcTxSerialized,
                height,
                pmtSerialized,
                derivationArgumentsHash,
                userRefundAddress,
                lbcAddress,
                lpBtcAddress,
                shouldTransferToContract
            );
        } catch (Exception e) {
            logger.warn("Exception in registerFastBridgeBtcTransaction", e);
            return BridgeSupport.FAST_BRIDGE_GENERIC_ERROR;
        }
    }

    public byte[] getBtcBlockchainBestBlockHeader(Object[] args) {
        logger.trace("getBtcBlockchainBestBlockHeader");

        try {
            return this.bridgeSupport.getBtcBlockchainBestBlockHeader();
        } catch (Exception e) {
            logger.warn("Exception in getBtcBlockchainBestBlockHeader", e);
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }
    }

    public byte[] getBtcBlockchainBlockHeaderByHash(Object[] args) {
        logger.trace("getBtcBlockchainBlockHeaderByHash");

        try {
            byte[] hashBytes = (byte[])args[0];
            Sha256Hash hash = Sha256Hash.wrap(hashBytes);

            return this.bridgeSupport.getBtcBlockchainBlockHeaderByHash(hash);
        } catch (Exception e) {
            logger.warn("Exception in getBtcBlockchainBlockHeaderByHash", e);
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }
    }

    public byte[] getBtcBlockchainBlockHeaderByHeight(Object[] args) {
        logger.trace("getBtcBlockchainBlockHeaderByHeight");

        try {
            int height = ((BigInteger) args[0]).intValue();

            return this.bridgeSupport.getBtcBlockchainBlockHeaderByHeight(height);
        } catch (Exception e) {
            logger.warn("Exception in getBtcBlockchainBlockHeaderByHeight", e);
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }
    }

    public byte[] getBtcBlockchainParentBlockHeaderByHash(Object[] args) {
        logger.trace("getBtcBlockchainParentBlockHeaderByHash");

        try {
            byte[] hashBytes = (byte[])args[0];
            Sha256Hash hash = Sha256Hash.wrap(hashBytes);

            return this.bridgeSupport.getBtcBlockchainParentBlockHeaderByHash(hash);
        } catch (Exception e) {
            logger.warn("Exception in getBtcBlockchainParentBlockHeaderByHash", e);
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }
    }

    public static BridgeMethods.BridgeMethodExecutor activeAndRetiringFederationOnly(BridgeMethods.BridgeMethodExecutor decoratee, String funcName) {
        return (self, args) -> {
            Federation retiringFederation = self.bridgeSupport.getRetiringFederation();

            if (!BridgeUtils.isFromFederateMember(self.rskTx, self.bridgeSupport.getActiveFederation())
                    && ( retiringFederation == null || (retiringFederation != null && !BridgeUtils.isFromFederateMember(self.rskTx, retiringFederation)))) {
                String errorMessage = String.format("Sender is not part of the active or retiring federations, so he is not enabled to call the function '%s'",funcName);
                logger.warn(errorMessage);
                throw new VMException(errorMessage);
            }
            return decoratee.execute(self, args);
        };
    }

    public static BridgeMethods.BridgeMethodExecutor executeIfElse(
            BridgeMethods.BridgeCondition condition,
            BridgeMethods.BridgeMethodExecutor ifTrue,
            BridgeMethods.BridgeMethodExecutor ifFalse) {

        return (self, args) -> {
            if (condition.isTrue(self)) {
                return ifTrue.execute(self, args);
            } else {
                return ifFalse.execute(self, args);
            }
        };
    }

    private boolean isLocalCall() {
        return rskTx.isLocalCallTransaction();
    }
}

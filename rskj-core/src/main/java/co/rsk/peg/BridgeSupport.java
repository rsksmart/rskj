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

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.AddressFormatException;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcBlockChain;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.CheckpointManager;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.InsufficientMoneyException;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.core.UTXOProviderException;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.*;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import co.rsk.rpc.modules.trace.CallType;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.InternalTransaction;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.TransferInvoke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static co.rsk.peg.BridgeUtils.getRegularPegoutTxSize;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP186;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP219;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP271;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP294;

/**
 * Helper class to move funds from btc to rsk and rsk to btc
 * @author Oscar Guindzberg
 */
public class BridgeSupport {
    public static final RskAddress BURN_ADDRESS = new RskAddress("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

    public static final int MAX_RELEASE_ITERATIONS = 30;

    public static final Integer FEDERATION_CHANGE_GENERIC_ERROR_CODE = -10;
    public static final Integer LOCK_WHITELIST_GENERIC_ERROR_CODE = -10;
    public static final Integer LOCK_WHITELIST_INVALID_ADDRESS_FORMAT_ERROR_CODE = -2;
    public static final Integer LOCK_WHITELIST_ALREADY_EXISTS_ERROR_CODE = -1;
    public static final Integer LOCK_WHITELIST_UNKNOWN_ERROR_CODE = 0;
    public static final Integer LOCK_WHITELIST_SUCCESS_CODE = 1;
    public static final Integer FEE_PER_KB_GENERIC_ERROR_CODE = -10;
    public static final Integer NEGATIVE_FEE_PER_KB_ERROR_CODE = -1;
    public static final Integer EXCESSIVE_FEE_PER_KB_ERROR_CODE = -2;

    public static final Integer BTC_TRANSACTION_CONFIRMATION_INEXISTENT_BLOCK_HASH_ERROR_CODE = -1;
    public static final Integer BTC_TRANSACTION_CONFIRMATION_BLOCK_NOT_IN_BEST_CHAIN_ERROR_CODE = -2;
    public static final Integer BTC_TRANSACTION_CONFIRMATION_INCONSISTENT_BLOCK_ERROR_CODE = -3;
    public static final Integer BTC_TRANSACTION_CONFIRMATION_BLOCK_TOO_OLD_ERROR_CODE = -4;
    public static final Integer BTC_TRANSACTION_CONFIRMATION_INVALID_MERKLE_BRANCH_ERROR_CODE = -5;

    public static final long FAST_BRIDGE_REFUNDED_USER_ERROR_CODE = -100;
    public static final long FAST_BRIDGE_REFUNDED_LP_ERROR_CODE = -200;
    public static final long FAST_BRIDGE_UNPROCESSABLE_TX_NOT_CONTRACT_ERROR_CODE = -300;
    public static final long FAST_BRIDGE_UNPROCESSABLE_TX_INVALID_SENDER_ERROR_CODE = -301;
    public static final long FAST_BRIDGE_UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR_CODE = -302;
    public static final long FAST_BRIDGE_UNPROCESSABLE_TX_VALIDATIONS_ERROR = -303;
    public static final long FAST_BRIDGE_UNPROCESSABLE_TX_VALUE_ZERO_ERROR = -304;
    public static final long FAST_BRIDGE_GENERIC_ERROR = -900;

    public static final Integer RECEIVE_HEADER_CALLED_TOO_SOON = -1;
    public static final Integer RECEIVE_HEADER_BLOCK_TOO_OLD = -2;
    public static final Integer RECEIVE_HEADER_CANT_FOUND_PREVIOUS_BLOCK = -3;
    public static final Integer RECEIVE_HEADER_BLOCK_PREVIOUSLY_SAVED = -4;
    public static final Integer RECEIVE_HEADER_UNEXPECTED_EXCEPTION = -99;

    // Enough depth to be able to search backwards one month worth of blocks
    // (6 blocks/hour, 24 hours/day, 30 days/month)
    public static final Integer BTC_TRANSACTION_CONFIRMATION_MAX_DEPTH = 4320;

    private static final Logger logger = LoggerFactory.getLogger("BridgeSupport");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final String INVALID_ADDRESS_FORMAT_MESSAGE = "invalid address format";

    private final List<String> FEDERATION_CHANGE_FUNCTIONS = Collections.unmodifiableList(Arrays.asList(
            "create",
            "add",
            "add-multi",
            "commit",
            "rollback"));

    private final BridgeConstants bridgeConstants;
    private final BridgeStorageProvider provider;
    private final Repository rskRepository;
    private final BridgeEventLogger eventLogger;
    private final List<ProgramSubtrace> subtraces = new ArrayList<>();
    private final BtcLockSenderProvider btcLockSenderProvider;
    private final PeginInstructionsProvider peginInstructionsProvider;

    private final FederationSupport federationSupport;

    private final Context btcContext;
    private final BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BtcBlockStoreWithCache btcBlockStore;
    private BtcBlockChain btcBlockChain;
    private final org.ethereum.core.Block rskExecutionBlock;
    private final ActivationConfig.ForBlock activations;

    protected enum TxType {
        PEGIN,
        PEGOUT,
        MIGRATION,
        UNKNOWN
    }

    public BridgeSupport(
            BridgeConstants bridgeConstants,
            BridgeStorageProvider provider,
            BridgeEventLogger eventLogger,
            BtcLockSenderProvider btcLockSenderProvider,
            PeginInstructionsProvider peginInstructionsProvider,
            Repository repository,
            Block executionBlock,
            Context btcContext,
            FederationSupport federationSupport,
            BtcBlockStoreWithCache.Factory btcBlockStoreFactory,
            ActivationConfig.ForBlock activations) {
        this.rskRepository = repository;
        this.provider = provider;
        this.rskExecutionBlock = executionBlock;
        this.bridgeConstants = bridgeConstants;
        this.eventLogger = eventLogger;
        this.btcLockSenderProvider = btcLockSenderProvider;
        this.peginInstructionsProvider = peginInstructionsProvider;
        this.btcContext = btcContext;
        this.federationSupport = federationSupport;
        this.btcBlockStoreFactory = btcBlockStoreFactory;
        this.activations = activations;
    }

    public List<ProgramSubtrace> getSubtraces() {
        return Collections.unmodifiableList(this.subtraces);
    }

    @VisibleForTesting
    InputStream getCheckPoints() {
        InputStream checkpoints = BridgeSupport.class.getResourceAsStream("/rskbitcoincheckpoints/" + bridgeConstants.getBtcParams().getId() + ".checkpoints");
        if (checkpoints == null) {
            // If we don't have a custom checkpoints file, try to use bitcoinj's default checkpoints for that network
            checkpoints = BridgeSupport.class.getResourceAsStream("/" + bridgeConstants.getBtcParams().getId() + ".checkpoints");
        }
        return checkpoints;
    }

    @VisibleForTesting
    ActivationConfig.ForBlock getActivations() {
        return this.activations;
    }

    public void save() throws IOException {
        provider.save();
    }

    /**
     * Receives an array of serialized Bitcoin block headers and adds them to the internal BlockChain structure.
     * @param headers The bitcoin headers
     */
    public void receiveHeaders(BtcBlock[] headers) throws IOException, BlockStoreException {
        if (headers.length > 0) {
            logger.debug("Received {} headers. First {}, last {}.", headers.length, headers[0].getHash(), headers[headers.length - 1].getHash());
        } else {
            logger.warn("Received 0 headers");
        }

        Context.propagate(btcContext);
        this.ensureBtcBlockChain();
        for (int i = 0; i < headers.length; i++) {
            try {
                btcBlockChain.add(headers[i]);
            } catch (Exception e) {
                // If we tray to add an orphan header bitcoinj throws an exception
                // This catches that case and any other exception that may be thrown
                logger.warn("Exception adding btc header {}", headers[i].getHash(), e);
            }
        }
    }

    /**
     * Receives only one header of serialized Bitcoin block headers and adds them to the internal BlockChain structure.
     * @param header The bitcoin headers
     */
    public Integer receiveHeader(BtcBlock header) throws IOException, BlockStoreException {
        Context.propagate(btcContext);
        this.ensureBtcBlockChain();

        if (btcBlockStore.get(header.getHash()) != null) {
            return RECEIVE_HEADER_BLOCK_PREVIOUSLY_SAVED;
        }

        long diffTimeStamp = bridgeConstants.getMinSecondsBetweenCallsToReceiveHeader();

        long currentTimeStamp = rskExecutionBlock.getTimestamp(); //in seconds
        Optional<Long> optionalLastTimeStamp = provider.getReceiveHeadersLastTimestamp();
        if (optionalLastTimeStamp.isPresent() && (currentTimeStamp - optionalLastTimeStamp.get() < diffTimeStamp)) {
            logger.warn("Receive header last TimeStamp less than {} milliseconds", diffTimeStamp);
            return RECEIVE_HEADER_CALLED_TOO_SOON;
        }

        //Depth
        StoredBlock previousBlock = btcBlockStore.get(header.getPrevBlockHash());
        if (previousBlock == null) {
            return RECEIVE_HEADER_CANT_FOUND_PREVIOUS_BLOCK;
        }

        // height of best chain - height of current header block greater than maximum depth accepted
        if ((getBtcBlockchainBestChainHeight() - (previousBlock.getHeight() + 1)) > bridgeConstants.getMaxDepthBlockchainAccepted()) {
            return RECEIVE_HEADER_BLOCK_TOO_OLD;
        }

        try {
            btcBlockChain.add(header);
        } catch (Exception e) {
            // If we tray to add an orphan header bitcoinj throws an exception
            // This catches that case and any other exception that may be thrown
            logger.warn("Exception adding btc header {}", header.getHash(), e);
            return RECEIVE_HEADER_UNEXPECTED_EXCEPTION;
        }
        provider.setReceiveHeadersLastTimestamp(currentTimeStamp);
        return 0;
    }

    /**
     * Get the wallet for the currently active federation
     * @return A BTC wallet for the currently active federation
     *
     * @throws IOException
     * @param shouldConsiderFastBridgeUTXOs
     */
    public Wallet getActiveFederationWallet(boolean shouldConsiderFastBridgeUTXOs) throws IOException {
        Federation federation = getActiveFederation();
        List<UTXO> utxos = getActiveFederationBtcUTXOs();

        return BridgeUtils.getFederationSpendWallet(
            btcContext,
            federation,
            utxos,
            shouldConsiderFastBridgeUTXOs,
            provider
        );
    }

    /**
     * Get the wallet for the currently retiring federation
     * or null if there's currently no retiring federation
     * @return A BTC wallet for the currently active federation
     *
     * @throws IOException
     * @param shouldConsiderFastBridgeUTXOs
     */
    public Wallet getRetiringFederationWallet(boolean shouldConsiderFastBridgeUTXOs) throws IOException {
        List<UTXO> retiringFederationBtcUTXOs = getRetiringFederationBtcUTXOs();
        return getRetiringFederationWallet(shouldConsiderFastBridgeUTXOs, retiringFederationBtcUTXOs.size());
    }

    public Wallet getRetiringFederationWallet(boolean shouldConsiderFastBridgeUTXOs, int utxosSizeLimit) throws IOException {
        Federation federation = getRetiringFederation();
        if (federation == null) {
            logger.debug("[getRetiringFederationWallet] No retiring federation found");
            return null;
        }

        List<UTXO> utxos = getRetiringFederationBtcUTXOs();
        if (utxos.size() > utxosSizeLimit) {
            logger.debug("[getRetiringFederationWallet] Going to limit the amount of UTXOs to {}", utxosSizeLimit);
            utxos = utxos.subList(0, utxosSizeLimit);
        }

        logger.debug("[getRetiringFederationWallet] Fetching retiring federation spend wallet");
        return BridgeUtils.getFederationSpendWallet(
            btcContext,
            federation,
            utxos,
            shouldConsiderFastBridgeUTXOs,
            provider
        );
    }

    /**
     * Get the wallet for the currently live federations
     * but limited to a specific list of UTXOs
     * @return A BTC wallet for the currently live federation(s)
     * limited to the given list of UTXOs
     *
     */
    public Wallet getUTXOBasedWalletForLiveFederations(List<UTXO> utxos, boolean isFastBridgeCompatible) {
        return BridgeUtils.getFederationsSpendWallet(btcContext, getLiveFederations(), utxos, isFastBridgeCompatible, provider);
    }

    /**
     * Get a no spend wallet for the currently live federations
     * @return A no spend BTC wallet for the currently live federation(s)
     *
     */
    public Wallet getNoSpendWalletForLiveFederations(boolean isFastBridgeCompatible) {
        return BridgeUtils.getFederationsNoSpendWallet(btcContext, getLiveFederations(), isFastBridgeCompatible, provider);
    }

    /**
     * In case of a lock tx: Transfers some SBTCs to the sender of the btc tx and keeps track of the new UTXOs available for spending.
     * In case of a release tx: Keeps track of the change UTXOs, now available for spending.
     * @param rskTx The RSK transaction
     * @param btcTxSerialized The raw BTC tx
     * @param height The height of the BTC block that contains the tx
     * @param pmtSerialized The raw partial Merkle tree
     * @throws BlockStoreException
     * @throws IOException
     */
    public void registerBtcTransaction(Transaction rskTx, byte[] btcTxSerialized, int height, byte[] pmtSerialized)
            throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Context.propagate(btcContext);
        Sha256Hash btcTxHash = BtcTransactionFormatUtils.calculateBtcTxHash(btcTxSerialized);

        try {
            // Check the tx was not already processed
            if (isAlreadyBtcTxHashProcessed(btcTxHash)) {
                throw new RegisterBtcTransactionException("Transaction already processed");
            }

            // Validations for register
            if (!validationsForRegisterBtcTransaction(btcTxHash, height, pmtSerialized, btcTxSerialized)) {
                throw new RegisterBtcTransactionException("Could not validate transaction");
            }

            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams(), btcTxSerialized);
            btcTx.verify();

            // Check again that the tx was not already processed but making sure to use the txid (no witness)
            if (isAlreadyBtcTxHashProcessed(btcTx.getHash(false))) {
                throw new RegisterBtcTransactionException("Transaction already processed");
            }

            // Specific code for pegin/pegout/migration/none txs
            switch (getTransactionType(btcTx)) {
                case PEGIN:
                    processPegIn(btcTx, rskTx, height, btcTxHash);
                    break;
                case PEGOUT:
                    processRelease(btcTx, btcTxHash);
                    break;
                case MIGRATION:
                    processMigration(btcTx, btcTxHash);
                    break;
                default:
                    logger.warn("[registerBtcTransaction] This is not a lock, a release nor a migration tx {}", btcTx);
                    panicProcessor.panic("btclock", "This is not a lock, a release nor a migration tx " + btcTx);
            }
        } catch (RegisterBtcTransactionException e) {
            logger.warn("[registerBtcTransaction] Could not register transaction {}. Message: {}", btcTxHash,
                    e.getMessage());
        }
    }

    protected TxType getTransactionType(BtcTransaction btcTx) {
        Script retiredFederationP2SHScript = provider.getLastRetiredFederationP2SHScript().orElse(null);

        /************************************************************************/
        /** Special case to migrate funds from an old federation               **/
        /************************************************************************/
        if (activations.isActive(ConsensusRule.RSKIP199) && txIsFromOldFederation(btcTx)) {
            return TxType.MIGRATION;
        }

        if (BridgeUtils.isValidPegInTx(
            btcTx,
            getLiveFederations(),
            retiredFederationP2SHScript,
            btcContext,
            bridgeConstants,
            activations
        )) {
            return TxType.PEGIN;
        }

        if (BridgeUtils.isMigrationTx(
            btcTx,
            getActiveFederation(),
            getRetiringFederation(),
            retiredFederationP2SHScript,
            btcContext,
            bridgeConstants,
            activations
        )) {
            return TxType.MIGRATION;
        }

        if (BridgeUtils.isPegOutTx(btcTx, getLiveFederations(), activations)) {
            return TxType.PEGOUT;
        }

        return TxType.UNKNOWN;
    }

    private boolean txIsFromOldFederation(BtcTransaction btcTx) {
        Address oldFederationAddress = Address.fromBase58(bridgeConstants.getBtcParams(), bridgeConstants.getOldFederationAddress());
        Script p2shScript = ScriptBuilder.createP2SHOutputScript(oldFederationAddress.getHash160());

        for (int i = 0; i < btcTx.getInputs().size(); i++) {
            if (BridgeUtils.scriptCorrectlySpendsTx(btcTx, i, p2shScript)) {
                return true;
            }
        }

        return false;
    }

    protected void processPegIn(
        BtcTransaction btcTx,
        Transaction rskTx,
        int height,
        Sha256Hash btcTxHash) throws IOException, RegisterBtcTransactionException {

        logger.debug("[processPegIn] This is a lock tx {}", btcTx);

        Coin totalAmount = computeTotalAmountSent(btcTx);

        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProvider,
            peginInstructionsProvider,
            activations
        );
        try {
            peginInformation.parse(btcTx);
        } catch (PeginInstructionsException e) {
            if (activations.isActive(ConsensusRule.RSKIP170)) {
                if (activations.isActive(ConsensusRule.RSKIP181)) {
                    eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.PEGIN_V1_INVALID_PAYLOAD);
                }

                // If possible to get the sender address, refund
                refundTxSender(btcTx, rskTx, peginInformation, totalAmount);
                markTxAsProcessed(btcTx);
            }

            String message = String.format(
                "Error while trying to parse peg-in information for tx %s. %s",
                btcTx.getHash(),
                e.getMessage()
            );
            logger.warn("[processPegIn] {}", message);
            throw new RegisterBtcTransactionException(message);
        }

        int protocolVersion = peginInformation.getProtocolVersion();
        logger.debug("[processPegIn] Protocol version: {}", protocolVersion);
        switch (protocolVersion) {
            case 0:
                processPegInVersionLegacy(btcTx, rskTx, height, peginInformation, totalAmount);
                break;
            case 1:
                processPegInVersion1(btcTx, rskTx, peginInformation, totalAmount);
                break;
            default:
                markTxAsProcessed(btcTx);

                String message = String.format("Invalid peg-in protocol version: %d", protocolVersion);
                logger.warn("[processPegIn] {}", message);
                throw new RegisterBtcTransactionException(message);
        }

        markTxAsProcessed(btcTx);
        logger.info("[processPegIn] BTC Tx {} processed in RSK", btcTxHash);
    }

    private void processPegInVersionLegacy(
        BtcTransaction btcTx,
        Transaction rskTx,
        int height,
        PeginInformation peginInformation,
        Coin totalAmount) throws IOException, RegisterBtcTransactionException {

        Address senderBtcAddress = peginInformation.getSenderBtcAddress();
        TxSenderAddressType senderBtcAddressType = peginInformation.getSenderBtcAddressType();

        if (!BridgeUtils.txIsProcessableInLegacyVersion(senderBtcAddressType, activations)) {
            logger.warn("[processPeginVersionLegacy] [btcTx:{}] Could not get BtcLockSender from Btc tx", btcTx.getHash());

            if (activations.isActive(ConsensusRule.RSKIP181)) {
                eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);
            }

            throw new RegisterBtcTransactionException("Could not get BtcLockSender from Btc tx");
        }

        // Confirm we should process this lock
        if (shouldProcessPegInVersionLegacy(senderBtcAddressType, btcTx, senderBtcAddress, totalAmount, height)) {
            executePegIn(btcTx, peginInformation, totalAmount);
        } else {
            if (activations.isActive(ConsensusRule.RSKIP181)) {
                if (!isTxLockableForLegacyVersion(senderBtcAddressType, btcTx, senderBtcAddress)) {
                    eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER);
                } else if (!verifyLockDoesNotSurpassLockingCap(btcTx, totalAmount)) {
                    eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.PEGIN_CAP_SURPASSED);
                }
            }

            generateRejectionRelease(btcTx, senderBtcAddress, rskTx, totalAmount);
        }
    }

    private void processPegInVersion1(
        BtcTransaction btcTx,
        Transaction rskTx,
        PeginInformation peginInformation,
        Coin totalAmount) throws RegisterBtcTransactionException, IOException {

        if (!activations.isActive(ConsensusRule.RSKIP170)) {
            throw new RegisterBtcTransactionException("Can't process version 1 peg-ins before RSKIP 170 activation");
        }

        // Confirm we should process this lock
        if (verifyLockDoesNotSurpassLockingCap(btcTx, totalAmount)) {
            executePegIn(btcTx, peginInformation, totalAmount);
        } else {
            logger.debug("[processPegInVersion1] Peg-in attempt surpasses locking cap. Amount attempted to lock: {}", totalAmount);

            if (activations.isActive(ConsensusRule.RSKIP181)) {
                eventLogger.logRejectedPegin(btcTx, RejectedPeginReason.PEGIN_CAP_SURPASSED);
            }

            refundTxSender(btcTx, rskTx, peginInformation, totalAmount);
        }
    }

    private void executePegIn(BtcTransaction btcTx, PeginInformation peginInformation, Coin amount) throws IOException {
        RskAddress rskDestinationAddress = peginInformation.getRskDestinationAddress();
        Address senderBtcAddress = peginInformation.getSenderBtcAddress();
        TxSenderAddressType senderBtcAddressType = peginInformation.getSenderBtcAddressType();
        int protocolVersion = peginInformation.getProtocolVersion();
        co.rsk.core.Coin amountInWeis = co.rsk.core.Coin.fromBitcoin(amount);

        logger.debug("[executePegIn] [btcTx:{}] Is a lock from a {} sender", btcTx.getHash(), senderBtcAddressType);
        this.transferTo(peginInformation.getRskDestinationAddress(), amountInWeis);
        logger.info(
            "[executePegIn] Transferring from BTC Address {}. RSK Address: {}. Amount: {}",
            senderBtcAddress,
            rskDestinationAddress,
            amountInWeis
        );

        if (activations.isActive(ConsensusRule.RSKIP146)) {
            if (activations.isActive(ConsensusRule.RSKIP170)) {
                eventLogger.logPeginBtc(rskDestinationAddress, btcTx, amount, protocolVersion);
            } else {
                eventLogger.logLockBtc(rskDestinationAddress, btcTx, senderBtcAddress, amount);
            }
        }

        // Save UTXOs from the federation(s) only if we actually locked the funds
        saveNewUTXOs(btcTx);
    }

    private void refundTxSender(
        BtcTransaction btcTx,
        Transaction rskTx,
        PeginInformation peginInformation,
        Coin amount) throws IOException {

        Address btcRefundAddress = peginInformation.getBtcRefundAddress();
        if (btcRefundAddress != null) {
            generateRejectionRelease(btcTx, btcRefundAddress, rskTx, amount);
        } else {
            logger.debug("[refundTxSender] No btc refund address provided, couldn't get sender address either. Can't refund");

            if (activations.isActive(ConsensusRule.RSKIP181)) {
                if (peginInformation.getProtocolVersion() == 1) {
                    eventLogger.logUnrefundablePegin(btcTx, UnrefundablePeginReason.PEGIN_V1_REFUND_ADDRESS_NOT_SET);
                } else {
                    eventLogger.logUnrefundablePegin(btcTx, UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);
                }
            }
        }
    }

    private void markTxAsProcessed(BtcTransaction btcTx) throws IOException {
        // Mark tx as processed on this block (and use the txid without the witness)
        provider.setHeightBtcTxhashAlreadyProcessed(btcTx.getHash(false), rskExecutionBlock.getNumber());
    }

    protected void processRelease(BtcTransaction btcTx, Sha256Hash btcTxHash) throws IOException {
        logger.debug("[processRelease] This is a release tx {}", btcTx);
            // do-nothing
            // We could call removeUsedUTXOs(btcTx) here, but we decided to not do that.
            // Used utxos should had been removed when we created the release tx.
            // Invoking removeUsedUTXOs() here would make "some" sense in theses scenarios:
            // a) In testnet, devnet or local: we restart the RSK blockchain without changing the federation address. We don't want to have utxos that were already spent.
            // Open problem: TxA spends TxB. registerBtcTransaction() for TxB is called, it spends a utxo the bridge is not yet aware of,
            // so nothing is removed. Then registerBtcTransaction() for TxA and the "already spent" utxo is added as it was not spent.
            // When is not guaranteed to be called in the chronological order, so a Federator can inform
            // b) In prod: Federator created a tx manually or the federation was compromised and some utxos were spent. Better not try to spend them.
            // Open problem: For performance removeUsedUTXOs() just removes 1 utxo

        markTxAsProcessed(btcTx);

        // Generate new change UTXO
        saveNewUTXOs(btcTx);
        logger.info("[processRelease] BTC Tx {} processed in RSK", btcTxHash);
    }

    protected void processMigration(BtcTransaction btcTx, Sha256Hash btcTxHash) throws IOException {
        logger.debug("[processMigration] This is a migration tx {}", btcTx);

        markTxAsProcessed(btcTx);

        // Input spent on retiring federation and a new UTXO that is created on active federation.
        // It is probably merging multiple UTXOs from the retiring federation
        saveNewUTXOs(btcTx);
        logger.info("[processMigration] BTC Tx {} processed in RSK", btcTxHash);
    }

    private boolean shouldProcessPegInVersionLegacy(TxSenderAddressType txSenderAddressType, BtcTransaction btcTx,
                                       Address senderBtcAddress, Coin totalAmount, int height) {
        return isTxLockableForLegacyVersion(txSenderAddressType, btcTx, senderBtcAddress) &&
                verifyLockSenderIsWhitelisted(senderBtcAddress, totalAmount, height) &&
                verifyLockDoesNotSurpassLockingCap(btcTx, totalAmount);
    }

    protected boolean isTxLockableForLegacyVersion(TxSenderAddressType txSenderAddressType, BtcTransaction btcTx, Address senderBtcAddress) {

        if (txSenderAddressType == TxSenderAddressType.P2PKH ||
                (txSenderAddressType == TxSenderAddressType.P2SHP2WPKH && activations.isActive(ConsensusRule.RSKIP143))) {
            return true;
        } else {
            logger.warn(
                "[isTxLockableForLegacyVersion]: [btcTx:{}] Btc tx type not supported: {}, returning funds to sender: {}",
                btcTx.getHash(),
                txSenderAddressType,
                senderBtcAddress
            );
            return false;
        }
    }

    /**
     * Internal method to transfer RSK to an RSK account
     * It also produce the appropiate internal transaction subtrace if needed
     *
     * @param receiver  address that receives the amount
     * @param amount    amount to transfer
     */
    private void transferTo(RskAddress receiver, co.rsk.core.Coin amount) {
        rskRepository.transfer(
                PrecompiledContracts.BRIDGE_ADDR,
                receiver,
                amount
        );

        DataWord from = DataWord.valueOf(PrecompiledContracts.BRIDGE_ADDR.getBytes());
        DataWord to = DataWord.valueOf(receiver.getBytes());
        long gas = 0L;
        DataWord value = DataWord.valueOf(amount.getBytes());

        TransferInvoke invoke = new TransferInvoke(from, to, gas, value);
        ProgramResult result     = ProgramResult.empty();
        ProgramSubtrace subtrace = ProgramSubtrace.newCallSubtrace(CallType.CALL, invoke, result, null, Collections.emptyList());

        logger.info("Transferred {} weis to {}", amount, receiver);

        this.subtraces.add(subtrace);
    }

    /*
      Add the btcTx outputs that send btc to the federation(s) to the UTXO list
     */
    private void saveNewUTXOs(BtcTransaction btcTx) throws IOException {
        // Outputs to the active federation
        List<TransactionOutput> outputsToTheActiveFederation = btcTx.getWalletOutputs(getActiveFederationWallet(
            false));
        for (TransactionOutput output : outputsToTheActiveFederation) {
            UTXO utxo = new UTXO(btcTx.getHash(), output.getIndex(), output.getValue(), 0, btcTx.isCoinBase(), output.getScriptPubKey());
            getActiveFederationBtcUTXOs().add(utxo);
        }

        // Outputs to the retiring federation (if any)
        Wallet retiringFederationWallet = getRetiringFederationWallet(false);
        if (retiringFederationWallet != null) {
            List<TransactionOutput> outputsToTheRetiringFederation = btcTx.getWalletOutputs(retiringFederationWallet);
            for (TransactionOutput output : outputsToTheRetiringFederation) {
                UTXO utxo = new UTXO(btcTx.getHash(), output.getIndex(), output.getValue(), 0, btcTx.isCoinBase(), output.getScriptPubKey());
                getRetiringFederationBtcUTXOs().add(utxo);
            }
        }
    }

    /**
     * Initiates the process of sending coins back to BTC.
     * This is the default contract method.
     * The funds will be sent to the bitcoin address controlled by the private key that signed the rsk tx.
     * The amount sent to the bridge in this tx will be the amount sent in the btc network minus fees.
     * @param rskTx The rsk tx being executed.
     * @throws IOException
     */
    public void releaseBtc(Transaction rskTx) throws IOException {
        Coin value = rskTx.getValue().toBitcoin();
        final RskAddress senderAddress = rskTx.getSender();
        //as we can't send btc from contracts we want to send them back to the senderAddressStr
        if (BridgeUtils.isContractTx(rskTx)) {
            logger.trace("Contract {} tried to release funds. Release is just allowed from standard accounts.", rskTx);
            if (activations.isActive(ConsensusRule.RSKIP185)) {
                emitRejectEvent(value, senderAddress.toHexString(), RejectedPegoutReason.CALLER_CONTRACT);
                return;
            } else {
                throw new Program.OutOfGasException("Contract calling releaseBTC");
            }
        }

        Context.propagate(btcContext);
        NetworkParameters btcParams = bridgeConstants.getBtcParams();
        Address btcDestinationAddress = BridgeUtils.recoverBtcAddressFromEthTransaction(rskTx, btcParams);

        requestRelease(btcDestinationAddress, value, rskTx);
    }

    private void refundAndEmitRejectEvent(Coin value, RskAddress senderAddress, RejectedPegoutReason reason) {
        String senderAddressStr = senderAddress.toHexString();
        logger.trace("Executing a refund of {} to {}. Reason: {}", value, senderAddressStr, reason);
        rskRepository.transfer(
                PrecompiledContracts.BRIDGE_ADDR,
                senderAddress,
                co.rsk.core.Coin.fromBitcoin(value)
        );
        emitRejectEvent(value, senderAddressStr, reason);
    }

    private void emitRejectEvent(Coin value, String senderAddressStr, RejectedPegoutReason reason) {
        eventLogger.logReleaseBtcRequestRejected(senderAddressStr, value, reason);
    }

    /**
     * Creates a request for BTC release and
     * adds it to the request queue for it
     * to be processed later.
     *
     * @param destinationAddress the destination BTC address.
     * @param value the amount of BTC to release.
     * @throws IOException
     */
    private void requestRelease(Address destinationAddress, Coin value, Transaction rskTx) throws IOException {
        Optional<RejectedPegoutReason> optionalRejectedPegoutReason = Optional.empty();
        if (activations.isActive(RSKIP219)) {
            int pegoutSize = getRegularPegoutTxSize(activations, getActiveFederation());
            Coin feePerKB = getFeePerKb();
            // The pegout transaction has a cost related to its size and the current feePerKB
            // The actual cost cannot be asserted exactly so the calculation is approximated
            // On top of this, the remainder after the fee should be enough for the user to be able to operate
            // For this, the calculation includes an additional percentage to assert for this
            Coin requireFundsForFee = feePerKB
                .multiply(pegoutSize) // times the size in bytes
                .divide(1000); // Get the s/b
            requireFundsForFee = requireFundsForFee
                .add(requireFundsForFee
                    .times(bridgeConstants.getMinimumPegoutValuePercentageToReceiveAfterFee())
                    .divide(100)
                ); // add the gap

            // The pegout value should be greater or equals than the max of these two values
            Coin minValue = Coin.valueOf(Math.max(bridgeConstants.getMinimumPegoutTxValueInSatoshis().value, requireFundsForFee.value));

            // Since Iris the peg-out the rule is that the minimum is inclusive
            if (value.isLessThan(minValue)) {
                optionalRejectedPegoutReason = Optional.of(
                    Objects.equals(minValue, requireFundsForFee) ?
                    RejectedPegoutReason.FEE_ABOVE_VALUE:
                    RejectedPegoutReason.LOW_AMOUNT
                );
            }
        } else {
            // For legacy peg-outs the rule stated that the minimum was exclusive
            if (!value.isGreaterThan(bridgeConstants.getLegacyMinimumPegoutTxValueInSatoshis())) {
                optionalRejectedPegoutReason = Optional.of(RejectedPegoutReason.LOW_AMOUNT);
            }
        }

        if (optionalRejectedPegoutReason.isPresent()) {
            logger.warn(
                "releaseBtc ignored. To {}. Tx {}. Value {}. Reason: {}",
                destinationAddress,
                rskTx,
                value,
                optionalRejectedPegoutReason.get()
            );
            if (activations.isActive(ConsensusRule.RSKIP185)) {
                refundAndEmitRejectEvent(
                    value,
                    rskTx.getSender(),
                    optionalRejectedPegoutReason.get()
                );
            }
        } else {
            if (activations.isActive(ConsensusRule.RSKIP146)) {
                provider.getReleaseRequestQueue().add(destinationAddress, value, rskTx.getHash());
            } else {
                provider.getReleaseRequestQueue().add(destinationAddress, value);
            }

            if (activations.isActive(ConsensusRule.RSKIP185)) {
                eventLogger.logReleaseBtcRequestReceived(rskTx.getSender().toHexString(), destinationAddress.getHash160(), value);
            }
            logger.info("releaseBtc succesful to {}. Tx {}. Value {}.", destinationAddress, rskTx, value);
        }
    }

    /**
     * @return Current fee per kb in BTC.
     */
    public Coin getFeePerKb() {
        Coin currentFeePerKb = provider.getFeePerKb();

        if (currentFeePerKb == null) {
            currentFeePerKb = bridgeConstants.getGenesisFeePerKb();
        }

        return currentFeePerKb;
    }

    /**
     * Executed every now and then.
     * Performs a few tasks: processing of any pending btc funds
     * migrations from retiring federations;
     * processing of any outstanding btc release requests; and
     * processing of any outstanding release btc transactions.
     * @throws IOException
     * @param rskTx current RSK transaction
     */
    public void updateCollections(Transaction rskTx) throws IOException {
        Context.propagate(btcContext);

        eventLogger.logUpdateCollections(rskTx);

        processFundsMigration(rskTx);

        processReleaseRequests();

        processReleaseTransactions(rskTx);

        updateFederationCreationBlockHeights();
    }

    private void processFundsMigration(Transaction rskTx) throws IOException {
        Wallet retiringFederationWallet = activations.isActive(RSKIP294) ?
            getRetiringFederationWallet(true, bridgeConstants.getMaxInputsPerPegoutTransaction()) :
            getRetiringFederationWallet(true);

        List<UTXO> availableUTXOs = getRetiringFederationBtcUTXOs();
        Federation activeFederation = getActiveFederation();

        if (federationIsInMigrationAge(activeFederation) && hasMinimumFundsToMigrate(retiringFederationWallet)) {
            logger.info(
                "Active federation (age={}) is in migration age and retiring federation has funds to migrate: {}.",
                rskExecutionBlock.getNumber() - activeFederation.getCreationBlockNumber(),
                retiringFederationWallet.getBalance().toFriendlyString()
            );

            migrateFunds(
                rskTx.getHash(),
                retiringFederationWallet,
                activeFederation.getAddress(),
                availableUTXOs
            );
        }

        if (retiringFederationWallet != null && federationIsPastMigrationAge(activeFederation)) {
            if (retiringFederationWallet.getBalance().isGreaterThan(Coin.ZERO)) {
                logger.info(
                    "Federation is past migration age and will try to migrate remaining balance: {}.",
                    retiringFederationWallet.getBalance().toFriendlyString()
                );

                try {
                    migrateFunds(
                        rskTx.getHash(),
                        retiringFederationWallet,
                        activeFederation.getAddress(),
                        availableUTXOs
                    );
                } catch (Exception e) {
                    logger.error(
                        "Unable to complete retiring federation migration. Balance left: {} in {}",
                        retiringFederationWallet.getBalance().toFriendlyString(),
                        getRetiringFederationAddress()
                    );
                    panicProcessor.panic("updateCollection", "Unable to complete retiring federation migration.");
                }
            }

            logger.info("Retiring federation migration finished. Available UTXOs left: {}.", availableUTXOs.size());
            provider.setOldFederation(null);
        }
    }

    private boolean federationIsInMigrationAge(Federation federation) {
        long federationAge = rskExecutionBlock.getNumber() - federation.getCreationBlockNumber();
        long ageBegin = bridgeConstants.getFederationActivationAge() + bridgeConstants.getFundsMigrationAgeSinceActivationBegin();
        long ageEnd = bridgeConstants.getFederationActivationAge() + bridgeConstants.getFundsMigrationAgeSinceActivationEnd();

        return federationAge > ageBegin && federationAge < ageEnd;
    }

    private boolean federationIsPastMigrationAge(Federation federation) {
        long federationAge = rskExecutionBlock.getNumber() - federation.getCreationBlockNumber();
        long ageEnd = bridgeConstants.getFederationActivationAge() + bridgeConstants.getFundsMigrationAgeSinceActivationEnd();

        return federationAge >= ageEnd;
    }

    private boolean hasMinimumFundsToMigrate(@Nullable Wallet retiringFederationWallet) {
        // This value is set according to the average 500 bytes transaction size
        Coin minimumFundsToMigrate = getFeePerKb().divide(2);
        return retiringFederationWallet != null
                && retiringFederationWallet.getBalance().isGreaterThan(minimumFundsToMigrate);
    }

    private void migrateFunds(
        Keccak256 rskTxHash,
        Wallet retiringFederationWallet,
        Address activeFederationAddress,
        List<UTXO> availableUTXOs) throws IOException {

        ReleaseTransactionSet releaseTransactionSet = provider.getReleaseTransactionSet();
        Pair<BtcTransaction, List<UTXO>> createResult = createMigrationTransaction(retiringFederationWallet, activeFederationAddress);
        BtcTransaction btcTx = createResult.getLeft();
        List<UTXO> selectedUTXOs = createResult.getRight();

        // Add the TX to the release set
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            Coin amountMigrated = selectedUTXOs.stream()
                .map(UTXO::getValue)
                .reduce(Coin.ZERO, Coin::add);
            releaseTransactionSet.add(btcTx, rskExecutionBlock.getNumber(), rskTxHash);
            // Log the Release request
            eventLogger.logReleaseBtcRequested(rskTxHash.getBytes(), btcTx, amountMigrated);
        } else {
            releaseTransactionSet.add(btcTx, rskExecutionBlock.getNumber());
        }

        // Mark UTXOs as spent
        availableUTXOs.removeIf(utxo -> selectedUTXOs.stream().anyMatch(selectedUtxo ->
            utxo.getHash().equals(selectedUtxo.getHash()) && utxo.getIndex() == selectedUtxo.getIndex()
        ));
    }

    /**
     * Processes the current btc release request queue
     * and tries to build btc transactions using (and marking as spent)
     * the current active federation's utxos.
     * Newly created btc transactions are added to the btc release tx set,
     * and failed attempts are kept in the release queue for future
     * processing.
     *
     */
    private void processReleaseRequests() {
        final Wallet activeFederationWallet;
        final ReleaseRequestQueue releaseRequestQueue;

        try {
            activeFederationWallet = getActiveFederationWallet(true);
            releaseRequestQueue = provider.getReleaseRequestQueue();
        } catch (IOException e) {
            logger.error("Unexpected error accessing storage while attempting to process release requests", e);
            return;
        }

        // Releases are attempted using the currently active federation
        // wallet.
        final ReleaseTransactionBuilder txBuilder = new ReleaseTransactionBuilder(
                btcContext.getParams(),
                activeFederationWallet,
                getFederationAddress(),
                getFeePerKb(),
                activations
        );

        // We have a BTC transaction, mark the UTXOs as spent and add the tx to the release set.
        List<UTXO> availableUTXOs;
        ReleaseTransactionSet releaseTransactionSet;
        // Attempt access to storage first
        // (any of these could fail and would invalidate both the tx build and utxo selection, so treat as atomic)
        try {
            availableUTXOs = getActiveFederationBtcUTXOs();
            releaseTransactionSet = provider.getReleaseTransactionSet();
        } catch (IOException exception) {
            // Unexpected error accessing storage, log and fail
            logger.error("Unexpected error accessing storage while attempting to processReleaseRequests ", exception);
            return;
        }

        // Instead of creating a pegout transaction for every element in the release request queue,
        // check if the elapsed time or blocks have gone by before creating the pegout transaction including all elements in the release request queue.
        // Under a rush hour of peg-outs, the Bridge may need to create more than one peg-out transaction
        // simultaneously per peg-out event to reduce the transaction size, and input count.
        // Pending: Define the limit of the transaction size

        if (activations.isActive(RSKIP271)) {
            processReleasesInBatch(releaseRequestQueue, txBuilder, availableUTXOs, releaseTransactionSet);
        } else {
            processReleasesIndividually(releaseRequestQueue, txBuilder, availableUTXOs, releaseTransactionSet);
        }
    }

    private void addPegoutTxToReleaseTransactionSet(BtcTransaction generatedTransaction,
                                                 ReleaseTransactionSet releaseTransactionSet,
                                                 ReleaseRequestQueue.Entry releaseRequest) {
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            Keccak256 rskTxHash = releaseRequest.getRskTxHash();
            // Add the TX
            releaseTransactionSet.add(generatedTransaction, rskExecutionBlock.getNumber(), rskTxHash);
            // For a short time period, there could be items in the release request queue that don't have the rskTxHash
            // (these are releases created right before the consensus rule activation, that weren't processed before its activation)
            // We shouldn't generate the event for those releases
            if (rskTxHash != null) {
                // Log the Release request
                eventLogger.logReleaseBtcRequested(rskTxHash.getBytes(), generatedTransaction, releaseRequest.getAmount());
            }
        } else {
            addPegoutTxToReleaseTransactionSet(generatedTransaction, releaseTransactionSet);
        }
    }

    private void addPegoutTxToReleaseTransactionSet(BtcTransaction generatedTransaction,
                                                    ReleaseTransactionSet releaseTransactionSet) {
        releaseTransactionSet.add(generatedTransaction, rskExecutionBlock.getNumber());
    }

    private void processReleasesIndividually(ReleaseRequestQueue releaseRequestQueue,
                                             ReleaseTransactionBuilder txBuilder,
                                             List<UTXO> availableUTXOs,
                                             ReleaseTransactionSet releaseTransactionSet){
        releaseRequestQueue.process(MAX_RELEASE_ITERATIONS, (ReleaseRequestQueue.Entry releaseRequest) -> {
            Optional<ReleaseTransactionBuilder.BuildResult> result = txBuilder.buildAmountTo(
                    releaseRequest.getDestination(),
                    releaseRequest.getAmount()
            );

            // Couldn't build a transaction to release these funds
            // Log the event and return false so that the request remains in the
            // queue for future processing.
            // Further logging is done at the tx builder level.
            if (!result.isPresent()) {
                logger.warn(
                        "Couldn't build a release BTC tx for <{}, {}>",
                        releaseRequest.getDestination().toBase58(),
                        releaseRequest.getAmount());
                return false;
            }

            BtcTransaction generatedTransaction = result.get().getBtcTx();
            addPegoutTxToReleaseTransactionSet(generatedTransaction, releaseTransactionSet, releaseRequest);

            // Mark UTXOs as spent
            List<UTXO> selectedUTXOs = result.get().getSelectedUTXOs();
            availableUTXOs.removeAll(selectedUTXOs);

            // TODO: (Ariel Mendelzon, 07/12/2017)
            // TODO: Balance adjustment assumes that change output is output with index 1.
            // TODO: This will change if we implement multiple releases per BTC tx, so
            // TODO: it would eventually need to be fixed.
            // Adjust balances in edge cases
            adjustBalancesIfChangeOutputWasDust(generatedTransaction, releaseRequest.getAmount());

            return true;
        });
    }

    private void processReleasesInBatch(ReleaseRequestQueue releaseRequestQueue,
                                        ReleaseTransactionBuilder txBuilder,
                                        List<UTXO> availableUTXOs,
                                        ReleaseTransactionSet releaseTransactionSet) {
        long currentBlockNumber = rskExecutionBlock.getNumber();
        long nextPegoutCreationBlockNumber = getNextPegoutCreationBlockNumber();

        if (currentBlockNumber >= nextPegoutCreationBlockNumber) {
            // batch pegout transactions
            Optional<ReleaseTransactionBuilder.BuildResult> result = txBuilder.buildBatchedPegouts(releaseRequestQueue.getEntries());

            if (!result.isPresent()) {
                logger.warn(
                        "Couldn't build a release BTC tx for <{}, with sum {}>",
                        releaseRequestQueue.getEntries().hashCode(),
                        releaseRequestQueue.getEntries().stream().mapToDouble(e -> e.getAmount().value).sum());
                return;
            }

            BtcTransaction generatedTransaction = result.get().getBtcTx();
            addPegoutTxToReleaseTransactionSet(generatedTransaction, releaseTransactionSet);

            // Mark UTXOs as spent
            List<UTXO> selectedUTXOs = result.get().getSelectedUTXOs();
            availableUTXOs.removeAll(selectedUTXOs);

            // update next Pegout height
            long nextPegoutHeight = currentBlockNumber + bridgeConstants.getNumberOfBlocksBetweenPegouts();
            provider.setNextPegoutHeight(nextPegoutHeight);

            adjustBalancesIfChangeOutputWasDust(generatedTransaction,
                Coin.valueOf(releaseRequestQueue.getEntries().stream().mapToLong(e -> e.getAmount().value).sum()));
        }
    }

    /**
     * Processes the current btc release transaction set.
     * It basically looks for transactions with enough confirmations
     * and marks them as ready for signing as well as removes them
     * from the set.
     * @param rskTx the RSK transaction that is causing this processing.
     */
    private void processReleaseTransactions(Transaction rskTx) {
        final Map<Keccak256, BtcTransaction> txsWaitingForSignatures;
        final ReleaseTransactionSet releaseTransactionSet;

        try {
            txsWaitingForSignatures = provider.getRskTxsWaitingForSignatures();
            releaseTransactionSet = provider.getReleaseTransactionSet();
        } catch (IOException e) {
            logger.error("Unexpected error accessing storage while attempting to process release btc transactions", e);
            return;
        }

        // TODO: (Ariel Mendelzon - 07/12/2017)
        // TODO: at the moment, there can only be one btc transaction
        // TODO: per rsk transaction in the txsWaitingForSignatures
        // TODO: map, and the rest of the processing logic is
        // TODO: dependant upon this. That is the reason we
        // TODO: add only one btc transaction at a time
        // TODO: (at least at this stage).

        // IMPORTANT: sliceWithEnoughConfirmations also modifies the transaction set in place
        Set<ReleaseTransactionSet.Entry> txsWithEnoughConfirmations = releaseTransactionSet.sliceWithConfirmations(
                rskExecutionBlock.getNumber(),
                bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations(),
                Optional.of(1)
        );
        if (txsWithEnoughConfirmations.size() > 0) {
            ReleaseTransactionSet.Entry entry = txsWithEnoughConfirmations.iterator().next();
            // Since RSKIP176 we are moving back to using the updateCollections related txHash as the set key
            if (activations.isActive(ConsensusRule.RSKIP146) && !activations.isActive(ConsensusRule.RSKIP176)) {
                // The release transaction may have been created prior to the Consensus Rule activation
                // therefore it won't have a rskTxHash value, fallback to this transaction's hash
                txsWaitingForSignatures.put(entry.getRskTxHash() == null ? rskTx.getHash() : entry.getRskTxHash(), entry.getTransaction());
            }
            else {
                txsWaitingForSignatures.put(rskTx.getHash(), entry.getTransaction());
            }
        }
    }

    private void updateFederationCreationBlockHeights() {
        if (!activations.isActive(RSKIP186)) {
            return;
        }

        Optional<Long> nextFederationCreationBlockHeightOpt = provider.getNextFederationCreationBlockHeight();
        if (nextFederationCreationBlockHeightOpt.isPresent()) {
            long nextFederationCreationBlockHeight = nextFederationCreationBlockHeightOpt.get();
            long curBlockHeight = rskExecutionBlock.getNumber();

            if (curBlockHeight >= nextFederationCreationBlockHeight + bridgeConstants.getFederationActivationAge()) {
                provider.setActiveFederationCreationBlockHeight(nextFederationCreationBlockHeight);
                provider.clearNextFederationCreationBlockHeight();
            }
        }
    }

    /**
     * If federation change output value had to be increased to be non-dust, the federation now has
     * more BTC than it should. So, we burn some sBTC to make balances match.
     *
     * @param btcTx      The btc tx that was just completed
     * @param sentByUser The number of sBTC originaly sent by the user
     */
    private void adjustBalancesIfChangeOutputWasDust(BtcTransaction btcTx, Coin sentByUser) {
        if (btcTx.getOutputs().size() <= 1) {
            // If there is no change, do-nothing
            return;
        }
        Coin sumInputs = Coin.ZERO;
        for (TransactionInput transactionInput : btcTx.getInputs()) {
            sumInputs = sumInputs.add(transactionInput.getValue());
        }

        Coin change = Coin.ZERO;
        try {
            change = btcTx.getValueSentToMe(getActiveFederationWallet(false));
        } catch (IOException e) {
            logger.error("Unexpected error accessing storage while attempting to process release requests", e);
        }
        Coin spentByFederation = sumInputs.subtract(change);
        if (spentByFederation.isLessThan(sentByUser)) {
            Coin coinsToBurn = sentByUser.subtract(spentByFederation);
            this.transferTo(BURN_ADDRESS, co.rsk.core.Coin.fromBitcoin(coinsToBurn));
        }
    }

    /**
     * Adds a federator signature to a btc release tx.
     * The hash for the signature must be calculated with Transaction.SigHash.ALL and anyoneCanPay=false. The signature must be canonical.
     * If enough signatures were added, ask federators to broadcast the btc release tx.
     *
     * @param federatorPublicKey   Federator who is signing
     * @param signatures           1 signature per btc tx input
     * @param rskTxHash            The id of the rsk tx
     */
    public void addSignature(BtcECKey federatorPublicKey, List<byte[]> signatures, byte[] rskTxHash) throws Exception {
        Context.propagate(btcContext);

        Federation retiringFederation = getRetiringFederation();
        Federation activeFederation = getActiveFederation();
        Federation federation =
                activeFederation.hasBtcPublicKey(federatorPublicKey) ?
                        activeFederation :
                        (retiringFederation != null && retiringFederation.hasBtcPublicKey(federatorPublicKey) ?
                                retiringFederation:
                                null);

        if (federation == null) {
            logger.warn("Supplied federator public key {} does not belong to any of the federators.", federatorPublicKey);
            return;
        }

        BtcTransaction btcTx = provider.getRskTxsWaitingForSignatures().get(new Keccak256(rskTxHash));
        if (btcTx == null) {
            logger.warn("No tx waiting for signature for hash {}. Probably fully signed already.", new Keccak256(rskTxHash));
            return;
        }
        if (btcTx.getInputs().size() != signatures.size()) {
            logger.warn("Expected {} signatures but received {}.", btcTx.getInputs().size(), signatures.size());
            return;
        }
        eventLogger.logAddSignature(federatorPublicKey, btcTx, rskTxHash);
        processSigning(federatorPublicKey, signatures, rskTxHash, btcTx, federation);
    }

    private void processSigning(BtcECKey federatorPublicKey, List<byte[]> signatures, byte[] rskTxHash, BtcTransaction btcTx, Federation federation) throws IOException {
        // Build input hashes for signatures
        int numInputs = btcTx.getInputs().size();

        List<Sha256Hash> sighashes = new ArrayList<>();
        List<TransactionSignature> txSigs = new ArrayList<>();
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = btcTx.getInput(i);
            Script inputScript = txIn.getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            sighashes.add(btcTx.hashForSignature(i, redeemScript, BtcTransaction.SigHash.ALL, false));
        }

        // Verify given signatures are correct before proceeding
        for (int i = 0; i < numInputs; i++) {
            BtcECKey.ECDSASignature sig;
            try {
                sig = BtcECKey.ECDSASignature.decodeFromDER(signatures.get(i));
            } catch (RuntimeException e) {
                logger.warn("Malformed signature for input {} of tx {}: {}", i, new Keccak256(rskTxHash), ByteUtil.toHexString(signatures.get(i)));
                return;
            }

            Sha256Hash sighash = sighashes.get(i);

            if (!federatorPublicKey.verify(sighash, sig)) {
                logger.warn(
                    "Signature {} {} is not valid for hash {} and public key {}",
                    i,
                    ByteUtil.toHexString(sig.encodeToDER()),
                    sighash,
                    federatorPublicKey
                );
                return;
            }

            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
            txSigs.add(txSig);
            if (!txSig.isCanonical()) {
                logger.warn("Signature {} {} is not canonical.", i, ByteUtil.toHexString(signatures.get(i)));
                return;
            }
        }

        // All signatures are correct. Proceed to signing
        for (int i = 0; i < numInputs; i++) {
            Sha256Hash sighash = sighashes.get(i);
            TransactionInput input = btcTx.getInput(i);
            Script inputScript = input.getScriptSig();

            boolean alreadySignedByThisFederator = BridgeUtils.isInputSignedByThisFederator(
                    federatorPublicKey,
                    sighash,
                    input);

            // Sign the input if it wasn't already
            if (!alreadySignedByThisFederator) {
                try {
                    int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
                    inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigs.get(i).encodeToBitcoin(), sigIndex, 1, 1);
                    input.setScriptSig(inputScript);
                    logger.debug("Tx input {} for tx {} signed.", i, new Keccak256(rskTxHash));
                } catch (IllegalStateException e) {
                    Federation retiringFederation = getRetiringFederation();
                    if (getActiveFederation().hasBtcPublicKey(federatorPublicKey)) {
                        logger.debug("A member of the active federation is trying to sign a tx of the retiring one");
                        return;
                    } else if (retiringFederation != null && retiringFederation.hasBtcPublicKey(federatorPublicKey)) {
                        logger.debug("A member of the retiring federation is trying to sign a tx of the active one");
                        return;
                    }
                    throw e;
                }
            } else {
                logger.warn("Input {} of tx {} already signed by this federator.", i, new Keccak256(rskTxHash));
                break;
            }
        }

        if (BridgeUtils.hasEnoughSignatures(btcContext, btcTx)) {
            logger.info("Tx fully signed {}. Hex: {}", btcTx, Hex.toHexString(btcTx.bitcoinSerialize()));
            provider.getRskTxsWaitingForSignatures().remove(new Keccak256(rskTxHash));

            eventLogger.logReleaseBtc(btcTx, rskTxHash);
        } else if (logger.isDebugEnabled()) {
            int missingSignatures = BridgeUtils.countMissingSignatures(btcContext, btcTx);
            int neededSignatures = federation.getNumberOfSignaturesRequired();
            int signaturesCount = neededSignatures - missingSignatures;

            logger.debug("Tx {} not yet fully signed. Requires {}/{} signatures but has {}",
                    new Keccak256(rskTxHash), neededSignatures, getFederationSize(), signaturesCount);
        }
    }

    /**
     * Returns the btc tx that federators need to sign or broadcast
     * @return a StateForFederator serialized in RLP
     */
    public byte[] getStateForBtcReleaseClient() throws IOException {
        StateForFederator stateForFederator = new StateForFederator(provider.getRskTxsWaitingForSignatures());
        return stateForFederator.getEncoded();
    }

    /**
     * Returns the insternal state of the bridge
     * @return a BridgeState serialized in RLP
     */
    public byte[] getStateForDebugging() throws IOException, BlockStoreException {
        BridgeState stateForDebugging = new BridgeState(getBtcBlockchainBestChainHeight(), provider, activations);

        return stateForDebugging.getEncoded();
    }

    /**
     * Returns the bitcoin blockchain best chain height know by the bridge contract
     */
    public int getBtcBlockchainBestChainHeight() throws IOException, BlockStoreException {
        return getBtcBlockchainChainHead().getHeight();
    }

    /**
     * Returns the bitcoin blockchain initial stored block height
     */
    public int getBtcBlockchainInitialBlockHeight() throws IOException {
        return getLowestBlock().getHeight();
    }

    /**
     * @deprecated
     * Returns an array of block hashes known by the bridge contract.
     * Federators can use this to find what is the latest block in the mainchain the bridge has.
     * @return a List of bitcoin block hashes
     */
    @Deprecated
    public List<Sha256Hash> getBtcBlockchainBlockLocator() throws IOException, BlockStoreException {
        StoredBlock  initialBtcStoredBlock = this.getLowestBlock();
        final int maxHashesToInform = 100;
        List<Sha256Hash> blockLocator = new ArrayList<>();
        StoredBlock cursor = getBtcBlockchainChainHead();
        int bestBlockHeight = cursor.getHeight();
        blockLocator.add(cursor.getHeader().getHash());
        if (bestBlockHeight > initialBtcStoredBlock.getHeight()) {
            boolean stop = false;
            int i = 0;
            try {
                while (blockLocator.size() <= maxHashesToInform && !stop) {
                    int blockHeight = (int) (bestBlockHeight - Math.pow(2, i));
                    if (blockHeight <= initialBtcStoredBlock.getHeight()) {
                        blockLocator.add(initialBtcStoredBlock.getHeader().getHash());
                        stop = true;
                    } else {
                        cursor = this.getPrevBlockAtHeight(cursor, blockHeight);
                        blockLocator.add(cursor.getHeader().getHash());
                    }
                    i++;
                }
            } catch (Exception e) {
                logger.error("Failed to walk the block chain whilst constructing a locator");
                panicProcessor.panic("btcblockchain", "Failed to walk the block chain whilst constructing a locator");
                throw new RuntimeException(e);
            }
            if (!stop) {
                blockLocator.add(initialBtcStoredBlock.getHeader().getHash());
            }
        }
        return blockLocator;
    }

    public byte[] getBtcBlockchainBestBlockHeader() throws BlockStoreException, IOException {
        return serializeBlockHeader(getBtcBlockchainChainHead());
    }

    public byte[] getBtcBlockchainBlockHeaderByHash(Sha256Hash hash) throws IOException, BlockStoreException {
        this.ensureBtcBlockStore();

        return serializeBlockHeader(btcBlockStore.get(hash));
    }

    public byte[] getBtcBlockchainBlockHeaderByHeight(int height) throws BlockStoreException, IOException {
        Context.propagate(btcContext);
        this.ensureBtcBlockStore();

        StoredBlock block = btcBlockStore.getStoredBlockAtMainChainHeight(height);

        return serializeBlockHeader(block);
    }

    public byte[] getBtcBlockchainParentBlockHeaderByHash(Sha256Hash hash) throws IOException, BlockStoreException {
        this.ensureBtcBlockStore();

        StoredBlock block = btcBlockStore.get(hash);

        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        return serializeBlockHeader(btcBlockStore.get(block.getHeader().getPrevBlockHash()));
    }

    public Sha256Hash getBtcBlockchainBlockHashAtDepth(int depth) throws BlockStoreException, IOException {
        Context.propagate(btcContext);
        this.ensureBtcBlockStore();

        StoredBlock head = btcBlockStore.getChainHead();
        int maxDepth = head.getHeight() - getLowestBlock().getHeight();

        if (depth < 0 || depth > maxDepth) {
            throw new IndexOutOfBoundsException(String.format("Depth must be between 0 and %d", maxDepth));
        }

        StoredBlock blockAtDepth = btcBlockStore.getStoredBlockAtMainChainDepth(depth);
        return blockAtDepth.getHeader().getHash();
    }

    public Long getBtcTransactionConfirmationsGetCost(Object[] args) {
        final long BASIC_COST = 27_000;
        final long STEP_COST = 315;
        final long DOUBLE_HASH_COST = 144; // 72 * 2. 72 is the cost of the hash operation

        Sha256Hash btcBlockHash;
        int branchHashesSize;
        try {
            btcBlockHash = Sha256Hash.wrap((byte[]) args[1]);
            Object[] merkleBranchHashesArray = (Object[]) args[3];
            branchHashesSize = merkleBranchHashesArray.length;
        } catch (NullPointerException | IllegalArgumentException e) {
            return BASIC_COST;
        }

        // Dynamic cost based on the depth of the block that contains
        // the transaction. Find such depth first, then calculate
        // the cost.
        Context.propagate(btcContext);
        try {
            this.ensureBtcBlockStore();
            final StoredBlock block = btcBlockStore.getFromCache(btcBlockHash);

            // Block not found, default to basic cost
            if (block == null) {
                return BASIC_COST;
            }

            final int bestChainHeight = getBtcBlockchainBestChainHeight();

            // Make sure calculated depth is >= 0
            final int blockDepth = Math.max(0, bestChainHeight - block.getHeight());

            // Block too deep, default to basic cost
            if (blockDepth > BTC_TRANSACTION_CONFIRMATION_MAX_DEPTH) {
                return BASIC_COST;
            }

            return BASIC_COST + blockDepth*STEP_COST + branchHashesSize*DOUBLE_HASH_COST;
        } catch (IOException | BlockStoreException e) {
            logger.warn("getBtcTransactionConfirmationsGetCost btcBlockHash:{} there was a problem " +
                    "gathering the block depth while calculating the gas cost. " +
                    "Defaulting to basic cost.", btcBlockHash, e);
            return BASIC_COST;
        }
    }

    /**
     * @param btcTxHash The BTC transaction Hash
     * @param btcBlockHash The BTC block hash
     * @param merkleBranch The merkle branch
     * @throws BlockStoreException
     * @throws IOException
     */
    public Integer getBtcTransactionConfirmations(Sha256Hash btcTxHash, Sha256Hash btcBlockHash, MerkleBranch merkleBranch) throws BlockStoreException, IOException {
        Context.propagate(btcContext);
        this.ensureBtcBlockChain();

        // Get the block using the given block hash
        StoredBlock block = btcBlockStore.getFromCache(btcBlockHash);
        if (block == null) {
            return BTC_TRANSACTION_CONFIRMATION_INEXISTENT_BLOCK_HASH_ERROR_CODE;
        }

        final int bestChainHeight = getBtcBlockchainBestChainHeight();

        // Prevent diving too deep in the blockchain to avoid high processing costs
        final int blockDepth = Math.max(0, bestChainHeight - block.getHeight());
        if (blockDepth > BTC_TRANSACTION_CONFIRMATION_MAX_DEPTH) {
            return BTC_TRANSACTION_CONFIRMATION_BLOCK_TOO_OLD_ERROR_CODE;
        }

        try {
            StoredBlock storedBlock = btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight());
            // Make sure it belongs to the best chain
            if (storedBlock == null || !storedBlock.equals(block)){
                return BTC_TRANSACTION_CONFIRMATION_BLOCK_NOT_IN_BEST_CHAIN_ERROR_CODE;
            }
        } catch (BlockStoreException e) {
            logger.warn(String.format(
                    "Illegal state trying to get block with hash %s",
                    btcBlockHash
            ), e);
            return BTC_TRANSACTION_CONFIRMATION_INCONSISTENT_BLOCK_ERROR_CODE;
        }

        Sha256Hash merkleRoot = merkleBranch.reduceFrom(btcTxHash);

        if (!isBlockMerkleRootValid(merkleRoot, block.getHeader())) {
            return BTC_TRANSACTION_CONFIRMATION_INVALID_MERKLE_BRANCH_ERROR_CODE;
        }

        return bestChainHeight - block.getHeight() + 1;
    }

    private StoredBlock getPrevBlockAtHeight(StoredBlock cursor, int height) throws BlockStoreException {
        if (cursor.getHeight() == height) {
            return cursor;
        }

        boolean stop = false;
        StoredBlock current = cursor;
        while (!stop) {
            current = current.getPrev(this.btcBlockStore);
            stop = current.getHeight() == height;
        }
        return current;
    }

    /**
     * Returns whether a given btc transaction hash has already
     * been processed by the bridge.
     * @param btcTxHash the btc tx hash to check.
     * @return a Boolean indicating whether the given btc tx hash was
     * already processed by the bridge.
     * @throws IOException
     */
    public Boolean isBtcTxHashAlreadyProcessed(Sha256Hash btcTxHash) throws IOException {
        return provider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTxHash).isPresent();
    }

    /**
     * Returns the RSK blockchain height a given btc transaction hash
     * was processed at by the bridge.
     * @param btcTxHash the btc tx hash for which to retrieve the height.
     * @return a Long with the processed height. If the hash was not processed
     * -1 is returned.
     * @throws IOException
     */
    public Long getBtcTxHashProcessedHeight(Sha256Hash btcTxHash) throws IOException {
        // Return -1 if the transaction hasn't been processed
        return provider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTxHash).orElse(-1L);
    }

    /**
     * Returns if tx was already processed by the bridge
     * @param btcTxHash the btc tx hash for which to retrieve the height.
     * @return true or false according
     * @throws  IOException
     * */
    public boolean isAlreadyBtcTxHashProcessed(Sha256Hash btcTxHash) throws IOException {
        if (getBtcTxHashProcessedHeight(btcTxHash) > -1L) {
            logger.warn("Supplied Btc Tx {} was already processed", btcTxHash);
            return true;
        }

        return false;
    }

    /**
     * Returns the currently active federation.
     * See getActiveFederationReference() for details.
     * @return the currently active federation.
     */
    public Federation getActiveFederation() {
        return federationSupport.getActiveFederation();
    }

    /**
     * Returns the currently retiring federation.
     * See getRetiringFederationReference() for details.
     * @return the retiring federation.
     */
    @Nullable
    public Federation getRetiringFederation() {
        return federationSupport.getRetiringFederation();
    }

    private List<UTXO> getActiveFederationBtcUTXOs() throws IOException {
        return federationSupport.getActiveFederationBtcUTXOs();
    }

    private List<UTXO> getRetiringFederationBtcUTXOs() throws IOException {
        return federationSupport.getRetiringFederationBtcUTXOs();
    }

    /**
     * Returns the federation bitcoin address.
     * @return the federation bitcoin address.
     */
    public Address getFederationAddress() {
        return getActiveFederation().getAddress();
    }

    /**
     * Returns the federation's size
     * @return the federation size
     */
    public Integer getFederationSize() {
        return getActiveFederation().getBtcPublicKeys().size();
    }

    /**
     * Returns the federation's minimum required signatures
     * @return the federation minimum required signatures
     */
    public Integer getFederationThreshold() {
        return getActiveFederation().getNumberOfSignaturesRequired();
    }

    /**
     * Returns the public key of the federation's federator at the given index
     * @param index the federator's index (zero-based)
     * @return the federator's public key
     */
    public byte[] getFederatorPublicKey(int index) {
        return federationSupport.getFederatorBtcPublicKey(index);
    }

    /**
     * Returns the public key of given type of the federation's federator at the given index
     * @param index the federator's index (zero-based)
     * @param keyType the key type
     * @return the federator's public key
     */
    public byte[] getFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        return federationSupport.getFederatorPublicKeyOfType(index, keyType);
    }

    /**
     * Returns the federation's creation time
     * @return the federation creation time
     */
    public Instant getFederationCreationTime() {
        return getActiveFederation().getCreationTime();
    }

    /**
     * Returns the federation's creation block number
     * @return the federation creation block number
     */
    public long getFederationCreationBlockNumber() {
        return getActiveFederation().getCreationBlockNumber();
    }

    /**
     * Returns the retiring federation bitcoin address.
     * @return the retiring federation bitcoin address, null if no retiring federation exists
     */
    public Address getRetiringFederationAddress() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return null;
        }

        return retiringFederation.getAddress();
    }

    /**
     * Returns the retiring federation's size
     * @return the retiring federation size, -1 if no retiring federation exists
     */
    public Integer getRetiringFederationSize() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return -1;
        }

        return retiringFederation.getBtcPublicKeys().size();
    }

    /**
     * Returns the retiring federation's minimum required signatures
     * @return the retiring federation minimum required signatures, -1 if no retiring federation exists
     */
    public Integer getRetiringFederationThreshold() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return -1;
        }

        return retiringFederation.getNumberOfSignaturesRequired();
    }

    /**
     * Returns the public key of the retiring federation's federator at the given index
     * @param index the retiring federator's index (zero-based)
     * @return the retiring federator's public key, null if no retiring federation exists
     */
    public byte[] getRetiringFederatorPublicKey(int index) {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return null;
        }

        List<BtcECKey> publicKeys = retiringFederation.getBtcPublicKeys();

        if (index < 0 || index >= publicKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Retiring federator index must be between 0 and %d", publicKeys.size() - 1));
        }

        return publicKeys.get(index).getPubKey();
    }

    /**
     * Returns the public key of the given type of the retiring federation's federator at the given index
     * @param index the retiring federator's index (zero-based)
     * @param keyType the key type
     * @return the retiring federator's public key of the given type, null if no retiring federation exists
     */
    public byte[] getRetiringFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return null;
        }

        return federationSupport.getMemberPublicKeyOfType(retiringFederation.getMembers(), index, keyType, "Retiring federator");
    }

    /**
     * Returns the retiring federation's creation time
     * @return the retiring federation creation time, null if no retiring federation exists
     */
    public Instant getRetiringFederationCreationTime() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return null;
        }

        return retiringFederation.getCreationTime();
    }

    /**
     * Returns the retiring federation's creation block number
     * @return the retiring federation creation block number,
     * -1 if no retiring federation exists
     */
    public long getRetiringFederationCreationBlockNumber() {
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation == null) {
            return -1L;
        }
        return retiringFederation.getCreationBlockNumber();
    }

    /**
     * Returns the currently live federations
     * This would be the active federation plus
     * potentially the retiring federation
     * @return a list of live federations
     */
    private List<Federation> getLiveFederations() {
        List<Federation> liveFederations = new ArrayList<>();
        liveFederations.add(getActiveFederation());
        Federation retiringFederation = getRetiringFederation();
        if (retiringFederation != null) {
            liveFederations.add(retiringFederation);
        }
        return liveFederations;
    }

    /**
     * Creates a new pending federation
     * If there's currently no pending federation and no funds remain
     * to be moved from a previous federation, a new one is created.
     * Otherwise, -1 is returned if there's already a pending federation,
     * -2 is returned if there is a federation awaiting to be active,
     * or -3 if funds are left from a previous one.
     * @param dryRun whether to just do a dry run
     * @return 1 upon success, -1 when a pending federation is present,
     * -2 when a federation is to be activated,
     * and if -3 funds are still to be moved between federations.
     */
    private Integer createFederation(boolean dryRun) {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation != null) {
            return -1;
        }

        if (federationSupport.amAwaitingFederationActivation()) {
            return -2;
        }

        if (getRetiringFederation() != null) {
            return -3;
        }

        if (dryRun) {
            return 1;
        }

        currentPendingFederation = new PendingFederation(Collections.emptyList());

        provider.setPendingFederation(currentPendingFederation);

        // Clear votes on election
        provider.getFederationElection(bridgeConstants.getFederationChangeAuthorizer()).clear();

        return 1;
    }

    /**
     * Adds the given keys to the current pending federation.
     *
     * @param dryRun whether to just do a dry run
     * @param btcKey the BTC public key to add
     * @param rskKey the RSK public key to add
     * @param mstKey the MST public key to add
     * @return 1 upon success, -1 if there was no pending federation, -2 if the key was already in the pending federation
     */
    private Integer addFederatorPublicKeyMultikey(boolean dryRun, BtcECKey btcKey, ECKey rskKey, ECKey mstKey) {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return -1;
        }

        if (currentPendingFederation.getBtcPublicKeys().contains(btcKey) ||
            currentPendingFederation.getMembers().stream().map(FederationMember::getRskPublicKey).anyMatch(k -> k.equals(rskKey)) ||
            currentPendingFederation.getMembers().stream().map(FederationMember::getMstPublicKey).anyMatch(k -> k.equals(mstKey))) {
            return -2;
        }

        if (dryRun) {
            return 1;
        }

        FederationMember member = new FederationMember(btcKey, rskKey, mstKey);

        currentPendingFederation = currentPendingFederation.addMember(member);

        provider.setPendingFederation(currentPendingFederation);

        return 1;
    }

    /**
     * Commits the currently pending federation.
     * That is, the retiring federation is set to be the currently active federation,
     * the active federation is replaced with a new federation generated from the pending federation,
     * and the pending federation is wiped out.
     * Also, UTXOs are moved from active to retiring so that the transfer of funds can
     * begin.
     * @param dryRun whether to just do a dry run
     * @param hash the pending federation's hash. This is checked the execution block's pending federation hash for equality.
     * @return 1 upon success, -1 if there was no pending federation, -2 if the pending federation was incomplete,
     * -3 if the given hash doesn't match the current pending federation's hash.
     */
    protected Integer commitFederation(boolean dryRun, Keccak256 hash) throws IOException {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return -1;
        }

        if (!currentPendingFederation.isComplete()) {
            return -2;
        }

        if (!hash.equals(currentPendingFederation.getHash())) {
            return -3;
        }

        if (dryRun) {
            return 1;
        }

        // Move UTXOs from the new federation into the old federation
        // and clear the new federation's UTXOs
        List<UTXO> utxosToMove = new ArrayList<>(provider.getNewFederationBtcUTXOs());
        provider.getNewFederationBtcUTXOs().clear();
        List<UTXO> oldFederationUTXOs = provider.getOldFederationBtcUTXOs();
        oldFederationUTXOs.clear();
        oldFederationUTXOs.addAll(utxosToMove);

        // Network parameters for the new federation are taken from the bridge constants.
        // Creation time is the block's timestamp.
        Instant creationTime = Instant.ofEpochMilli(rskExecutionBlock.getTimestamp());
        Federation oldFederation = getActiveFederation();
        provider.setOldFederation(oldFederation);
        provider.setNewFederation(
            currentPendingFederation.buildFederation(
                creationTime,
                rskExecutionBlock.getNumber(),
                bridgeConstants,
                activations
            )
        );
        provider.setPendingFederation(null);

        // Clear votes on election
        provider.getFederationElection(bridgeConstants.getFederationChangeAuthorizer()).clear();

        if (activations.isActive(RSKIP186)) {
            // Preserve federation change info
            long nextFederationCreationBlockHeight = rskExecutionBlock.getNumber();
            provider.setNextFederationCreationBlockHeight(nextFederationCreationBlockHeight);
            Script oldFederationP2SHScript = oldFederation.getP2SHScript();
            provider.setLastRetiredFederationP2SHScript(oldFederationP2SHScript);
        }

        logger.debug("[commitFederation] New Federation committed: {}", provider.getNewFederation().getAddress());
        eventLogger.logCommitFederation(rskExecutionBlock, provider.getOldFederation(), provider.getNewFederation());

        return 1;
    }

    /**
     * Rolls back the currently pending federation
     * That is, the pending federation is wiped out.
     * @param dryRun whether to just do a dry run
     * @return 1 upon success, 1 if there was no pending federation
     */
    private Integer rollbackFederation(boolean dryRun) {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return -1;
        }

        if (dryRun) {
            return 1;
        }

        provider.setPendingFederation(null);

        // Clear votes on election
        provider.getFederationElection(bridgeConstants.getFederationChangeAuthorizer()).clear();

        return 1;
    }

    public Integer voteFederationChange(Transaction tx, ABICallSpec callSpec) throws BridgeIllegalArgumentException {
        // Must be on one of the allowed functions
        if (!FEDERATION_CHANGE_FUNCTIONS.contains(callSpec.getFunction())) {
            return FEDERATION_CHANGE_GENERIC_ERROR_CODE;
        }

        AddressBasedAuthorizer authorizer = bridgeConstants.getFederationChangeAuthorizer();

        // Must be authorized to vote (checking for signature)
        if (!authorizer.isAuthorized(tx)) {
            return FEDERATION_CHANGE_GENERIC_ERROR_CODE;
        }

        // Try to do a dry-run and only register the vote if the
        // call would be successful
        ABICallVoteResult result;
        try {
            result = executeVoteFederationChangeFunction(true, callSpec);
        } catch (IOException | BridgeIllegalArgumentException e) {
            result = new ABICallVoteResult(false, FEDERATION_CHANGE_GENERIC_ERROR_CODE);
        }

        // Return if the dry run failed or we are on a reversible execution
        if (!result.wasSuccessful()) {
            return (Integer) result.getResult();
        }

        ABICallElection election = provider.getFederationElection(authorizer);
        // Register the vote. It is expected to succeed, since all previous checks succeeded
        if (!election.vote(callSpec, tx.getSender())) {
            logger.warn("Unexpected federation change vote failure");
            return FEDERATION_CHANGE_GENERIC_ERROR_CODE;
        }

        // If enough votes have been reached, then actually execute the function
        ABICallSpec winnerSpec = election.getWinner();
        if (winnerSpec != null) {
            try {
                result = executeVoteFederationChangeFunction(false, winnerSpec);
            } catch (IOException e) {
                logger.warn("Unexpected federation change vote exception: {}", e.getMessage());
                return FEDERATION_CHANGE_GENERIC_ERROR_CODE;
            } finally {
                // Clear the winner so that we don't repeat ourselves
                election.clearWinners();
            }
        }

        return (Integer) result.getResult();
    }

    private ABICallVoteResult executeVoteFederationChangeFunction(boolean dryRun, ABICallSpec callSpec) throws IOException, BridgeIllegalArgumentException {
        // Try to do a dry-run and only register the vote if the
        // call would be successful
        ABICallVoteResult result;
        Integer executionResult;
        switch (callSpec.getFunction()) {
            case "create":
                executionResult = createFederation(dryRun);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            case "add":
                byte[] publicKeyBytes = callSpec.getArguments()[0];
                BtcECKey publicKey;
                ECKey publicKeyEc;
                try {
                    publicKey = BtcECKey.fromPublicOnly(publicKeyBytes);
                    publicKeyEc = ECKey.fromPublicOnly(publicKeyBytes);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("Public key could not be parsed " + ByteUtil.toHexString(publicKeyBytes), e);
                }
                executionResult = addFederatorPublicKeyMultikey(dryRun, publicKey, publicKeyEc, publicKeyEc);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            case "add-multi":
                BtcECKey btcPublicKey;
                ECKey rskPublicKey, mstPublicKey;
                try {
                    btcPublicKey = BtcECKey.fromPublicOnly(callSpec.getArguments()[0]);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("BTC public key could not be parsed " + ByteUtil.toHexString(callSpec.getArguments()[0]), e);
                }

                try {
                    rskPublicKey = ECKey.fromPublicOnly(callSpec.getArguments()[1]);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("RSK public key could not be parsed " + ByteUtil.toHexString(callSpec.getArguments()[1]), e);
                }

                try {
                    mstPublicKey = ECKey.fromPublicOnly(callSpec.getArguments()[2]);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("MST public key could not be parsed " + ByteUtil.toHexString(callSpec.getArguments()[2]), e);
                }
                executionResult = addFederatorPublicKeyMultikey(dryRun, btcPublicKey, rskPublicKey, mstPublicKey);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            case "commit":
                Keccak256 hash = new Keccak256((byte[]) callSpec.getArguments()[0]);
                executionResult = commitFederation(dryRun, hash);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            case "rollback":
                executionResult = rollbackFederation(dryRun);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            default:
                // Fail by default
                result = new ABICallVoteResult(false, FEDERATION_CHANGE_GENERIC_ERROR_CODE);
        }

        return result;
    }

    /**
     * Returns the currently pending federation hash, or null if none exists
     * @return the currently pending federation hash, or null if none exists
     */
    public byte[] getPendingFederationHash() {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return null;
        }

        return currentPendingFederation.getHash().getBytes();
    }

    /**
     * Returns the currently pending federation size, or -1 if none exists
     * @return the currently pending federation size, or -1 if none exists
     */
    public Integer getPendingFederationSize() {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return -1;
        }

        return currentPendingFederation.getBtcPublicKeys().size();
    }

    /**
     * Returns the currently pending federation federator's public key at the given index, or null if none exists
     * @param index the federator's index (zero-based)
     * @return the pending federation's federator public key
     */
    public byte[] getPendingFederatorPublicKey(int index) {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return null;
        }

        List<BtcECKey> publicKeys = currentPendingFederation.getBtcPublicKeys();

        if (index < 0 || index >= publicKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Federator index must be between 0 and %d", publicKeys.size() - 1));
        }

        return publicKeys.get(index).getPubKey();
    }

    /**
     * Returns the public key of the given type of the pending federation's federator at the given index
     * @param index the federator's index (zero-based)
     * @param keyType the key type
     * @return the pending federation's federator public key of given type
     */
    public byte[] getPendingFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return null;
        }

        return federationSupport.getMemberPublicKeyOfType(currentPendingFederation.getMembers(), index, keyType, "Federator");
    }

    /**
     * Returns the lock whitelist size, that is,
     * the number of whitelisted addresses
     * @return the lock whitelist size
     */
    public Integer getLockWhitelistSize() {
        return provider.getLockWhitelist().getSize();
    }

    /**
     * Returns the lock whitelist address stored
     * at the given index, or null if the
     * index is out of bounds
     * @param index the index at which to get the address
     * @return the base58-encoded address stored at the given index, or null if index is out of bounds
     */
    public LockWhitelistEntry getLockWhitelistEntryByIndex(int index) {
        List<LockWhitelistEntry> entries = provider.getLockWhitelist().getAll();

        if (index < 0 || index >= entries.size()) {
            return null;
        }

        return entries.get(index);
    }

    /**
     *
     * @param addressBase58
     * @return
     */
    public LockWhitelistEntry getLockWhitelistEntryByAddress(String addressBase58) {
        try {
            Address address = getParsedAddress(addressBase58);
            return provider.getLockWhitelist().get(address);
        } catch (AddressFormatException e) {
            logger.warn(INVALID_ADDRESS_FORMAT_MESSAGE, e);
            return null;
        }
    }

    /**
     * Adds the given address to the lock whitelist.
     * Returns 1 upon success, or -1 if the address was
     * already in the whitelist.
     * @param addressBase58 the base58-encoded address to add to the whitelist
     * @param maxTransferValue the max amount of satoshis enabled to transfer for this address
     * @return 1 upon success, -1 if the address was already
     * in the whitelist, -2 if address is invalid
     * LOCK_WHITELIST_GENERIC_ERROR_CODE otherwise.
     */
    public Integer addOneOffLockWhitelistAddress(Transaction tx, String addressBase58, BigInteger maxTransferValue) {
        try {
            Address address = getParsedAddress(addressBase58);
            Coin maxTransferValueCoin = Coin.valueOf(maxTransferValue.longValueExact());
            return this.addLockWhitelistAddress(tx, new OneOffWhiteListEntry(address, maxTransferValueCoin));
        } catch (AddressFormatException e) {
            logger.warn(INVALID_ADDRESS_FORMAT_MESSAGE, e);
            return LOCK_WHITELIST_INVALID_ADDRESS_FORMAT_ERROR_CODE;
        }
    }

    public Integer addUnlimitedLockWhitelistAddress(Transaction tx, String addressBase58) {
        try {
            Address address = getParsedAddress(addressBase58);
            return this.addLockWhitelistAddress(tx, new UnlimitedWhiteListEntry(address));
        } catch (AddressFormatException e) {
            logger.warn(INVALID_ADDRESS_FORMAT_MESSAGE, e);
            return LOCK_WHITELIST_INVALID_ADDRESS_FORMAT_ERROR_CODE;
        }
    }

    private Integer addLockWhitelistAddress(Transaction tx, LockWhitelistEntry entry) {
        if (!isLockWhitelistChangeAuthorized(tx)) {
            return LOCK_WHITELIST_GENERIC_ERROR_CODE;
        }

        LockWhitelist whitelist = provider.getLockWhitelist();

        try {
            if (whitelist.isWhitelisted(entry.address())) {
                return LOCK_WHITELIST_ALREADY_EXISTS_ERROR_CODE;
            }
            whitelist.put(entry.address(), entry);
            return LOCK_WHITELIST_SUCCESS_CODE;
        } catch (Exception e) {
            logger.error("Unexpected error in addLockWhitelistAddress: {}", e.getMessage());
            panicProcessor.panic("lock-whitelist", e.getMessage());
            return LOCK_WHITELIST_UNKNOWN_ERROR_CODE;
        }
    }

    private boolean isLockWhitelistChangeAuthorized(Transaction tx) {
        AddressBasedAuthorizer authorizer = bridgeConstants.getLockWhitelistChangeAuthorizer();

        return authorizer.isAuthorized(tx);
    }

    /**
     * Removes the given address from the lock whitelist.
     * Returns 1 upon success, or -1 if the address was
     * not in the whitelist.
     * @param addressBase58 the base58-encoded address to remove from the whitelist
     * @return 1 upon success, -1 if the address was not
     * in the whitelist, -2 if the address is invalid,
     * LOCK_WHITELIST_GENERIC_ERROR_CODE otherwise.
     */
    public Integer removeLockWhitelistAddress(Transaction tx, String addressBase58) {
        if (!isLockWhitelistChangeAuthorized(tx)) {
            return LOCK_WHITELIST_GENERIC_ERROR_CODE;
        }

        LockWhitelist whitelist = provider.getLockWhitelist();

        try {
            Address address = getParsedAddress(addressBase58);

            if (!whitelist.remove(address)) {
                return -1;
            }

            return 1;
        } catch (AddressFormatException e) {
            return -2;
        } catch (Exception e) {
            logger.error("Unexpected error in removeLockWhitelistAddress: {}", e.getMessage());
            panicProcessor.panic("lock-whitelist", e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the minimum amount of satoshis a user should send to the federation.
     * @return the minimum amount of satoshis a user should send to the federation.
     */
    public Coin getMinimumPeginTxValue() {
        return activations.isActive(RSKIP219) ? bridgeConstants.getMinimumPeginTxValueInSatoshis() : bridgeConstants.getLegacyMinimumPeginTxValueInSatoshis();
    }

    /**
     * Votes for a fee per kb value.
     *
     * @return 1 upon successful vote, -1 when the vote was unsuccessful,
     * FEE_PER_KB_GENERIC_ERROR_CODE when there was an un expected error.
     */
    public Integer voteFeePerKbChange(Transaction tx, Coin feePerKb) {
        AddressBasedAuthorizer authorizer = bridgeConstants.getFeePerKbChangeAuthorizer();
        if (!authorizer.isAuthorized(tx)) {
            return FEE_PER_KB_GENERIC_ERROR_CODE;
        }

        if(!feePerKb.isPositive()){
            return NEGATIVE_FEE_PER_KB_ERROR_CODE;
        }

        if(feePerKb.isGreaterThan(bridgeConstants.getMaxFeePerKb())) {
            return EXCESSIVE_FEE_PER_KB_ERROR_CODE;
        }

        ABICallElection feePerKbElection = provider.getFeePerKbElection(authorizer);
        ABICallSpec feeVote = new ABICallSpec("setFeePerKb", new byte[][]{BridgeSerializationUtils.serializeCoin(feePerKb)});
        boolean successfulVote = feePerKbElection.vote(feeVote, tx.getSender());
        if (!successfulVote) {
            return -1;
        }

        ABICallSpec winner = feePerKbElection.getWinner();
        if (winner == null) {
            logger.info("Successful fee per kb vote for {}", feePerKb);
            return 1;
        }

        Coin winnerFee;
        try {
            winnerFee = BridgeSerializationUtils.deserializeCoin(winner.getArguments()[0]);
        } catch (Exception e) {
            logger.warn("Exception deserializing winner feePerKb", e);
            return FEE_PER_KB_GENERIC_ERROR_CODE;
        }

        if (winnerFee == null) {
            logger.warn("Invalid winner feePerKb: feePerKb can't be null");
            return FEE_PER_KB_GENERIC_ERROR_CODE;
        }

        if (!winnerFee.equals(feePerKb)) {
            logger.debug("Winner fee is different than the last vote: maybe you forgot to clear winners");
        }

        logger.info("Fee per kb changed to {}", winnerFee);
        provider.setFeePerKb(winnerFee);
        feePerKbElection.clear();
        return 1;
    }

    /**
     * Sets a delay in the BTC best chain to disable lock whitelist
     * @param tx current RSK transaction
     * @param disableBlockDelayBI block since current BTC best chain height to disable lock whitelist
     * @return 1 if it was successful, -1 if a delay was already set, -2 if disableBlockDelay contains an invalid value
     */
    public Integer setLockWhitelistDisableBlockDelay(Transaction tx, BigInteger disableBlockDelayBI) throws IOException, BlockStoreException {
        if (!isLockWhitelistChangeAuthorized(tx)) {
            return LOCK_WHITELIST_GENERIC_ERROR_CODE;
        }
        LockWhitelist lockWhitelist = provider.getLockWhitelist();
        if (lockWhitelist.isDisableBlockSet()) {
            return -1;
        }
        int disableBlockDelay = disableBlockDelayBI.intValueExact();
        int bestChainHeight = getBtcBlockchainBestChainHeight();
        if (disableBlockDelay + bestChainHeight <= bestChainHeight) {
            return -2;
        }
        lockWhitelist.setDisableBlockHeight(bestChainHeight + disableBlockDelay);
        return 1;
    }

    public Coin getLockingCap() {
        // Before returning the locking cap, check if it was already set
        if (activations.isActive(ConsensusRule.RSKIP134) && this.provider.getLockingCap() == null) {
            // Set the initial locking cap value
            logger.debug("Setting initial locking cap value");
            this.provider.setLockingCap(bridgeConstants.getInitialLockingCap());
        }

        return this.provider.getLockingCap();
    }

    public boolean increaseLockingCap(Transaction tx, Coin newCap) {
        // Only pre configured addresses can modify locking cap
        AddressBasedAuthorizer authorizer = bridgeConstants.getIncreaseLockingCapAuthorizer();
        if (!authorizer.isAuthorized(tx)) {
            logger.warn("not authorized address tried to increase locking cap. Address: {}", tx.getSender());
            return false;
        }
        // new locking cap must be bigger than current locking cap
        Coin currentLockingCap = this.getLockingCap();
        if (newCap.compareTo(currentLockingCap) < 0) {
            logger.warn("attempted value doesn't increase locking cap. Attempted: {}", newCap.value);
            return false;
        }
        Coin maxLockingCap = currentLockingCap.multiply(bridgeConstants.getLockingCapIncrementsMultiplier());
        if (newCap.compareTo(maxLockingCap) > 0) {
            logger.warn("attempted value increases locking cap above its limit. Attempted: {}", newCap.value);
            return false;
        }

        logger.info("increased locking cap: {}", newCap.value);
        this.provider.setLockingCap(newCap);

        return true;
    }

    public void registerBtcCoinbaseTransaction(byte[] btcTxSerialized, Sha256Hash blockHash, byte[] pmtSerialized, Sha256Hash witnessMerkleRoot, byte[] witnessReservedValue) throws VMException {
        Context.propagate(btcContext);
        try{
            this.ensureBtcBlockStore();
        }catch (BlockStoreException | IOException e) {
            logger.warn("Exception in registerBtcCoinbaseTransaction", e);
            throw new VMException("Exception in registerBtcCoinbaseTransaction", e);
        }

        Sha256Hash btcTxHash = BtcTransactionFormatUtils.calculateBtcTxHash(btcTxSerialized);

        if (witnessReservedValue.length != 32) {
            logger.warn("[btcTx:{}] WitnessResevedValue length can't be different than 32 bytes", btcTxHash);
            throw new BridgeIllegalArgumentException("WitnessResevedValue length can't be different than 32 bytes");
        }

        if (!PartialMerkleTreeFormatUtils.hasExpectedSize(pmtSerialized)) {
            logger.warn("[btcTx:{}] PartialMerkleTree doesn't have expected size", btcTxHash);
            throw new BridgeIllegalArgumentException("PartialMerkleTree doesn't have expected size");
        }

        Sha256Hash merkleRoot;

        try {
            PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), pmtSerialized, 0);
            List<Sha256Hash> hashesInPmt = new ArrayList<>();
            merkleRoot = pmt.getTxnHashAndMerkleRoot(hashesInPmt);
            if (!hashesInPmt.contains(btcTxHash)) {
                logger.warn("Supplied Btc Tx {} is not in the supplied partial merkle tree", btcTxHash);
                return;
            }
        } catch (VerificationException e) {
            logger.warn("[btcTx:{}] PartialMerkleTree could not be parsed", btcTxHash);
            throw new BridgeIllegalArgumentException(String.format("PartialMerkleTree could not be parsed %s", ByteUtil.toHexString(pmtSerialized)), e);
        }

        // Check merkle root equals btc block merkle root at the specified height in the btc best chain
        // Btc blockstore is available since we've already queried the best chain height
        StoredBlock storedBlock = btcBlockStore.getFromCache(blockHash);
        if (storedBlock == null) {
            logger.warn("[btcTx:{}] Block not registered", btcTxHash);
            throw new BridgeIllegalArgumentException(String.format("Block not registered %s", blockHash.toString()));
        }
        BtcBlock blockHeader = storedBlock.getHeader();
        if (!blockHeader.getMerkleRoot().equals(merkleRoot)) {
            String panicMessage = String.format(
                    "Btc Tx %s Supplied merkle root %s does not match block's merkle root %s",
                    btcTxHash.toString(),
                    merkleRoot,
                    blockHeader.getMerkleRoot()
            );
            logger.warn(panicMessage);
            panicProcessor.panic("btclock", panicMessage);
            return;
        }

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams(), btcTxSerialized);
        btcTx.verify();

        Sha256Hash witnessCommitment = Sha256Hash.twiceOf(witnessMerkleRoot.getReversedBytes(), witnessReservedValue);

        if(!witnessCommitment.equals(btcTx.findWitnessCommitment())){
            logger.warn("[btcTx:{}] WitnessCommitment does not match", btcTxHash);
            throw new BridgeIllegalArgumentException("WitnessCommitment does not match");
        }

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(blockHeader.getHash(), coinbaseInformation);

        logger.warn("[btcTx:{}] Registered coinbase information", btcTxHash);
    }

    public boolean hasBtcBlockCoinbaseTransactionInformation(Sha256Hash blockHash) {
        CoinbaseInformation coinbaseInformation = provider.getCoinbaseInformation(blockHash);
        return coinbaseInformation != null;
    }

    public long getActiveFederationCreationBlockHeight() {
        if (!activations.isActive(RSKIP186)) {
            return 0L;
        }

        Optional<Long> nextFederationCreationBlockHeightOpt = provider.getNextFederationCreationBlockHeight();
        if (nextFederationCreationBlockHeightOpt.isPresent()) {
            long nextFederationCreationBlockHeight = nextFederationCreationBlockHeightOpt.get();
            long curBlockHeight = rskExecutionBlock.getNumber();
            if (curBlockHeight >= nextFederationCreationBlockHeight + bridgeConstants.getFederationActivationAge()) {
                return nextFederationCreationBlockHeight;
            }
        }

        Optional<Long> activeFederationCreationBlockHeightOpt = provider.getActiveFederationCreationBlockHeight();
        return activeFederationCreationBlockHeightOpt.orElse(0L);
    }

    public long getNextPegoutCreationBlockNumber() {
        return activations.isActive(RSKIP271) ? provider.getNextPegoutHeight().orElse(0L) : 0L;
    }

    public int getQueuedPegoutsCount() throws IOException {
        if (activations.isActive(RSKIP271)) {
            return provider.getReleaseRequestQueueSize();
        }
        return 0;
    }

    public Coin getEstimatedFeesForNextPegOutEvent() throws IOException {
        //  This method returns the fees of a peg-out transaction containing (N+2) outputs and 2 inputs,
        //  where N is the number of peg-outs requests waiting in the queue.

        final int INPUT_MULTIPLIER = 2; // 2 inputs

        int pegoutRequestsCount = getQueuedPegoutsCount();

        if (!activations.isActive(RSKIP271) || pegoutRequestsCount == 0) {
            return Coin.ZERO;
        }

        int totalOutputs = pegoutRequestsCount + 2; // N + 2 outputs

        int pegoutTxSize = BridgeUtils.calculatePegoutTxSize(activations, getActiveFederation(), INPUT_MULTIPLIER, totalOutputs);

        Coin feePerKB = getFeePerKb();

        return feePerKB
                .multiply(pegoutTxSize) // times the size in bytes
                .divide(1000);
    }

    public BigInteger registerFastBridgeBtcTransaction(
        Transaction rskTx,
        byte[] btcTxSerialized,
        int height,
        byte[] pmtSerialized,
        Keccak256 derivationArgumentsHash,
        Address userRefundAddress,
        RskAddress lbcAddress,
        Address lpBtcAddress,
        boolean shouldTransferToContract
    )
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        if (!BridgeUtils.isContractTx(rskTx)) {
            logger.debug("[registerFastBridgeBtcTransaction] (rskTx:{}) Sender not a contract", rskTx.getHash());
            return BigInteger.valueOf(FAST_BRIDGE_UNPROCESSABLE_TX_NOT_CONTRACT_ERROR_CODE);
        }

        if (!rskTx.getSender().equals(lbcAddress)) {
            logger.debug(
                "[registerFastBridgeBtcTransaction] Expected sender to be the same as lbcAddress. (sender: {}) (lbcAddress:{})",
                rskTx.getSender(),
                lbcAddress
            );
            return BigInteger.valueOf(FAST_BRIDGE_UNPROCESSABLE_TX_INVALID_SENDER_ERROR_CODE);
        }

        Context.propagate(btcContext);
        Sha256Hash btcTxHash = BtcTransactionFormatUtils.calculateBtcTxHash(btcTxSerialized);

        Keccak256 fastBridgeDerivationHash = getFastBridgeDerivationHash(
            derivationArgumentsHash,
            userRefundAddress,
            lpBtcAddress,
            lbcAddress
        );

        if (provider.isFastBridgeFederationDerivationHashUsed(btcTxHash, fastBridgeDerivationHash)) {
            logger.debug("[registerFastBridgeBtcTransaction] Transaction and derivation hash already used");
            return BigInteger.valueOf(FAST_BRIDGE_UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR_CODE);
        }

        if (!validationsForRegisterBtcTransaction(btcTxHash, height, pmtSerialized, btcTxSerialized)) {
            logger.debug(
                "[registerFastBridgeBtcTransaction] (btcTx:{}) error during validationsForRegisterBtcTransaction",
                btcTxHash
            );
            return BigInteger.valueOf(FAST_BRIDGE_UNPROCESSABLE_TX_VALIDATIONS_ERROR);
        }

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams(), btcTxSerialized);
        btcTx.verify();

        Sha256Hash btcTxHashWithoutWitness = btcTx.getHash(false);
        if (!btcTxHashWithoutWitness.equals(btcTxHash) &&
            provider.isFastBridgeFederationDerivationHashUsed(btcTxHashWithoutWitness, derivationArgumentsHash)) {
            logger.debug("[registerFastBridgeBtcTransaction] Transaction and derivation hash already used");
            return BigInteger.valueOf(FAST_BRIDGE_UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR_CODE);
        }

        FastBridgeFederationInformation fastBridgeFederationInformation =
            createFastBridgeFederationInformation(fastBridgeDerivationHash);

        Address fastBridgeFedAddress =
            fastBridgeFederationInformation.getFastBridgeFederationAddress(bridgeConstants.getBtcParams());

        Coin totalAmount = getAmountSentToAddress(btcTx, fastBridgeFedAddress);

        if (totalAmount == Coin.ZERO) {
            logger.debug("[registerFastBridgeBtcTransaction] Amount sent can't be 0");
            return BigInteger.valueOf(FAST_BRIDGE_UNPROCESSABLE_TX_VALUE_ZERO_ERROR);
        }

        if (!verifyLockDoesNotSurpassLockingCap(btcTx, totalAmount)) {
            InternalTransaction internalTx = (InternalTransaction) rskTx;
            logger.info("[registerFastBridgeBtcTransaction] Locking cap surpassed, going to return funds!");
            WalletProvider walletProvider = createFastBridgeWalletProvider(fastBridgeFederationInformation);

            provider.markFastBridgeFederationDerivationHashAsUsed(btcTxHash, fastBridgeDerivationHash);

            if (shouldTransferToContract) {
                logger.debug("[registerFastBridgeBtcTransaction] Returning to liquidity provider");
                generateRejectionRelease(btcTx, lpBtcAddress, fastBridgeFedAddress, new Keccak256(internalTx.getOriginHash()), totalAmount, walletProvider);
                return BigInteger.valueOf(FAST_BRIDGE_REFUNDED_LP_ERROR_CODE);
            } else {
                logger.debug("[registerFastBridgeBtcTransaction] Returning to user");
                generateRejectionRelease(btcTx, userRefundAddress, fastBridgeFedAddress, new Keccak256(internalTx.getOriginHash()), totalAmount, walletProvider);
                return BigInteger.valueOf(FAST_BRIDGE_REFUNDED_USER_ERROR_CODE);
            }
        }

        transferTo(lbcAddress, co.rsk.core.Coin.fromBitcoin(totalAmount));

        saveFastBridgeDataInStorage(
            btcTxHashWithoutWitness,
            fastBridgeDerivationHash,
            fastBridgeFederationInformation,
            getUTXOsForAddress(btcTx, fastBridgeFedAddress)
        );

        logger.info("[registerFastBridgeBtcTransaction] (btcTx:{}) transaction registered successfully", btcTxHashWithoutWitness);

        return co.rsk.core.Coin.fromBitcoin(totalAmount).asBigInteger();
    }

    protected FastBridgeFederationInformation createFastBridgeFederationInformation(Keccak256 fastBridgeDerivationHash) {
        Script fastBridgeScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            getActiveFederation().getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        Script fastBridgeScriptHash = ScriptBuilder.createP2SHOutputScript(fastBridgeScript);

        return new FastBridgeFederationInformation(
            fastBridgeDerivationHash,
            getActiveFederation().getP2SHScript().getPubKeyHash(),
            fastBridgeScriptHash.getPubKeyHash()
        );
    }

    private WalletProvider createFastBridgeWalletProvider(
        FastBridgeFederationInformation fastBridgeFederationInformation) {
        return (BtcTransaction a, Address b) -> {
            List<UTXO> utxosList = getUTXOsForAddress(a, b);
            return getFastBridgeWallet(btcContext, utxosList, fastBridgeFederationInformation);
        };
    }

    protected List<UTXO> getUTXOsForAddress(BtcTransaction btcTx, Address btcAddress) {
        List<UTXO> utxosList = new ArrayList<>();
        for (TransactionOutput o : btcTx.getOutputs()) {
            if (o.getScriptPubKey().getToAddress(bridgeConstants.getBtcParams()).equals(btcAddress)) {
                utxosList.add(
                    new UTXO(
                        btcTx.getHash(),
                        o.getIndex(),
                        o.getValue(),
                        0,
                        btcTx.isCoinBase(),
                        o.getScriptPubKey()
                    )
                );
            }
        }

        return utxosList;
    }

    protected Wallet getFastBridgeWallet(Context btcContext, List<UTXO> utxos, FastBridgeFederationInformation fb) {
        Wallet wallet = new FastBridgeCompatibleBtcWalletWithSingleScript(btcContext, getLiveFederations(), fb);
        RskUTXOProvider utxoProvider = new RskUTXOProvider(btcContext.getParams(), utxos);
        wallet.setUTXOProvider(utxoProvider);
        wallet.setCoinSelector(new RskAllowUnconfirmedCoinSelector());
        return wallet;
    }

    protected Keccak256 getFastBridgeDerivationHash(
        Keccak256 derivationArgumentsHash,
        Address userRefundAddress,
        Address lpBtcAddress,
        RskAddress lbcAddress
    ) {
        byte[] fastBridgeDerivationHashData = derivationArgumentsHash.getBytes();
        byte[] userRefundAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, userRefundAddress);
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, lpBtcAddress);
        byte[] lbcAddressBytes = lbcAddress.getBytes();
        byte[] result = new byte[
            fastBridgeDerivationHashData.length +
            userRefundAddressBytes.length +
            lpBtcAddressBytes.length +
            lbcAddressBytes.length
        ];

        int dstPosition = 0;

        System.arraycopy(
            fastBridgeDerivationHashData,
            0,
            result,
            dstPosition,
            fastBridgeDerivationHashData.length
        );
        dstPosition += fastBridgeDerivationHashData.length;

        System.arraycopy(
            userRefundAddressBytes,
            0,
            result,
            dstPosition,
            userRefundAddressBytes.length
        );
        dstPosition += userRefundAddressBytes.length;

        System.arraycopy(
            lbcAddressBytes,
            0,
            result,
            dstPosition,
            lbcAddressBytes.length
        );
        dstPosition += lbcAddressBytes.length;

        System.arraycopy(
            lpBtcAddressBytes,
            0,
            result,
            dstPosition,
            lpBtcAddressBytes.length
        );

        return new Keccak256(HashUtil.keccak256(result));
    }

    protected Coin getAmountSentToAddress(BtcTransaction btcTx, Address btcAddress) {
        Coin v = Coin.ZERO;
        for (TransactionOutput o : btcTx.getOutputs()) {
            if (o.getScriptPubKey().getToAddress(bridgeConstants.getBtcParams()).equals(btcAddress)) {
                v = v.add(o.getValue());
            }
        }
        return v;
    }

   // This method will be used by registerBtcTransfer to save all the data required on storage (utxos, btcTxHash-derivationHash),
   // and will look like.
    protected void saveFastBridgeDataInStorage(
            Sha256Hash btcTxHash,
            Keccak256 derivationHash,
            FastBridgeFederationInformation fastBridgeFederationInformation,
            List<UTXO> utxosList) throws IOException {
        provider.markFastBridgeFederationDerivationHashAsUsed(btcTxHash, derivationHash);
        provider.setFastBridgeFederationInformation(fastBridgeFederationInformation);
        for (UTXO utxo : utxosList) {
            getActiveFederationBtcUTXOs().add(utxo);
        }
    }

    private StoredBlock getBtcBlockchainChainHead() throws IOException, BlockStoreException {
        // Gather the current btc chain's head
        // IMPORTANT: we assume that getting the chain head from the btc blockstore
        // is enough since we're not manipulating the blockchain here, just querying it.
        this.ensureBtcBlockStore();
        return btcBlockStore.getChainHead();
    }

    /**
     * Returns the first bitcoin block we have. It is either a checkpoint or the genesis
     */
    private StoredBlock getLowestBlock() throws IOException {
        InputStream checkpoints = this.getCheckPoints();
        if (checkpoints == null) {
            BtcBlock genesis = bridgeConstants.getBtcParams().getGenesisBlock();
            return new StoredBlock(genesis, genesis.getWork(), 0);
        }
        CheckpointManager manager = new CheckpointManager(bridgeConstants.getBtcParams(), checkpoints);
        long time = getActiveFederation().getCreationTime().toEpochMilli();
        // Go back 1 week to match CheckpointManager.checkpoint() behaviour
        time -= 86400 * 7;
        return manager.getCheckpointBefore(time);
    }

    private Pair<BtcTransaction, List<UTXO>> createMigrationTransaction(Wallet originWallet, Address destinationAddress) {
        Coin expectedMigrationValue = originWallet.getBalance();
        logger.debug("[createMigrationTransaction] Balance to migrate: {}", expectedMigrationValue);
        for(;;) {
            BtcTransaction migrationBtcTx = new BtcTransaction(originWallet.getParams());
            migrationBtcTx.addOutput(expectedMigrationValue, destinationAddress);

            SendRequest sr = SendRequest.forTx(migrationBtcTx);
            sr.changeAddress = destinationAddress;
            sr.feePerKb = getFeePerKb();
            sr.missingSigsMode = Wallet.MissingSigsMode.USE_OP_ZERO;
            sr.recipientsPayFees = true;
            try {
                originWallet.completeTx(sr);
                for (TransactionInput transactionInput : migrationBtcTx.getInputs()) {
                    transactionInput.disconnect();
                }

                List<UTXO> selectedUTXOs = originWallet
                        .getUTXOProvider().getOpenTransactionOutputs(originWallet.getWatchedAddresses()).stream()
                        .filter(utxo ->
                                migrationBtcTx.getInputs().stream().anyMatch(input ->
                                        input.getOutpoint().getHash().equals(utxo.getHash()) &&
                                                input.getOutpoint().getIndex() == utxo.getIndex()
                                )
                        )
                        .collect(Collectors.toList());

                return Pair.of(migrationBtcTx, selectedUTXOs);
            } catch (InsufficientMoneyException | Wallet.ExceededMaxTransactionSize | Wallet.CouldNotAdjustDownwards e) {
                expectedMigrationValue = expectedMigrationValue.divide(2);
            } catch(Wallet.DustySendRequested e) {
                throw new IllegalStateException("Retiring federation wallet cannot be emptied", e);
            } catch (UTXOProviderException e) {
                throw new RuntimeException("Unexpected UTXO provider error", e);
            }
        }
    }

    // Make sure the local bitcoin blockchain is instantiated
    private void ensureBtcBlockChain() throws IOException, BlockStoreException {
        this.ensureBtcBlockStore();

        if (this.btcBlockChain == null) {
            this.btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
        }
    }

    // Make sure the local bitcoin blockstore is instantiated
    private void ensureBtcBlockStore() throws IOException, BlockStoreException {
        if(btcBlockStore == null) {
            btcBlockStore = btcBlockStoreFactory.newInstance(
                rskRepository,
                bridgeConstants,
                provider,
                activations
            );
            NetworkParameters btcParams = this.bridgeConstants.getBtcParams();

            if (this.btcBlockStore.getChainHead().getHeader().getHash().equals(btcParams.getGenesisBlock().getHash())) {
                // We are building the blockstore for the first time, so we have not set the checkpoints yet.
                long time = federationSupport.getActiveFederation().getCreationTime().toEpochMilli();
                InputStream checkpoints = this.getCheckPoints();
                if (time > 0 && checkpoints != null) {
                    CheckpointManager.checkpoint(btcParams, checkpoints, this.btcBlockStore, time);
                }
            }
        }
    }

    private Address getParsedAddress(String base58Address) throws AddressFormatException {
        return Address.fromBase58(btcContext.getParams(), base58Address);
    }

    private void generateRejectionRelease(
        BtcTransaction btcTx,
        Address btcRefundAddress,
        Address spendingAddress,
        Keccak256 rskTxHash,
        Coin totalAmount,
        WalletProvider walletProvider) throws IOException {

        ReleaseTransactionBuilder txBuilder = new ReleaseTransactionBuilder(
            btcContext.getParams(),
            walletProvider.provide(btcTx, spendingAddress),
            btcRefundAddress,
            getFeePerKb(),
            activations
        );

        Optional<ReleaseTransactionBuilder.BuildResult> buildReturnResult = txBuilder.buildEmptyWalletTo(btcRefundAddress);
        if (buildReturnResult.isPresent()) {
            if (activations.isActive(ConsensusRule.RSKIP146)) {
                provider.getReleaseTransactionSet().add(buildReturnResult.get().getBtcTx(), rskExecutionBlock.getNumber(), rskTxHash);
                eventLogger.logReleaseBtcRequested(rskTxHash.getBytes(), buildReturnResult.get().getBtcTx(), totalAmount);
            } else {
                provider.getReleaseTransactionSet().add(buildReturnResult.get().getBtcTx(), rskExecutionBlock.getNumber());
            }
            logger.info("Rejecting peg-in: return tx build successful to {}. Tx {}. Value {}.", btcRefundAddress, rskTxHash, totalAmount);
        } else {
            logger.warn("Rejecting peg-in: return tx build for btc tx {} error. Return was to {}. Tx {}. Value {}", btcTx.getHash(), btcRefundAddress, rskTxHash, totalAmount);
            panicProcessor.panic("peg-in-refund", String.format("peg-in money return tx build for btc tx %s error. Return was to %s. Tx %s. Value %s", btcTx.getHash(), btcRefundAddress, rskTxHash, totalAmount));
        }
    }

    private void generateRejectionRelease(
        BtcTransaction btcTx,
        Address senderBtcAddress,
        Transaction rskTx,
        Coin totalAmount
    ) throws IOException {
        WalletProvider createWallet = (BtcTransaction a, Address b) -> {
            // Build the list of UTXOs in the BTC transaction sent to either the active
            // or retiring federation
            List<UTXO> utxosToUs = btcTx.getWalletOutputs(
                getNoSpendWalletForLiveFederations(false)
            )
                .stream()
                .map(output ->
                    new UTXO(
                        btcTx.getHash(),
                        output.getIndex(),
                        output.getValue(),
                        0,
                        btcTx.isCoinBase(),
                        output.getScriptPubKey()
                    )
                ).collect(Collectors.toList());
            // Use the list of UTXOs to build a transaction builder
            // for the return btc transaction generation
            return getUTXOBasedWalletForLiveFederations(utxosToUs, false);
        };

        generateRejectionRelease(btcTx, senderBtcAddress, null, rskTx.getHash(), totalAmount, createWallet);
    }

    private boolean verifyLockSenderIsWhitelisted(Address senderBtcAddress, Coin totalAmount, int height) {
        // If the address is not whitelisted, then return the funds
        // using the exact same utxos sent to us.
        // That is, build a release transaction and get it in the release transaction set.
        // Otherwise, transfer SBTC to the sender of the BTC
        // The RSK account to update is the one that matches the pubkey "spent" on the first bitcoin tx input
        LockWhitelist lockWhitelist = provider.getLockWhitelist();
        if (!lockWhitelist.isWhitelistedFor(senderBtcAddress, totalAmount, height)) {
            logger.info("Rejecting lock. Address {} is not whitelisted.", senderBtcAddress);
            return false;
        }

        // Consume this whitelisted address
        lockWhitelist.consume(senderBtcAddress);

        return true;
    }

    private boolean verifyLockDoesNotSurpassLockingCap(BtcTransaction btcTx, Coin totalAmount) {
        if (!activations.isActive(ConsensusRule.RSKIP134)) {
            return true;
        }

        Coin fedCurrentFunds = getBtcLockedInFederation();
        Coin lockingCap = this.getLockingCap();
        logger.trace("Evaluating locking cap for: TxId {}. Value to lock {}. Current funds {}. Current locking cap {}", btcTx.getHash(true), totalAmount, fedCurrentFunds, lockingCap);
        Coin fedUTXOsAfterThisLock = fedCurrentFunds.add(totalAmount);
        // If the federation funds (including this new UTXO) are smaller than or equals to the current locking cap, we are fine.
        if (fedUTXOsAfterThisLock.compareTo(lockingCap) <= 0) {
            return true;
        }

        logger.info("locking cap exceeded! btc Tx {}", btcTx);
        return false;
    }

    private Coin getBtcLockedInFederation() {
        Coin maxRbtc = this.bridgeConstants.getMaxRbtc();
        Coin currentBridgeBalance = rskRepository.getBalance(PrecompiledContracts.BRIDGE_ADDR).toBitcoin();

        return maxRbtc.subtract(currentBridgeBalance);
    }

    @VisibleForTesting
    protected boolean isBlockMerkleRootValid(Sha256Hash merkleRoot, BtcBlock blockHeader) {
        boolean isValid = false;

        if (blockHeader.getMerkleRoot().equals(merkleRoot)) {
            logger.trace("block merkle root is valid");
            isValid = true;
        }
        else {
            if (activations.isActive(ConsensusRule.RSKIP143)) {
                CoinbaseInformation coinbaseInformation = provider.getCoinbaseInformation(blockHeader.getHash());
                if (coinbaseInformation == null) {
                    logger.trace("coinbase information for block {} is not yet registered", blockHeader.getHash());
                }
                isValid = coinbaseInformation != null && coinbaseInformation.getWitnessMerkleRoot().equals(merkleRoot);
                logger.trace("witness merkle root is {} valid", (isValid ? "":"NOT"));
            } else {
                logger.trace("RSKIP143 is not active, avoid checking witness merkle root");
            }
        }
        return isValid;
    }

    @VisibleForTesting
    protected boolean validationsForRegisterBtcTransaction(Sha256Hash btcTxHash, int height, byte[] pmtSerialized, byte[] btcTxSerialized)
            throws BlockStoreException, VerificationException.EmptyInputsOrOutputs, BridgeIllegalArgumentException {

        // Validates height and confirmations for tx
        try {
            int acceptableConfirmationsAmount = bridgeConstants.getBtc2RskMinimumAcceptableConfirmations();
            if (!BridgeUtils.validateHeightAndConfirmations(height, getBtcBlockchainBestChainHeight(), acceptableConfirmationsAmount, btcTxHash)) {
                return false;
            }
        } catch (Exception e) {
            String panicMessage = String.format("Btc Tx %s Supplied Height is %d but should be greater than 0", btcTxHash, height);
            logger.warn(panicMessage);
            panicProcessor.panic("btclock", panicMessage);
            return false;
        }

        // Validates pmt size
        if (!PartialMerkleTreeFormatUtils.hasExpectedSize(pmtSerialized)) {
            throw new BridgeIllegalArgumentException("PartialMerkleTree doesn't have expected size");
        }

        // Calculates merkleRoot
        Sha256Hash merkleRoot;
        try {
            NetworkParameters networkParameters = bridgeConstants.getBtcParams();
            merkleRoot = BridgeUtils.calculateMerkleRoot(networkParameters, pmtSerialized, btcTxHash);
            if (merkleRoot == null) {
                return false;
            }
        } catch (VerificationException e) {
            throw new BridgeIllegalArgumentException(e.getMessage(), e);
        }

        // Validates inputs count
        logger.info("Going to validate inputs for btc tx {}", btcTxHash);
        BridgeUtils.validateInputsCount(btcTxSerialized, activations.isActive(ConsensusRule.RSKIP143));

        // Check the the merkle root equals merkle root of btc block at specified height in the btc best chain
        // BTC blockstore is available since we've already queried the best chain height
        logger.trace("Getting btc block at height: {}", height);
        BtcBlock blockHeader = btcBlockStore.getStoredBlockAtMainChainHeight(height).getHeader();
        logger.trace("Validating block merkle root at height: {}", height);
        if (!isBlockMerkleRootValid(merkleRoot, blockHeader)){
            String panicMessage = String.format(
                    "Btc Tx %s Supplied merkle root %s does not match block's merkle root %s",
                    btcTxHash.toString(),
                    merkleRoot,
                    blockHeader.getMerkleRoot()
            );
            logger.warn(panicMessage);
            panicProcessor.panic("btclock", panicMessage);
            return false;
        }

        return true;
    }

    private Coin computeTotalAmountSent(BtcTransaction btcTx) throws IOException {
        // Compute the total amount sent. Value could have been sent both to the
        // currently active federation as well as to the currently retiring federation.
        // Add both amounts up in that case.
        Coin amountToActive = btcTx.getValueSentToMe(getActiveFederationWallet(false));
        Coin amountToRetiring = Coin.ZERO;
        Wallet retiringFederationWallet = getRetiringFederationWallet(false);
        if (retiringFederationWallet != null) {
            amountToRetiring = btcTx.getValueSentToMe(retiringFederationWallet);
        }
        return amountToActive.add(amountToRetiring);
    }

    private static byte[] serializeBlockHeader(StoredBlock block) {
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        byte[] bytes = block.getHeader().unsafeBitcoinSerialize();

        byte[] header = new byte[80];

        System.arraycopy(bytes, 0, header, 0, 80);

        return header;
    }
}

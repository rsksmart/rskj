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

import static co.rsk.peg.BridgeUtils.calculatePegoutTxSize;
import static co.rsk.peg.BridgeUtils.getRegularPegoutTxSize;
import static co.rsk.peg.PegUtils.*;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
import static co.rsk.peg.bitcoin.BitcoinUtils.*;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static co.rsk.peg.pegin.RejectedPeginReason.INVALID_AMOUNT;
import static java.util.Objects.isNull;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.peg.bitcoin.*;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.peg.flyover.FlyoverTxResponseCodes;
import co.rsk.peg.lockingcap.LockingCapIllegalArgumentException;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.pegin.*;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.*;
import co.rsk.peg.vote.*;
import co.rsk.peg.whitelist.*;
import co.rsk.rpc.modules.trace.CallType;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import co.rsk.util.HexUtils;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.SignatureException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.*;
import org.ethereum.vm.program.invoke.TransferInvoke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to move funds from btc to rsk and rsk to btc
 * @author Oscar Guindzberg
 */
public class BridgeSupport {
    public static final RskAddress BURN_ADDRESS = new RskAddress("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

    public static final int MAX_RELEASE_ITERATIONS = 30;

    public static final Integer BTC_TRANSACTION_CONFIRMATION_INEXISTENT_BLOCK_HASH_ERROR_CODE = -1;
    public static final Integer BTC_TRANSACTION_CONFIRMATION_BLOCK_NOT_IN_BEST_CHAIN_ERROR_CODE = -2;
    public static final Integer BTC_TRANSACTION_CONFIRMATION_INCONSISTENT_BLOCK_ERROR_CODE = -3;
    public static final Integer BTC_TRANSACTION_CONFIRMATION_BLOCK_TOO_OLD_ERROR_CODE = -4;
    public static final Integer BTC_TRANSACTION_CONFIRMATION_INVALID_MERKLE_BRANCH_ERROR_CODE = -5;

    public static final Integer RECEIVE_HEADER_CALLED_TOO_SOON = -1;
    public static final Integer RECEIVE_HEADER_BLOCK_TOO_OLD = -2;
    public static final Integer RECEIVE_HEADER_CANT_FOUND_PREVIOUS_BLOCK = -3;
    public static final Integer RECEIVE_HEADER_BLOCK_PREVIOUSLY_SAVED = -4;
    public static final Integer RECEIVE_HEADER_UNEXPECTED_EXCEPTION = -99;

    // Enough depth to be able to search backwards one month worth of blocks
    // (6 blocks/hour, 24 hours/day, 30 days/month)
    public static final Integer BTC_TRANSACTION_CONFIRMATION_MAX_DEPTH = 4320;

    private static final Logger logger = LoggerFactory.getLogger(BridgeSupport.class);
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final BridgeConstants bridgeConstants;
    private final NetworkParameters networkParameters;
    private final BridgeStorageProvider provider;
    private final Repository rskRepository;
    private final BridgeEventLogger eventLogger;
    private final List<ProgramSubtrace> subtraces = new ArrayList<>();
    private final BtcLockSenderProvider btcLockSenderProvider;
    private final PeginInstructionsProvider peginInstructionsProvider;

    private final FeePerKbSupport feePerKbSupport;
    private final WhitelistSupport whitelistSupport;
    private final FederationSupport federationSupport;
    private final LockingCapSupport lockingCapSupport;

    private final Context btcContext;
    private final BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BtcBlockStoreWithCache btcBlockStore;
    private BtcBlockChain btcBlockChain;
    private final org.ethereum.core.Block rskExecutionBlock;
    private final ActivationConfig.ForBlock activations;

    private final SignatureCache signatureCache;

    public BridgeSupport(
        BridgeConstants bridgeConstants,
        BridgeStorageProvider provider,
        BridgeEventLogger eventLogger,
        BtcLockSenderProvider btcLockSenderProvider,
        PeginInstructionsProvider peginInstructionsProvider,
        Repository repository,
        Block executionBlock,
        Context btcContext,
        FeePerKbSupport feePerKbSupport,
        WhitelistSupport whitelistSupport,
        FederationSupport federationSupport,
        LockingCapSupport lockingCapSupport,
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory,
        ActivationConfig.ForBlock activations,
        SignatureCache signatureCache) {
        this.rskRepository = repository;
        this.provider = provider;
        this.rskExecutionBlock = executionBlock;
        this.bridgeConstants = bridgeConstants;
        this.networkParameters = bridgeConstants.getBtcParams();
        this.eventLogger = eventLogger;
        this.btcLockSenderProvider = btcLockSenderProvider;
        this.peginInstructionsProvider = peginInstructionsProvider;
        this.btcContext = btcContext;
        this.feePerKbSupport = feePerKbSupport;
        this.whitelistSupport = whitelistSupport;
        this.federationSupport = federationSupport;
        this.lockingCapSupport = lockingCapSupport;
        this.btcBlockStoreFactory = btcBlockStoreFactory;
        this.activations = activations;
        this.signatureCache = signatureCache;
    }

    public List<ProgramSubtrace> getSubtraces() {
        return Collections.unmodifiableList(this.subtraces);
    }

    @VisibleForTesting
    InputStream getCheckPoints() {
        String resourceName = "/rskbitcoincheckpoints/" + networkParameters.getId() + ".checkpoints";
        InputStream checkpoints = BridgeSupport.class.getResourceAsStream(resourceName);
        logger.debug("[getCheckPoints] Looking for checkpoint {}. Found? {}", resourceName, checkpoints != null);
        if (checkpoints == null) {
            // If we don't have a custom checkpoints file, try to use bitcoinj's default checkpoints for that network
            checkpoints = BridgeSupport.class.getResourceAsStream("/" + networkParameters.getId() + ".checkpoints");
        }
        return checkpoints;
    }

    public void save() {
        provider.save();
        feePerKbSupport.save();
        whitelistSupport.save();
        federationSupport.save();
        lockingCapSupport.save();
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
        for (BtcBlock header : headers) {
            try {
                StoredBlock previousBlock = btcBlockStore.get(header.getPrevBlockHash());
                if (cannotProcessNextBlock(previousBlock)) {
                    logger.warn("[receiveHeaders] Header {} has too much work to be processed", header.getHash());
                    break;
                }
                btcBlockChain.add(header);
            } catch (Exception e) {
                // If we try to add an orphan header bitcoinj throws an exception
                // This catches that case and any other exception that may be thrown
                logger.warn("Exception adding btc header {}", header.getHash(), e);
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

        if (cannotProcessNextBlock(previousBlock)) {
            logger.warn("[receiveHeader] Header {} has too much work to be processed", header.getHash());
            return RECEIVE_HEADER_UNEXPECTED_EXCEPTION;
        }

        try {
            btcBlockChain.add(header);
        } catch (Exception e) {
            // If we try to add an orphan header bitcoinj throws an exception
            // This catches that case and any other exception that may be thrown
            logger.warn("Exception adding btc header {}", header.getHash(), e);
            return RECEIVE_HEADER_UNEXPECTED_EXCEPTION;
        }
        provider.setReceiveHeadersLastTimestamp(currentTimeStamp);
        return 0;
    }

    private boolean cannotProcessNextBlock(StoredBlock previousBlock) {
        int nextBlockHeight = previousBlock.getHeight() + 1;
        boolean networkIsMainnet = btcContext.getParams().equals(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));

        return nextBlockHeight >= bridgeConstants.getBlockWithTooMuchChainWorkHeight()
            && networkIsMainnet
            && !activations.isActive(ConsensusRule.RSKIP434);
    }

    /**
     * Get the wallet for the currently active federation
     * @return A BTC wallet for the currently active federation
     *
     * @param shouldConsiderFlyoverUTXOs Whether to consider flyover UTXOs
     */
    public Wallet getActiveFederationWallet(boolean shouldConsiderFlyoverUTXOs) {
        Federation federation = getActiveFederation();
        List<UTXO> utxos = federationSupport.getActiveFederationBtcUTXOs();

        return BridgeUtils.getFederationSpendWallet(
            btcContext,
            federation,
            utxos,
            shouldConsiderFlyoverUTXOs,
            provider
        );
    }

    /**
     * Get the wallet for the currently retiring federation
     * or null if there's currently no retiring federation
     * @return A BTC wallet for the currently active federation
     *
     * @param shouldConsiderFlyoverUTXOs Whether to consider flyover UTXOs
     */
    protected Wallet getRetiringFederationWallet(boolean shouldConsiderFlyoverUTXOs) {
        List<UTXO> retiringFederationBtcUTXOs = federationSupport.getRetiringFederationBtcUTXOs();
        return getRetiringFederationWallet(shouldConsiderFlyoverUTXOs, retiringFederationBtcUTXOs.size());
    }

    private Wallet getRetiringFederationWallet(boolean shouldConsiderFlyoverUTXOs, int utxosSizeLimit) {
        Federation federation = getRetiringFederation();
        if (federation == null) {
            logger.debug("[getRetiringFederationWallet] No retiring federation found");
            return null;
        }

        List<UTXO> utxos = federationSupport.getRetiringFederationBtcUTXOs();
        if (utxos.size() > utxosSizeLimit) {
            logger.debug("[getRetiringFederationWallet] Going to limit the amount of UTXOs to {}", utxosSizeLimit);
            utxos = utxos.subList(0, utxosSizeLimit);
        }

        logger.debug("[getRetiringFederationWallet] Fetching retiring federation spend wallet");
        return BridgeUtils.getFederationSpendWallet(
            btcContext,
            federation,
            utxos,
            shouldConsiderFlyoverUTXOs,
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
    public Wallet getUTXOBasedWalletForLiveFederations(List<UTXO> utxos, boolean isFlyoverCompatible) {
        return BridgeUtils.getFederationsSpendWallet(
            btcContext,
            federationSupport.getLiveFederations(),
            utxos,
            isFlyoverCompatible,
            provider
        );
    }

    /**
     * Get a no spend wallet for the currently live federations
     * @return A no spend BTC wallet for the currently live federation(s)
     *
     */
    public Wallet getNoSpendWalletForLiveFederations(boolean isFlyoverCompatible) {
        return BridgeUtils.getFederationsNoSpendWallet(
            btcContext,
            federationSupport.getLiveFederations(),
            isFlyoverCompatible,
            provider
        );
    }

    /**
     * In case of a peg-in tx: Transfers some RBTCs to the sender of the btc tx and keeps track of the new UTXOs available for spending.
     * In case of a peg-out tx: Keeps track of the change UTXOs, now available for spending.
     * @param rskTx The RSK transaction
     * @param btcTxSerialized The raw BTC tx
     * @param height The height of the BTC block that contains the tx
     * @param pmtSerialized The raw partial Merkle tree
     * @throws BlockStoreException If there's an error while executing validations
     * @throws IOException If there's an error while processing the tx
     */
    public void registerBtcTransaction(
        Transaction rskTx,
        byte[] btcTxSerialized,
        int height,
        byte[] pmtSerialized
    ) throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Context.propagate(btcContext);
        Keccak256 rskTxHash = rskTx.getHash();
        Sha256Hash btcTxHash = BtcTransactionFormatUtils.calculateBtcTxHash(btcTxSerialized);
        logger.debug("[registerBtcTransaction][rsk tx {}] Processing btc tx {}", rskTxHash, btcTxHash);

        try {
            // Check the tx was not already processed
            if (isAlreadyBtcTxHashProcessed(btcTxHash)) {
                throw new RegisterBtcTransactionException("Transaction already processed");
            }

            // Validations for register
            if (!validationsForRegisterBtcTransaction(btcTxHash, height, pmtSerialized, btcTxSerialized)) {
                throw new RegisterBtcTransactionException("Could not validate transaction");
            }

            BtcTransaction btcTx = new BtcTransaction(networkParameters, btcTxSerialized);
            btcTx.verify();
            logger.debug("[registerBtcTransaction][rsk tx {}] Btc tx hash without witness {}", rskTxHash, btcTx.getHash(false));

            // Check again that the tx was not already processed but making sure to use the txid (no witness)
            if (isAlreadyBtcTxHashProcessed(btcTx.getHash(false))) {
                throw new RegisterBtcTransactionException("Transaction already processed");
            }

            FederationContext federationContext = federationSupport.getFederationContext();
            PegTxType pegTxType = PegUtils.getTransactionType(
                activations,
                provider,
                bridgeConstants,
                federationContext,
                btcTx,
                height
            );

            logger.info("[registerBtcTransaction][btctx: {}] This is a {} transaction type", btcTx.getHash(), pegTxType);
            switch (pegTxType) {
                case PEGIN -> registerPegIn(btcTx, rskTxHash, height);
                case PEGOUT_OR_MIGRATION -> registerNewUtxos(btcTx);
                case SVP_FUND_TX -> registerSvpFundTx(btcTx);
                case SVP_SPEND_TX -> registerSvpSpendTx(btcTx);
            }
        } catch (RegisterBtcTransactionException e) {
            logger.warn(
                "[registerBtcTransaction][rsk tx {}] Could not register transaction {}. Message: {}",
                rskTxHash,
                btcTxHash,
                e.getMessage()
            );
        }
    }

    private void registerSvpFundTx(BtcTransaction btcTx) throws IOException {
        registerNewUtxos(btcTx); // Need to register the change UTXO

        // If the SVP validation period is over, SVP related values should be cleared in the next call to updateCollections
        // In that case, the fundTx will be identified as a regular peg-out tx and processed via #registerPegoutOrMigration
        // This covers the case when the fundTx is registered between the validation period end and the next call to updateCollections
        if (isSvpOngoing()) {
            updateSvpFundTransactionValues(btcTx);
        }
    }

    private void registerSvpSpendTx(BtcTransaction btcTx) throws IOException {
        registerNewUtxos(btcTx);
        provider.setSvpSpendTxHashUnsigned(null);

        logger.info("[registerSvpSpendTx] Going to commit the proposed federation.");
        federationSupport.commitProposedFederation();
    }

    private void updateSvpFundTransactionValues(BtcTransaction transaction) {
        logger.info(
            "[updateSvpFundTransactionValues] Transaction {} (wtxid:{}) is the svp fund transaction. Going to update its values",
            transaction.getHash(),
            transaction.getHash(true)
        );

        provider.setSvpFundTxSigned(transaction);
        provider.setSvpFundTxHashUnsigned(null);
    }

    @VisibleForTesting
    BtcBlockStoreWithCache getBtcBlockStore() {
        return btcBlockStore;
    }

    protected void registerPegIn(
        BtcTransaction btcTx,
        Keccak256 rskTxHash,
        int height
    ) throws IOException, RegisterBtcTransactionException {
        final String METHOD_NAME = "registerPegIn";

        if (!activations.isActive(ConsensusRule.RSKIP379)) {
            legacyRegisterPegin(btcTx, rskTxHash, height);
            logger.info(
                "[{}] BTC Tx {} processed in RSK transaction {} using legacy function",
                METHOD_NAME,
                btcTx.getHash(),
                rskTxHash
            );
            return;
        }

        Coin totalAmount = computeTotalAmountSent(btcTx);
        logger.debug("[{}] Total amount sent: {}", METHOD_NAME, totalAmount);

        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProvider,
            peginInstructionsProvider,
            activations
        );
        Coin minimumPeginTxValue = bridgeConstants.getMinimumPeginTxValue(activations);
        Wallet fedWallet = getNoSpendWalletForLiveFederations(false);
        PeginEvaluationResult peginEvaluationResult = PegUtils.evaluatePegin(
            btcTx,
            peginInformation,
            minimumPeginTxValue,
            fedWallet,
            activations
        );

        PeginProcessAction peginProcessAction = peginEvaluationResult.getPeginProcessAction();
        if (peginProcessAction == PeginProcessAction.CAN_BE_REGISTERED) {
            logger.debug("[{}] Peg-in is valid, going to register", METHOD_NAME);
            executePegIn(btcTx, peginInformation, totalAmount);
        } else {
            Optional<RejectedPeginReason> rejectedPeginReasonOptional = peginEvaluationResult.getRejectedPeginReason();
            if (rejectedPeginReasonOptional.isEmpty()) {
                // This flow should never be reached. There should always be a rejected pegin reason.
                String message = "Invalid state. No rejected reason was returned from evaluatePegin method";
                logger.error("[{}}] {}", METHOD_NAME, message);
                throw new IllegalStateException(message);
            }

            RejectedPeginReason rejectedPeginReason = rejectedPeginReasonOptional.get();
            logger.debug("[{}] Rejected peg-in, reason {}", METHOD_NAME, rejectedPeginReason);
            eventLogger.logRejectedPegin(btcTx, rejectedPeginReason);
            if (peginProcessAction == PeginProcessAction.CAN_BE_REFUNDED) {
                logger.debug("[{}] Refunding to address {} ", METHOD_NAME, peginInformation.getBtcRefundAddress());
                generateRejectionRelease(btcTx, peginInformation.getBtcRefundAddress(), rskTxHash, totalAmount);
                markTxAsProcessed(btcTx);
            } else {
                logger.debug("[{}] Unprocessable transaction {}.", METHOD_NAME, btcTx.getHash());
                handleUnprocessableBtcTx(btcTx, peginInformation.getProtocolVersion(), rejectedPeginReason);
            }
        }
    }

    private void handleUnprocessableBtcTx(
        BtcTransaction btcTx,
        int protocolVersion,
        RejectedPeginReason rejectedPeginReason
    ) {
        UnrefundablePeginReason unrefundablePeginReason;
        if (rejectedPeginReason == INVALID_AMOUNT) {
            unrefundablePeginReason = UnrefundablePeginReason.INVALID_AMOUNT;
        } else {
            unrefundablePeginReason = protocolVersion == 1 ?
                UnrefundablePeginReason.PEGIN_V1_REFUND_ADDRESS_NOT_SET :
                UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER;
        }

        logger.debug("[handleUnprocessableBtcTx] Unprocessable tx {}. Reason {}", btcTx.getHash(), unrefundablePeginReason);
        eventLogger.logUnrefundablePegin(btcTx, unrefundablePeginReason);
    }

    /**
     * Legacy version for processing peg-ins
     * Use instead {@link co.rsk.peg.BridgeSupport#registerPegIn}
     *
     * @param btcTx Peg-in transaction to process
     * @param rskTxHash Hash of the RSK transaction where the prg-in is being processed
     * @param height Peg-in transaction height in Bitcoin network
     * @deprecated
     */
    @Deprecated
    private void legacyRegisterPegin(
        BtcTransaction btcTx,
        Keccak256 rskTxHash,
        int height
    ) throws IOException, RegisterBtcTransactionException {
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
                refundTxSender(btcTx, rskTxHash, peginInformation, totalAmount);
                markTxAsProcessed(btcTx);
            }

            String message = String.format(
                "Error while trying to parse peg-in information for tx %s. %s",
                btcTx.getHash(),
                e.getMessage()
            );
            logger.warn("[legacyRegisterPegin] {}", message);
            throw new RegisterBtcTransactionException(message);
        }

        int protocolVersion = peginInformation.getProtocolVersion();
        logger.debug("[legacyRegisterPegin] Protocol version: {}", protocolVersion);
        switch (protocolVersion) {
            case 0:
                processPegInVersionLegacy(btcTx, rskTxHash, height, peginInformation, totalAmount);
                break;
            case 1:
                processPegInVersion1(btcTx, rskTxHash, peginInformation, totalAmount);
                break;
            default:
                markTxAsProcessed(btcTx);
                String message = String.format("Invalid peg-in protocol version: %d", protocolVersion);
                logger.warn("[legacyRegisterPegin] {}", message);
                throw new RegisterBtcTransactionException(message);
        }
    }

    private void processPegInVersionLegacy(
        BtcTransaction btcTx,
        Keccak256 rskTxHash,
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

            generateRejectionRelease(btcTx, senderBtcAddress, rskTxHash, totalAmount);
            markTxAsProcessed(btcTx);
        }
    }

    private void processPegInVersion1(
        BtcTransaction btcTx,
        Keccak256 rskTxHash,
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

            refundTxSender(btcTx, rskTxHash, peginInformation, totalAmount);
            markTxAsProcessed(btcTx);
        }
    }

    private void executePegIn(BtcTransaction btcTx, PeginInformation peginInformation, Coin amount) throws IOException {
        RskAddress rskDestinationAddress = peginInformation.getRskDestinationAddress();
        Address senderBtcAddress = peginInformation.getSenderBtcAddress();
        TxSenderAddressType senderBtcAddressType = peginInformation.getSenderBtcAddressType();
        int protocolVersion = peginInformation.getProtocolVersion();
        co.rsk.core.Coin amountInWeis = co.rsk.core.Coin.fromBitcoin(amount);

        logger.debug("[executePegIn] [btcTx:{}] Is a peg-in from a {} sender", btcTx.getHash(), senderBtcAddressType);
        this.transferTo(peginInformation.getRskDestinationAddress(), amountInWeis);
        logger.info(
            "[executePegIn] Transferring from BTC address {}. RSK address: {}. Amount: {}",
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
        registerNewUtxos(btcTx);
    }

    private void refundTxSender(
        BtcTransaction btcTx,
        Keccak256 rskTxHash,
        PeginInformation peginInformation,
        Coin amount) throws IOException {

        Address btcRefundAddress = peginInformation.getBtcRefundAddress();
        if (btcRefundAddress != null) {
            generateRejectionRelease(btcTx, btcRefundAddress, rskTxHash, amount);
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
        long rskHeight = rskExecutionBlock.getNumber();
        provider.setHeightBtcTxhashAlreadyProcessed(btcTx.getHash(false), rskHeight);
        logger.debug(
            "[markTxAsProcessed] Mark btc transaction {} (wtxid: {}) as processed at height {}",
            btcTx.getHash(),
            btcTx.getHash(true),
            rskHeight
        );
    }

    private boolean shouldProcessPegInVersionLegacy(
        TxSenderAddressType txSenderAddressType,
        BtcTransaction btcTx,
        Address senderBtcAddress,
        Coin totalAmount,
        int height) {

        return isTxLockableForLegacyVersion(txSenderAddressType, btcTx, senderBtcAddress) &&
            whitelistSupport.verifyLockSenderIsWhitelisted(senderBtcAddress, totalAmount, height) &&
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
    Add the btcTx outputs that send btc to the federation(s) to the UTXO list,
    so they can be used as inputs in future peg-out transactions.
    Finally, mark the btcTx as processed.
     */
    private void registerNewUtxos(BtcTransaction btcTx) throws IOException {
        // Outputs to the active federation
        Wallet activeFederationWallet = getActiveFederationWallet(false);
        List<TransactionOutput> outputsToTheActiveFederation = btcTx.getWalletOutputs(
            activeFederationWallet
        );
        for (TransactionOutput output : outputsToTheActiveFederation) {
            UTXO utxo = new UTXO(
                btcTx.getHash(),
                output.getIndex(),
                output.getValue(),
                0,
                btcTx.isCoinBase(),
                output.getScriptPubKey()
            );
            federationSupport.getActiveFederationBtcUTXOs().add(utxo);
        }
        logger.debug("[registerNewUtxos] Registered {} UTXOs sent to the active federation", outputsToTheActiveFederation.size());

        // Outputs to the retiring federation (if any)
        Wallet retiringFederationWallet = getRetiringFederationWallet(false);
        if (retiringFederationWallet != null) {
            List<TransactionOutput> outputsToTheRetiringFederation = btcTx.getWalletOutputs(retiringFederationWallet);
            for (TransactionOutput output : outputsToTheRetiringFederation) {
                UTXO utxo = new UTXO(
                    btcTx.getHash(),
                    output.getIndex(),
                    output.getValue(),
                    0,
                    btcTx.isCoinBase(),
                    output.getScriptPubKey()
                );
                federationSupport.getRetiringFederationBtcUTXOs().add(utxo);
            }
            logger.debug("[registerNewUtxos] Registered {} UTXOs sent to the retiring federation", outputsToTheRetiringFederation.size());
        }

        markTxAsProcessed(btcTx);
        logger.info("[registerNewUtxos] BTC Tx {} (wtxid: {}) processed in RSK", btcTx.getHash(), btcTx.getHash(true));
    }

    /**
     * Initiates the process of sending coins back to BTC.
     * This is the default contract method.
     * The funds will be sent to the bitcoin address controlled by the private key that signed the rsk tx.
     * The amount sent to the bridge in this tx will be the amount sent in the btc network minus fees.
     * @param rskTx The rsk tx being executed.
     * @throws IOException If there's an error while processing the release request.
     */
    public void releaseBtc(Transaction rskTx) throws IOException {
        final co.rsk.core.Coin pegoutValueInWeis = rskTx.getValue();
        final RskAddress senderAddress = rskTx.getSender(signatureCache);
        logger.debug(
            "[releaseBtc] Releasing {} weis from RSK address {} in tx {}",
            pegoutValueInWeis,
            senderAddress,
            rskTx.getHash()
        );

        // Peg-out from a smart contract not allowed since it's not possible to derive a BTC address from it
        if (BridgeUtils.isContractTx(rskTx)) {
            logger.trace(
                "[releaseBtc] Contract {} tried to release funds. Release is just allowed from EOA",
                senderAddress
            );
            if (activations.isActive(ConsensusRule.RSKIP185)) {
                emitRejectEvent(pegoutValueInWeis, senderAddress, RejectedPegoutReason.CALLER_CONTRACT);
                return;
            } else {
                String message = "Contract calling releaseBTC";
                logger.debug("[releaseBtc] {}", message);
                throw new Program.OutOfGasException(message);
            }
        }

        Context.propagate(btcContext);
        Address btcDestinationAddress = BridgeUtils.recoverBtcAddressFromEthTransaction(rskTx, networkParameters);
        logger.debug("[releaseBtc] BTC destination address: {}", btcDestinationAddress);

        requestRelease(btcDestinationAddress, pegoutValueInWeis, rskTx);
    }

    private void refundAndEmitRejectEvent(
        co.rsk.core.Coin releaseRequestedValueInWeis,
        RskAddress senderAddress,
        RejectedPegoutReason reason
    ) {
        logger.trace(
            "[refundAndEmitRejectEvent] Executing a refund of {} weis to {}. Reason: {}",
            releaseRequestedValueInWeis,
            senderAddress,
            reason
        );

        // Prior to RSKIP427, the value was converted to BTC before doing the refund
        // This could cause the original value to be rounded down to fit in satoshis value
        co.rsk.core.Coin refundValue = activations.isActive(RSKIP427) ?
            releaseRequestedValueInWeis :
            co.rsk.core.Coin.fromBitcoin(releaseRequestedValueInWeis.toBitcoin());

        rskRepository.transfer(
            PrecompiledContracts.BRIDGE_ADDR,
            senderAddress,
            refundValue
        );
        emitRejectEvent(releaseRequestedValueInWeis, senderAddress, reason);
    }

    private void emitRejectEvent(co.rsk.core.Coin releaseRequestedValueInWeis, RskAddress senderAddress, RejectedPegoutReason reason) {
        eventLogger.logReleaseBtcRequestRejected(senderAddress, releaseRequestedValueInWeis, reason);
    }

    /**
     * Creates a request for BTC release and
     * adds it to the request queue for it
     * to be processed later.
     *
     * @param destinationAddress the destination BTC address.
     * @param releaseRequestedValueInWeis the amount of RBTC requested to be released, represented in weis
     * @throws IOException if there is an error getting the release request queue from storage
     */
    private void requestRelease(Address destinationAddress, co.rsk.core.Coin releaseRequestedValueInWeis, Transaction rskTx) throws IOException {
        Coin valueToReleaseInSatoshis = releaseRequestedValueInWeis.toBitcoin();
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

            // The pegout releaseRequestedValueInWeis should be greater or equals than the max of these two values
            Coin minValue = Coin.valueOf(Math.max(bridgeConstants.getMinimumPegoutTxValue().value, requireFundsForFee.value));

            // Since Iris the peg-out the rule is that the minimum is inclusive
            if (valueToReleaseInSatoshis.isLessThan(minValue)) {
                optionalRejectedPegoutReason = Optional.of(
                    Objects.equals(minValue, requireFundsForFee) ?
                    RejectedPegoutReason.FEE_ABOVE_VALUE:
                    RejectedPegoutReason.LOW_AMOUNT
                );
            }
        } else {
            // For legacy peg-outs the rule stated that the minimum was exclusive
            if (!valueToReleaseInSatoshis.isGreaterThan(bridgeConstants.getLegacyMinimumPegoutTxValue())) {
                optionalRejectedPegoutReason = Optional.of(RejectedPegoutReason.LOW_AMOUNT);
            }
        }

        if (optionalRejectedPegoutReason.isPresent()) {
            logger.warn(
                "[requestRelease] releaseBtc ignored. To {}. Tx {}. Value {} weis. Reason: {}",
                destinationAddress,
                rskTx,
                releaseRequestedValueInWeis,
                optionalRejectedPegoutReason.get()
            );
            if (activations.isActive(ConsensusRule.RSKIP185)) {
                refundAndEmitRejectEvent(
                    releaseRequestedValueInWeis,
                    rskTx.getSender(signatureCache),
                    optionalRejectedPegoutReason.get()
                );
            }
        } else {
            if (activations.isActive(ConsensusRule.RSKIP146)) {
                provider.getReleaseRequestQueue().add(destinationAddress, valueToReleaseInSatoshis, rskTx.getHash());
            } else {
                provider.getReleaseRequestQueue().add(destinationAddress, valueToReleaseInSatoshis);
            }

            RskAddress sender = rskTx.getSender(signatureCache);
            if (activations.isActive(ConsensusRule.RSKIP185)) {
                eventLogger.logReleaseBtcRequestReceived(
                    sender,
                    destinationAddress,
                    releaseRequestedValueInWeis
                );
            }
            logger.info(
                "[requestRelease] releaseBtc successful to {}. Tx {}. Value {} weis.",
                destinationAddress,
                rskTx,
                releaseRequestedValueInWeis
            );
        }
    }

    /**
     * Executed every now and then.
     * Performs a few tasks: processing of any pending btc funds
     * migrations from retiring federations;
     * processing of any outstanding pegout requests; and
     * processing of any outstanding confirmed pegouts.
     * @throws IOException
     * @param rskTx current RSK transaction
     */
    public void updateCollections(Transaction rskTx) throws IOException {
        Context.propagate(btcContext);

        logUpdateCollections(rskTx);

        processFundsMigration(rskTx);

        processPegoutRequests(rskTx);

        processConfirmedPegouts(rskTx);

        updateFederationCreationBlockHeights();

        updateSvpState(rskTx);
    }

    private void logUpdateCollections(Transaction rskTx) {
        RskAddress sender = rskTx.getSender(signatureCache);
        eventLogger.logUpdateCollections(sender);
    }

    private void updateSvpState(Transaction rskTx) {
        Optional<Federation> proposedFederationOpt = federationSupport.getProposedFederation();
        if (proposedFederationOpt.isEmpty()) {
            return;
        }

        // if the proposed federation exists and the validation period ended,
        // we can conclude that the svp failed
        Federation proposedFederation = proposedFederationOpt.get();
        if (!validationPeriodIsOngoing(proposedFederation)) {
            processSvpFailure(proposedFederation);
            return;
        }

        Keccak256 rskTxHash = rskTx.getHash();

        if (shouldCreateAndProcessSvpFundTransaction()) {
            logger.info("[updateSvpState] No svp values were found, so fund tx creation will be processed.");
            processSvpFundTransactionUnsigned(rskTxHash, proposedFederation);
        }

        // if the fund tx signed is present, then the fund transaction change was registered,
        // meaning we can create the spend tx.
        Optional<BtcTransaction> svpFundTxSigned = provider.getSvpFundTxSigned();
        if (svpFundTxSigned.isPresent()) {
            logger.info(
                "[updateSvpState] Fund tx signed was found, so spend tx creation will be processed."
            );
            processSvpSpendTransactionUnsigned(rskTxHash, proposedFederation, svpFundTxSigned.get());
        }
    }

    private boolean shouldCreateAndProcessSvpFundTransaction() {
        // the fund tx will be created when the svp starts,
        // so we must ensure all svp values are clear to proceed with its creation
        Optional<Sha256Hash> svpFundTxHashUnsigned = provider.getSvpFundTxHashUnsigned();
        Optional<BtcTransaction> svpFundTxSigned = provider.getSvpFundTxSigned();
        Optional<Sha256Hash> svpSpendTxHashUnsigned = provider.getSvpSpendTxHashUnsigned(); // spendTxHash will be removed the last, after spendTxWFS, so is enough checking just this value

        return svpFundTxHashUnsigned.isEmpty()
            && svpFundTxSigned.isEmpty()
            && svpSpendTxHashUnsigned.isEmpty();
    }

    private void processSvpFailure(Federation proposedFederation) {
        logger.info(
            "[processSvpFailure] Proposed federation validation failed at block {}. SVP failure will be processed and Federation election will be allowed again.",
            rskExecutionBlock.getNumber()
        );
        eventLogger.logCommitFederationFailure(rskExecutionBlock, proposedFederation);
        allowFederationElectionAgain();
    }

    private void allowFederationElectionAgain() {
        federationSupport.clearProposedFederation();
        provider.clearSvpValues();
    }

    private boolean isSvpOngoing() {
        return federationSupport.getProposedFederation()
            .filter(this::validationPeriodIsOngoing)
            .isPresent();
    }

    private boolean validationPeriodIsOngoing(Federation proposedFederation) {
        long validationPeriodEndBlock = proposedFederation.getCreationBlockNumber() +
            bridgeConstants.getFederationConstants().getValidationPeriodDurationInBlocks();

        return rskExecutionBlock.getNumber() < validationPeriodEndBlock;
    }

    private void processSvpFundTransactionUnsigned(Keccak256 rskTxHash, Federation proposedFederation) {
        Coin spendableValueFromProposedFederation = bridgeConstants.getSpendableValueFromProposedFederation();
        try {
            BtcTransaction svpFundTransactionUnsigned = createSvpFundTransaction(proposedFederation, spendableValueFromProposedFederation);
            provider.setSvpFundTxHashUnsigned(svpFundTransactionUnsigned.getHash());
            PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();
            settleReleaseRequest(pegoutsWaitingForConfirmations, svpFundTransactionUnsigned, rskTxHash, spendableValueFromProposedFederation);
        } catch (InsufficientMoneyException e) {
            logger.error(
                "[processSvpFundTransactionUnsigned] Insufficient funds for creating the fund transaction. Error message: {}",
                e.getMessage()
            );
        } catch (IOException e) {
            logger.error(
                "[processSvpFundTransactionUnsigned] IOException getting the pegouts waiting for confirmations. Error message: {}",
                e.getMessage()
            );
        }
    }

    private BtcTransaction createSvpFundTransaction(Federation proposedFederation, Coin spendableValueFromProposedFederation) throws InsufficientMoneyException {
        Wallet activeFederationWallet = getActiveFederationWallet(true);

        BtcTransaction svpFundTransaction = new BtcTransaction(networkParameters);
        svpFundTransaction.setVersion(BTC_TX_VERSION_2);

        // add outputs to proposed fed and proposed fed with flyover prefix
        svpFundTransaction.addOutput(spendableValueFromProposedFederation, proposedFederation.getAddress());

        Address proposedFederationWithFlyoverPrefixAddress =
            getFlyoverAddress(networkParameters, bridgeConstants.getProposedFederationFlyoverPrefix(), proposedFederation.getRedeemScript());
        svpFundTransaction.addOutput(spendableValueFromProposedFederation, proposedFederationWithFlyoverPrefixAddress);

        // complete tx with input and change output
        SendRequest sendRequest = createSvpFundTransactionSendRequest(svpFundTransaction);
        activeFederationWallet.completeTx(sendRequest);

        return svpFundTransaction;
    }

    private SendRequest createSvpFundTransactionSendRequest(BtcTransaction transaction) {
        SendRequest sendRequest = SendRequest.forTx(transaction);
        sendRequest.changeAddress = getActiveFederationAddress();
        sendRequest.feePerKb = feePerKbSupport.getFeePerKb();
        sendRequest.missingSigsMode = Wallet.MissingSigsMode.USE_OP_ZERO;
        sendRequest.recipientsPayFees = false;

        return sendRequest;
    }

    private void processSvpSpendTransactionUnsigned(Keccak256 rskTxHash, Federation proposedFederation, BtcTransaction svpFundTxSigned) {
        BtcTransaction svpSpendTransactionUnsigned = createSvpSpendTransaction(svpFundTxSigned, proposedFederation);
        updateSvpSpendTransactionValues(rskTxHash, svpSpendTransactionUnsigned);

        Coin amountSentToActiveFed = svpSpendTransactionUnsigned.getOutput(0).getValue();
        logReleaseRequested(rskTxHash, svpSpendTransactionUnsigned, amountSentToActiveFed);
        logPegoutTransactionCreated(svpSpendTransactionUnsigned);
    }

    private BtcTransaction createSvpSpendTransaction(BtcTransaction svpFundTxSigned, Federation proposedFederation) {
        BtcTransaction svpSpendTransaction = new BtcTransaction(networkParameters);
        svpSpendTransaction.setVersion(BTC_TX_VERSION_2);

        addSvpSpendTransactionInputs(svpSpendTransaction, svpFundTxSigned, proposedFederation);

        svpSpendTransaction.addOutput(
            calculateSvpSpendTxAmount(proposedFederation),
            federationSupport.getActiveFederationAddress()
        );

        return svpSpendTransaction;
    }

    private void addSvpSpendTransactionInputs(BtcTransaction svpSpendTransaction, BtcTransaction svpFundTxSigned, Federation proposedFederation) {
        Script proposedFederationRedeemScript = proposedFederation.getRedeemScript();
        Script proposedFederationOutputScript = proposedFederation.getP2SHScript();
        addInputFromMatchingOutputScript(svpSpendTransaction, svpFundTxSigned, proposedFederationOutputScript);
        svpSpendTransaction.getInput(0)
            .setScriptSig(createBaseP2SHInputScriptThatSpendsFromRedeemScript(proposedFederationRedeemScript));

        Script flyoverRedeemScript =
            getFlyoverRedeemScript(bridgeConstants.getProposedFederationFlyoverPrefix(), proposedFederationRedeemScript);
        Script flyoverOutputScript = ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript);
        addInputFromMatchingOutputScript(svpSpendTransaction, svpFundTxSigned, flyoverOutputScript);
        svpSpendTransaction.getInput(1)
                .setScriptSig(createBaseP2SHInputScriptThatSpendsFromRedeemScript(flyoverRedeemScript));
    }

    private Coin calculateSvpSpendTxAmount(Federation proposedFederation) {
        int svpSpendTransactionSize = calculatePegoutTxSize(activations, proposedFederation, 2, 1);
        long svpSpendTransactionBackedUpSize = svpSpendTransactionSize * 12L / 10L; // just to be sure the amount sent will be enough

        return feePerKbSupport.getFeePerKb()
            .multiply(svpSpendTransactionBackedUpSize)
            .divide(1000);
    }

    private void updateSvpSpendTransactionValues(Keccak256 rskTxHash, BtcTransaction svpSpendTransactionUnsigned) {
        provider.setSvpSpendTxHashUnsigned(svpSpendTransactionUnsigned.getHash());
        provider.setSvpSpendTxWaitingForSignatures(
            new AbstractMap.SimpleEntry<>(rskTxHash, svpSpendTransactionUnsigned)
        );

        provider.setSvpFundTxSigned(null);
    }

    protected void updateFederationCreationBlockHeights() {
        federationSupport.updateFederationCreationBlockHeights();
    }

    private void processFundsMigration(Transaction rskTx) throws IOException {
        Wallet retiringFederationWallet = activations.isActive(RSKIP294) ?
            getRetiringFederationWallet(true, bridgeConstants.getMaxInputsPerPegoutTransaction()) :
            getRetiringFederationWallet(true);

        List<UTXO> availableUTXOs = federationSupport.getRetiringFederationBtcUTXOs();
        Federation activeFederation = getActiveFederation();

        if (federationIsInMigrationAge(activeFederation)) {
            long federationAge = rskExecutionBlock.getNumber() - activeFederation.getCreationBlockNumber();
            logger.trace("[processFundsMigration] Active federation (age={}) is in migration age.", federationAge);
            if (hasMinimumFundsToMigrate(retiringFederationWallet)){
                Coin retiringFederationBalance = retiringFederationWallet.getBalance();
                String retiringFederationBalanceInFriendlyFormat = retiringFederationBalance.toFriendlyString();
                logger.info(
                    "[processFundsMigration] Retiring federation has funds to migrate: {}.",
                    retiringFederationBalanceInFriendlyFormat
                );

                migrateFunds(
                    rskTx.getHash(),
                    retiringFederationWallet,
                    activeFederation.getAddress(),
                    availableUTXOs
                );
            }
        }

        if (retiringFederationWallet != null && federationIsPastMigrationAge(activeFederation)) {
            if (retiringFederationWallet.getBalance().isGreaterThan(Coin.ZERO)) {
                Coin retiringFederationBalance = retiringFederationWallet.getBalance();
                String retiringFederationBalanceInFriendlyFormat = retiringFederationBalance.toFriendlyString();
                logger.info(
                    "[processFundsMigration] Federation is past migration age and will try to migrate remaining balance: {}.",
                    retiringFederationBalanceInFriendlyFormat
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
                        "[processFundsMigration] Unable to complete retiring federation migration. Balance left: {} in {}",
                        retiringFederationWallet.getBalance().toFriendlyString(),
                        getRetiringFederationAddress()
                    );
                    panicProcessor.panic("updateCollection", "Unable to complete retiring federation migration.");
                }
            }

            logger.info(
                "[processFundsMigration] Retiring federation migration finished. Available UTXOs left: {}.",
                availableUTXOs.size()
            );
            federationSupport.clearRetiredFederation();
        }
    }

    private boolean federationIsInMigrationAge(Federation federation) {
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();

        long federationActivationAge = federationConstants.getFederationActivationAge(activations);
        long federationAge = rskExecutionBlock.getNumber() - federation.getCreationBlockNumber();
        long ageBegin = federationActivationAge + federationConstants.getFundsMigrationAgeSinceActivationBegin();
        long ageEnd = federationActivationAge + federationConstants.getFundsMigrationAgeSinceActivationEnd(activations);

        return federationAge > ageBegin && federationAge < ageEnd;
    }

    private boolean federationIsPastMigrationAge(Federation federation) {
        FederationConstants federationConstants = bridgeConstants.getFederationConstants();

        long federationAge = rskExecutionBlock.getNumber() - federation.getCreationBlockNumber();
        long ageEnd = federationConstants.getFederationActivationAge(activations) +
            federationConstants.getFundsMigrationAgeSinceActivationEnd(activations);

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

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();
        Pair<BtcTransaction, List<UTXO>> createResult = createMigrationTransaction(retiringFederationWallet, activeFederationAddress);
        BtcTransaction migrationTransaction = createResult.getLeft();
        List<UTXO> selectedUTXOs = createResult.getRight();

        logger.debug(
            "[migrateFunds] consumed {} UTXOs.",
            selectedUTXOs.size()
        );

        Coin amountMigrated = selectedUTXOs.stream()
            .map(UTXO::getValue)
            .reduce(Coin.ZERO, Coin::add);
        settleReleaseRequest(pegoutsWaitingForConfirmations, migrationTransaction, rskTxHash, amountMigrated);

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
     * @param rskTx
     */
    private void processPegoutRequests(Transaction rskTx) {
        final Wallet activeFederationWallet;
        final ReleaseRequestQueue pegoutRequests;
        final List<UTXO> availableUTXOs;
        final PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations;

        try {
            // (any of these could fail and would invalidate both the tx build and utxo selection, so treat as atomic)
            activeFederationWallet = getActiveFederationWallet(true);
            pegoutRequests = provider.getReleaseRequestQueue();
            availableUTXOs = federationSupport.getActiveFederationBtcUTXOs();
            pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();
        } catch (IOException e) {
            logger.error("Unexpected error accessing storage while attempting to process pegout requests", e);
            return;
        }

        // Pegouts are attempted using the currently active federation wallet.
        final ReleaseTransactionBuilder txBuilder = new ReleaseTransactionBuilder(
                btcContext.getParams(),
                activeFederationWallet,
                getActiveFederationAddress(),
                getFeePerKb(),
                activations
        );

        if (activations.isActive(RSKIP271)) {
            processPegoutsInBatch(pegoutRequests, txBuilder, availableUTXOs, pegoutsWaitingForConfirmations, activeFederationWallet, rskTx);
        } else {
            processPegoutsIndividually(pegoutRequests, txBuilder, availableUTXOs, pegoutsWaitingForConfirmations, activeFederationWallet);
        }
    }

    private void settleReleaseRequest(PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations, BtcTransaction pegoutTransaction, Keccak256 releaseCreationTxHash, Coin requestedAmount) {
        addPegoutToPegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations, pegoutTransaction, releaseCreationTxHash);
        savePegoutTxSigHash(pegoutTransaction);
        logReleaseRequested(releaseCreationTxHash, pegoutTransaction, requestedAmount);
        logPegoutTransactionCreated(pegoutTransaction);
    }

    private void addPegoutToPegoutsWaitingForConfirmations(PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations, BtcTransaction pegoutTransaction, Keccak256 releaseCreationTxHash) {
        long rskExecutionBlockNumber = rskExecutionBlock.getNumber();

        if (!activations.isActive(RSKIP146)) {
            pegoutsWaitingForConfirmations.add(pegoutTransaction, rskExecutionBlockNumber);
            return;
        }
        pegoutsWaitingForConfirmations.add(pegoutTransaction, rskExecutionBlockNumber, releaseCreationTxHash);
    }

    private void savePegoutTxSigHash(BtcTransaction pegoutTx) {
        if (!activations.isActive(ConsensusRule.RSKIP379)){
            return;
        }
        Optional<Sha256Hash> pegoutTxSigHash = BitcoinUtils.getFirstInputSigHash(pegoutTx);
        if (!pegoutTxSigHash.isPresent()){
            throw new IllegalStateException(String.format("SigHash could not be obtained from btc tx %s", pegoutTx.getHash()));
        }
        provider.setPegoutTxSigHash(pegoutTxSigHash.get());
    }

    private void logReleaseRequested(Keccak256 releaseCreationTxHash, BtcTransaction pegoutTransaction, Coin requestedAmount) {
        if (!activations.isActive(ConsensusRule.RSKIP146)) {
            return;
        }
        // For a short time period, there could be items in the pegout request queue
        // that were created but not processed before the consensus rule activation
        // These pegouts won't have the pegoutCreationRskTxHash,
        // so we shouldn't generate the event for them
        if (isNull(releaseCreationTxHash)) {
            return;
        }

        logger.debug(
            "[logReleaseRequested] release requested. rskTXHash: {}, btcTxHash: {}, amount: {}",
            releaseCreationTxHash, pegoutTransaction.getHash(), requestedAmount
        );

        byte[] rskTxHashSerialized = releaseCreationTxHash.getBytes();
        eventLogger.logReleaseBtcRequested(rskTxHashSerialized, pegoutTransaction, requestedAmount);
    }

    private void logPegoutTransactionCreated(BtcTransaction pegoutTransaction) {
        if (!activations.isActive(RSKIP428)) {
            return;
        }

        List<Coin> outpointValues = extractOutpointValues(pegoutTransaction);
        Sha256Hash pegoutTransactionHash = pegoutTransaction.getHash();
        eventLogger.logPegoutTransactionCreated(pegoutTransactionHash, outpointValues);
    }

    private void processPegoutsIndividually(
        ReleaseRequestQueue pegoutRequests,
        ReleaseTransactionBuilder txBuilder,
        List<UTXO> availableUTXOs,
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations,
        Wallet wallet
    ) {
        pegoutRequests.process(MAX_RELEASE_ITERATIONS, (ReleaseRequestQueue.Entry pegoutRequest) -> {
            ReleaseTransactionBuilder.BuildResult result = txBuilder.buildAmountTo(
                pegoutRequest.getDestination(),
                pegoutRequest.getAmount()
            );

            if (result.getResponseCode() != ReleaseTransactionBuilder.Response.SUCCESS) {
            // Couldn't build a pegout transaction to release these funds
            // Log the event and return false so that the request remains in the
            // queue for future processing.
            // Further logging is done at the tx builder level.
                logger.warn(
                    "Couldn't build a pegout transaction for <{}, {}>. Reason: {}",
                    pegoutRequest.getDestination().toBase58(),
                    pegoutRequest.getAmount(),
                    result.getResponseCode());
                return false;
            }

            BtcTransaction generatedTransaction = result.getBtcTx();
            Keccak256 pegoutCreationTxHash = pegoutRequest.getRskTxHash();
            settleReleaseRequest(pegoutsWaitingForConfirmations, generatedTransaction, pegoutCreationTxHash, pegoutRequest.getAmount());

            // Mark UTXOs as spent
            List<UTXO> selectedUTXOs = result.getSelectedUTXOs();
            availableUTXOs.removeAll(selectedUTXOs);

            adjustBalancesIfChangeOutputWasDust(generatedTransaction, pegoutRequest.getAmount(), wallet);

            return true;
        });
    }

    private void processPegoutsInBatch(
        ReleaseRequestQueue pegoutRequests,
        ReleaseTransactionBuilder txBuilder,
        List<UTXO> availableUTXOs,
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations,
        Wallet wallet,
        Transaction rskTx) {
        long currentBlockNumber = rskExecutionBlock.getNumber();
        long nextPegoutCreationBlockNumber = getNextPegoutCreationBlockNumber();

        if (currentBlockNumber < nextPegoutCreationBlockNumber) {
            return;
        }

        List<ReleaseRequestQueue.Entry> pegoutEntries = pegoutRequests.getEntries();
        Coin totalPegoutValue = pegoutEntries
            .stream()
            .map(ReleaseRequestQueue.Entry::getAmount)
            .reduce(Coin.ZERO, Coin::add);

        if (wallet.getBalance().isLessThan(totalPegoutValue)) {
            logger.warn("[processPegoutsInBatch] wallet balance {} is less than the totalPegoutValue {}", wallet.getBalance(), totalPegoutValue);
            return;
        }

        if (!pegoutEntries.isEmpty()) {
            logger.info("[processPegoutsInBatch] going to create a batched pegout transaction for {} requests, total amount {}", pegoutEntries.size(), totalPegoutValue);
            ReleaseTransactionBuilder.BuildResult result = txBuilder.buildBatchedPegouts(pegoutEntries);

            while (pegoutEntries.size() > 1 && result.getResponseCode() == ReleaseTransactionBuilder.Response.EXCEED_MAX_TRANSACTION_SIZE) {
                logger.info("[processPegoutsInBatch] Max size exceeded, going to divide {} requests in half", pegoutEntries.size());
                int firstHalfSize = pegoutEntries.size() / 2;
                pegoutEntries = pegoutEntries.subList(0, firstHalfSize);
                result = txBuilder.buildBatchedPegouts(pegoutEntries);
            }

            if (result.getResponseCode() != ReleaseTransactionBuilder.Response.SUCCESS) {
                logger.warn(
                    "Couldn't build a pegout BTC tx for {} pending requests (total amount: {}), Reason: {}",
                    pegoutRequests.getEntries().size(),
                    totalPegoutValue,
                    result.getResponseCode());
                return;
            }

            logger.info(
                "[processPegoutsInBatch] pegouts processed with btcTx hash {} and response code {}",
                result.getBtcTx().getHash(), result.getResponseCode());

            BtcTransaction batchPegoutTransaction = result.getBtcTx();
            Keccak256 batchPegoutCreationTxHash = rskTx.getHash();

            settleReleaseRequest(pegoutsWaitingForConfirmations, batchPegoutTransaction, batchPegoutCreationTxHash, totalPegoutValue);

            // Remove batched requests from the queue after successfully batching pegouts
            pegoutRequests.removeEntries(pegoutEntries);

            // Mark UTXOs as spent
            List<UTXO> selectedUTXOs = result.getSelectedUTXOs();
            logger.debug("[processPegoutsInBatch] used {} UTXOs for this pegout", selectedUTXOs.size());
            availableUTXOs.removeAll(selectedUTXOs);

            eventLogger.logBatchPegoutCreated(batchPegoutTransaction.getHash(),
                pegoutEntries.stream().map(ReleaseRequestQueue.Entry::getRskTxHash).collect(Collectors.toList()));

            adjustBalancesIfChangeOutputWasDust(batchPegoutTransaction, totalPegoutValue, wallet);
        }

        // set the next pegout creation block number when there are no pending pegout requests to be processed or they have been already processed
        if (pegoutRequests.getEntries().isEmpty()) {
            long nextPegoutHeight = currentBlockNumber + bridgeConstants.getNumberOfBlocksBetweenPegouts();
            provider.setNextPegoutHeight(nextPegoutHeight);
            logger.info("[processPegoutsInBatch] Next Pegout Height updated from {} to {}", currentBlockNumber, nextPegoutHeight);
        }
    }

    /**
     * Processes pegout waiting for confirmations.
     * It basically looks for pegout transactions with enough confirmations
     * and marks them as ready for signing as well as removes them
     * from the set.
     * @param rskTx the RSK transaction that is causing this processing.
     */
    private void processConfirmedPegouts(Transaction rskTx) {
        final Map<Keccak256, BtcTransaction> pegoutsWaitingForSignatures;
        final PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations;

        try {
            pegoutsWaitingForSignatures = provider.getPegoutsWaitingForSignatures();
            pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();
        } catch (IOException e) {
            logger.error("Unexpected error accessing storage while attempting to process confirmed pegouts", e);
            return;
        }

        // TODO: (Ariel Mendelzon - 07/12/2017)
        // TODO: at the moment, there can only be one btc transaction
        // TODO: per rsk transaction in the pegoutsWaitingForSignatures
        // TODO: map, and the rest of the processing logic is
        // TODO: dependant upon this. That is the reason we
        // TODO: add only one btc transaction at a time
        // TODO: (at least at this stage).
        Optional<PegoutsWaitingForConfirmations.Entry> nextPegoutWithEnoughConfirmations = pegoutsWaitingForConfirmations
            .getNextPegoutWithEnoughConfirmations(
                rskExecutionBlock.getNumber(),
                bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations()
            );

        if (!nextPegoutWithEnoughConfirmations.isPresent()) {
            return;
        }

        PegoutsWaitingForConfirmations.Entry confirmedPegout = nextPegoutWithEnoughConfirmations.get();

        Keccak256 txWaitingForSignatureKey = getPegoutWaitingForSignatureKey(rskTx, confirmedPegout);
        if (activations.isActive(ConsensusRule.RSKIP375)){
            /*
             This check aims to prevent confirmedPegout overriding. Currently, we do not accept more than one peg-out
             confirmation in the same update collections, but if in the future we do, then only one peg-out would be
             kept in the map, since the key used in the RSK tx hash that calls updateCollections would override the
             last one, thus resulting in losing funds. For this reason, we add this check that will alert anyone by
             throwing an exception during the development/QA phase when any code change introduces a bug allowing two
             entries to have the same key. So, informing on time the existence of this critical bug to pursue
             awareness and hint to rethink the changes being added.
             */
            checkIfEntryExistsInPegoutsWaitingForSignatures(txWaitingForSignatureKey, pegoutsWaitingForSignatures);
        }

        pegoutsWaitingForSignatures.put(txWaitingForSignatureKey, confirmedPegout.getBtcTransaction());
        pegoutsWaitingForConfirmations.removeEntry(confirmedPegout);

        if(activations.isActive(ConsensusRule.RSKIP326)) {
            eventLogger.logPegoutConfirmed(confirmedPegout.getBtcTransaction().getHash(), confirmedPegout.getPegoutCreationRskBlockNumber());
        }
    }

    private Keccak256 getPegoutWaitingForSignatureKey(Transaction rskTx, PegoutsWaitingForConfirmations.Entry confirmedPegout) {
        if (activations.isActive(ConsensusRule.RSKIP375)){
            return confirmedPegout.getPegoutCreationRskTxHash();
        }
        // Since RSKIP176 we are moving back to using the updateCollections related txHash as the set key
        if (activations.isActive(ConsensusRule.RSKIP146) && !activations.isActive(ConsensusRule.RSKIP176)) {
            // The pegout waiting for confirmations may have been created prior to the Consensus Rule activation
            // therefore it won't have a rskTxHash value, fallback to this transaction's hash
            return confirmedPegout.getPegoutCreationRskTxHash() == null ? rskTx.getHash() : confirmedPegout.getPegoutCreationRskTxHash();
        }
        return rskTx.getHash();
    }

    private void checkIfEntryExistsInPegoutsWaitingForSignatures(Keccak256 rskTxHash, Map<Keccak256, BtcTransaction> pegoutsWaitingForSignatures) {
        if (pegoutsWaitingForSignatures.containsKey(rskTxHash)) {
            String message = String.format(
                "An entry for the given rskTxHash %s already exists. Entry overriding is not allowed for pegoutsWaitingForSignatures map.",
                rskTxHash
            );
            logger.error("[checkIfEntryExistsInPegoutsWaitingForSignatures] {}", message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * If federation change output value had to be increased to be non-dust, the federation now has
     * more BTC than it should. So, we burn some sBTC to make balances match.
     *
     * @param btcTx      The btc tx that was just completed
     * @param sentByUser The number of sBTC originaly sent by the user
     */
    private void adjustBalancesIfChangeOutputWasDust(BtcTransaction btcTx, Coin sentByUser, Wallet wallet) {
        if (btcTx.getOutputs().size() <= 1) {
            // If there is no change, do-nothing
            return;
        }
        Coin sumInputs = Coin.ZERO;
        for (TransactionInput transactionInput : btcTx.getInputs()) {
            sumInputs = sumInputs.add(transactionInput.getValue());
        }

        Coin change = btcTx.getValueSentToMe(wallet);
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
     * @param federatorBtcPublicKey         Federator who is signing
     * @param signatures                    1 signature per btc tx input
     * @param releaseCreationRskTxHash      The hash of the release creation rsk tx
     */
    public void addSignature(BtcECKey federatorBtcPublicKey, List<byte[]> signatures, Keccak256 releaseCreationRskTxHash) throws IOException {
        if (signatures == null || signatures.isEmpty()) {
            return;
        }

        Context.propagate(btcContext);

        if (isSvpOngoing() && isSvpSpendTx(releaseCreationRskTxHash)) {
            logger.info("[addSignature] Going to sign svp spend transaction with federator public key {}", federatorBtcPublicKey);
            addSvpSpendTxSignatures(federatorBtcPublicKey, signatures);
            return;
        }

        logger.info("[addSignature] Going to sign release transaction with federator public key {}", federatorBtcPublicKey);
        addReleaseSignatures(federatorBtcPublicKey, signatures, releaseCreationRskTxHash);
    }

    private void addReleaseSignatures(
        BtcECKey federatorPublicKey,
        List<byte[]> signatures,
        Keccak256 releaseCreationRskTxHash
    ) throws IOException {

        BtcTransaction releaseTx = provider.getPegoutsWaitingForSignatures().get(releaseCreationRskTxHash);
        if (releaseTx == null) {
            logger.warn("[addReleaseSignatures] No tx waiting for signature for hash {}. Probably fully signed already.", releaseCreationRskTxHash);
            return;
        }
        if (!areSignaturesEnoughToSignAllTxInputs(releaseTx, signatures)) {
            return;
        }

        Optional<Federation> optionalFederation = getFederationFromPublicKey(federatorPublicKey);
        if (optionalFederation.isEmpty()) {
            logger.warn(
                "[addReleaseSignatures] Supplied federator btc public key {} does not belong to any of the federators.",
                federatorPublicKey
            );
            return;
        }

        Federation federation = optionalFederation.get();
        Optional<FederationMember> federationMember = federation.getMemberByBtcPublicKey(federatorPublicKey);
        if (federationMember.isEmpty()){
            logger.warn(
                "[addReleaseSignatures] Supplied federator btc public key {} doest not match any of the federator member btc public keys {}.",
                federatorPublicKey, federation.getBtcPublicKeys()
            );
            return;
        }
        FederationMember signingFederationMember = federationMember.get();

        byte[] releaseCreationRskTxHashSerialized = releaseCreationRskTxHash.getBytes();
        if (!activations.isActive(ConsensusRule.RSKIP326)) {
            eventLogger.logAddSignature(signingFederationMember, releaseTx, releaseCreationRskTxHashSerialized);
        }

        processSigning(signingFederationMember, signatures, releaseCreationRskTxHash, releaseTx);

        if (!BridgeUtils.hasEnoughSignatures(btcContext, releaseTx)) {
            logMissingSignatures(releaseTx, releaseCreationRskTxHash, federation);
            return;
        }

        logReleaseBtc(releaseTx, releaseCreationRskTxHashSerialized);
        provider.getPegoutsWaitingForSignatures().remove(releaseCreationRskTxHash);
    }

    private Optional<Federation> getFederationFromPublicKey(BtcECKey federatorPublicKey) {
        Federation retiringFederation = getRetiringFederation();
        Federation activeFederation = getActiveFederation();

        if (activeFederation.hasBtcPublicKey(federatorPublicKey)) {
            return Optional.of(activeFederation);
        }
        if (retiringFederation != null && retiringFederation.hasBtcPublicKey(federatorPublicKey)) {
            return Optional.of(retiringFederation);
        }

        return Optional.empty();
    }

    private boolean isSvpSpendTx(Keccak256 releaseCreationRskTxHash) {
        return provider.getSvpSpendTxWaitingForSignatures()
            .map(Map.Entry::getKey)
            .filter(key -> key.equals(releaseCreationRskTxHash))
            .isPresent();
    }

    private void addSvpSpendTxSignatures(
        BtcECKey proposedFederatorPublicKey,
        List<byte[]> signatures
    ) {
        Federation proposedFederation = federationSupport.getProposedFederation()
            // This flow should never be reached. There should always be a proposed federation if svpIsOngoing.
            .orElseThrow(() -> new IllegalStateException("Proposed federation must exist when trying to sign the svp spend transaction."));
        Map.Entry<Keccak256, BtcTransaction> svpSpendTxWFS = provider.getSvpSpendTxWaitingForSignatures()
            // The svpSpendTxWFS should always be present at this point, since we already checked isTheSvpSpendTx.
            .orElseThrow(() -> new IllegalStateException("Svp spend tx waiting for signatures must exist"));
        FederationMember federationMember = proposedFederation.getMemberByBtcPublicKey(proposedFederatorPublicKey)
            .orElseThrow(() -> new IllegalStateException("Federator must belong to proposed federation to sign the svp spend transaction."));

        Keccak256 svpSpendTxCreationRskTxHash = svpSpendTxWFS.getKey();
        BtcTransaction svpSpendTx = svpSpendTxWFS.getValue();

        if (!areSignaturesEnoughToSignAllTxInputs(svpSpendTx, signatures)) {
            return;
        }

        processSigning(federationMember, signatures, svpSpendTxCreationRskTxHash, svpSpendTx);
       
        // save current fed signature back in storage
        svpSpendTxWFS.setValue(svpSpendTx);
        provider.setSvpSpendTxWaitingForSignatures(svpSpendTxWFS);

        if (!BridgeUtils.hasEnoughSignatures(btcContext, svpSpendTx)) {
            logMissingSignatures(svpSpendTx, svpSpendTxCreationRskTxHash, proposedFederation);
            return;
        }

        logReleaseBtc(svpSpendTx, svpSpendTxCreationRskTxHash.getBytes());
        provider.setSvpSpendTxWaitingForSignatures(null);
    }

    private boolean areSignaturesEnoughToSignAllTxInputs(BtcTransaction releaseTx, List<byte[]> signatures) {
        int inputsSize = releaseTx.getInputs().size();
        int signaturesSize = signatures.size();

        if (inputsSize != signaturesSize) {
            logger.warn("[areSignaturesEnoughToSignAllTxInputs] Expected {} signatures but received {}.", inputsSize, signaturesSize);
            return false;
        }
        return true;
    }

    private void logMissingSignatures(BtcTransaction btcTx, Keccak256 releaseCreationRskTxHash, Federation federation) {
        int missingSignatures = BridgeUtils.countMissingSignatures(btcContext, btcTx);
        int neededSignatures = federation.getNumberOfSignaturesRequired();
        int signaturesCount = neededSignatures - missingSignatures;

        logger.debug("[logMissingSignatures] Tx {} not yet fully signed. Requires {}/{} signatures but has {}",
            releaseCreationRskTxHash, neededSignatures, federation.getSize(), signaturesCount);
    }

    private void logReleaseBtc(BtcTransaction btcTx, byte[] releaseCreationRskTxHashSerialized) {
        logger.info("[logReleaseBtc] Tx fully signed {}. Hex: {}", btcTx, Bytes.of(btcTx.bitcoinSerialize()));
        eventLogger.logReleaseBtc(btcTx, releaseCreationRskTxHashSerialized);
    }

    private void processSigning(
        FederationMember federatorMember,
        List<byte[]> signatures,
        Keccak256 releaseCreationRskTxHash,
        BtcTransaction btcTx) {

        // Build input hashes for signatures
        List<Sha256Hash> sigHashes = new ArrayList<>();
        for (int i = 0; i < btcTx.getInputs().size(); i++) {
            Sha256Hash sigHash = generateSigHashForP2SHTransactionInput(btcTx, i);
            sigHashes.add(sigHash);
        }

        // Verify given signatures are correct before proceeding
        BtcECKey federatorBtcPublicKey = federatorMember.getBtcPublicKey();
        List<TransactionSignature> txSigs;
        try {
            txSigs = getTransactionSignatures(federatorBtcPublicKey, sigHashes, signatures);
        } catch (SignatureException e) {
            logger.error("[processSigning] Unable to proceed with signing as the transaction signatures are incorrect. {} ", e.getMessage());
            return;
        }

        // All signatures are correct. Proceed to signing
        boolean signed = sign(federatorBtcPublicKey, txSigs, sigHashes, releaseCreationRskTxHash, btcTx);

        if (signed && activations.isActive(ConsensusRule.RSKIP326)) {
            eventLogger.logAddSignature(federatorMember, btcTx, releaseCreationRskTxHash.getBytes());
        }
    }

    private List<TransactionSignature> getTransactionSignatures(BtcECKey federatorBtcPublicKey, List<Sha256Hash> sigHashes, List<byte[]> signatures) throws SignatureException {
        List<BtcECKey.ECDSASignature> decodedSignatures = getDecodedSignatures(signatures);
        List<TransactionSignature> txSigs = new ArrayList<>();

        for (int i = 0; i < decodedSignatures.size(); i++) {
            BtcECKey.ECDSASignature decodedSignature = decodedSignatures.get(i);
            Sha256Hash sigHash = sigHashes.get(i);

            if (!federatorBtcPublicKey.verify(sigHash, decodedSignature)) {
                logger.warn(
                    "[getTransactionSignatures] Signature {} {} is not valid for hash {} and public key {}",
                    i,
                    Bytes.of(decodedSignature.encodeToDER()),
                    sigHash,
                    federatorBtcPublicKey
                );
                throw new SignatureException();
            }

            TransactionSignature txSig = new TransactionSignature(decodedSignature, BtcTransaction.SigHash.ALL, false);
            if (!txSig.isCanonical()) {
                logger.warn("[getTransactionSignatures] Signature {} {} is not canonical.", i, Bytes.of(decodedSignature.encodeToDER()));
                throw new SignatureException();
            }
            txSigs.add(txSig);
        }
        return txSigs;
    }

    private List<BtcECKey.ECDSASignature> getDecodedSignatures(List<byte[]> signatures) throws SignatureException {
        List<BtcECKey.ECDSASignature> decodedSignatures = new ArrayList<>();
        for (byte[] signature : signatures) {
            try {
                decodedSignatures.add(BtcECKey.ECDSASignature.decodeFromDER(signature));
            } catch (RuntimeException e) {
                int index = signatures.indexOf(signature);
                logger.warn("[getDecodedSignatures] Malformed signature for input {} : {}", index, Bytes.of(signature));
                throw new SignatureException();
            }
        }
        return decodedSignatures;
    }

    private boolean sign(
        BtcECKey federatorBtcPublicKey,
        List<TransactionSignature> txSigs,
        List<Sha256Hash> sigHashes,
        Keccak256 releaseCreationRskTxHash,
        BtcTransaction btcTx) {

        boolean signed = false;
        for (int i = 0; i < sigHashes.size(); i++) {
            Sha256Hash sigHash = sigHashes.get(i);
            TransactionInput input = btcTx.getInput(i);
            Script inputScript = input.getScriptSig();

            boolean alreadySignedByThisFederator =
                BridgeUtils.isInputSignedByThisFederator(federatorBtcPublicKey, sigHash, input);

            if (alreadySignedByThisFederator) {
                logger.warn("[sign] Input {} of tx {} already signed by this federator.", i, releaseCreationRskTxHash);
                break;
            }

            Optional<Script> redeemScriptOpt = extractRedeemScriptFromInput(input);
            if (redeemScriptOpt.isEmpty()) {
                break;
            }
            Script redeemScript = redeemScriptOpt.get();

            try {
                int sigIndex = inputScript.getSigInsertionIndex(sigHash, federatorBtcPublicKey);
                Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);

                Script inputScriptWithSignature = outputScript.getScriptSigWithSignature(inputScript, txSigs.get(i).encodeToBitcoin(), sigIndex);
                input.setScriptSig(inputScriptWithSignature);
                logger.debug("[sign] Tx input {} for tx {} signed.", i, releaseCreationRskTxHash);
                signed = true;
            } catch (IllegalStateException e) {
                Federation retiringFederation = getRetiringFederation();
                if (getActiveFederation().hasBtcPublicKey(federatorBtcPublicKey)) {
                    logger.debug("[sign] A member of the active federation is trying to sign a tx of the retiring one");
                } else if (retiringFederation != null && retiringFederation.hasBtcPublicKey(federatorBtcPublicKey)) {
                    logger.debug("[sign] A member of the retiring federation is trying to sign a tx of the active one");
                }
                return false;
            }
        }

        return signed;
    }

    /**
     * Returns the btc tx that federators need to sign or broadcast
     * @return a StateForFederator serialized in RLP
     */
    public byte[] getStateForBtcReleaseClient() throws IOException {
        StateForFederator stateForFederator = new StateForFederator(provider.getPegoutsWaitingForSignatures());
        return stateForFederator.encodeToRlp();
    }

   /**
     * Retrieves the current SVP spend transaction state for the SVP client.
     *
     * <p>
     * This method checks if there is an SVP spend transaction waiting for signatures, and if so, it serializes 
     * the state into RLP format. If no transaction is waiting, it returns an encoded empty RLP list.
     * </p>
     *
     * @return A byte array representing the RLP-encoded state of the SVP spend transaction. If no transaction 
     *         is waiting, returns a double RLP-encoded empty list.
     */
    public byte[] getStateForSvpClient() {
        return provider.getSvpSpendTxWaitingForSignatures()
            .map(StateForProposedFederator::new)
            .map(StateForProposedFederator::encodeToRlp)
            .orElse(RLP.encodeList(RLP.encodedEmptyList()));
    }

    /**
     * Returns the insternal state of the bridge
     * @return a BridgeState serialized in RLP
     */
    public byte[] getStateForDebugging() throws IOException, BlockStoreException {
        int btcBlockchainBestChainHeight = getBtcBlockchainBestChainHeight();
        long nextPegoutCreationBlockNumber = provider.getNextPegoutHeight().orElse(0L);
        List<UTXO> newFederationBtcUTXOs = federationSupport.getNewFederationBtcUTXOs();
        SortedMap<Keccak256, BtcTransaction> pegoutsWaitingForSignatures = provider.getPegoutsWaitingForSignatures();
        ReleaseRequestQueue releaseRequestQueue = provider.getReleaseRequestQueue();
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();

        BridgeState stateForDebugging = new BridgeState(
            btcBlockchainBestChainHeight,
            nextPegoutCreationBlockNumber,
            newFederationBtcUTXOs,
            pegoutsWaitingForSignatures,
            releaseRequestQueue,
            pegoutsWaitingForConfirmations,
            activations
        );

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
            final StoredBlock block = getBlockKeepingTestnetConsensus(btcBlockHash);

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
        StoredBlock block = getBlockKeepingTestnetConsensus(btcBlockHash);
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

    private StoredBlock getBlockKeepingTestnetConsensus(Sha256Hash btcBlockHash) throws BlockStoreException {
        long rskBlockNumber = 5_148_285;

        boolean networkIsTestnet = bridgeConstants.getBtcParams().equals(NetworkParameters.fromID(NetworkParameters.ID_TESTNET));
        Sha256Hash blockHash = Sha256Hash.wrap("00000000e8e7b540df01a7067e020fd7e2026bf86289def2283a35120c1af379");

        // DO NOT MODIFY.
        // This check is needed since this block caused a misbehaviour
        // for being stored in the cache but not in the storage
        if (rskExecutionBlock.getNumber() == rskBlockNumber
            && networkIsTestnet
            && btcBlockHash.equals(blockHash)
        ) {
            byte[] rawBtcBlockHeader = HexUtils.stringHexToByteArray("000000203b5d178405c4e6e7dc07d63d6de5db1342044791721654760c00000000000000796cf6743a036300b43fb3abe6703d04a7999751b6d5744f20327d1175320bd37b954e66ffff001d56dc11ce");

            BtcBlock btcBlockHeader = new BtcBlock(bridgeConstants.getBtcParams(), rawBtcBlockHeader);
            BigInteger btcBlockChainWork = new BigInteger("000000000000000000000000000000000000000000000ddeb5fbcd969312a77c", 16);
            int btcBlockNumber = 2_817_125;

            return new StoredBlock(btcBlockHeader, btcBlockChainWork, btcBlockNumber);
        }

        return btcBlockStore.get(btcBlockHash);
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
    protected boolean isAlreadyBtcTxHashProcessed(Sha256Hash btcTxHash) throws IOException {
        if (getBtcTxHashProcessedHeight(btcTxHash) > -1L) {
            logger.warn(
                "[isAlreadyBtcTxHashProcessed] Supplied Btc Tx {} was already processed",
                btcTxHash
            );
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


    @Nullable
    public Federation getRetiringFederation() {
        return federationSupport.getRetiringFederation();
    }

    public Address getActiveFederationAddress() {
        return federationSupport.getActiveFederationAddress();
    }

    public Integer getActiveFederationSize() {
        return federationSupport.getActiveFederationSize();
    }

    public Integer getActiveFederationThreshold() {
        return federationSupport.getActiveFederationThreshold();
    }

    public byte[] getActiveFederatorBtcPublicKey(int index) {
        return federationSupport.getActiveFederatorBtcPublicKey(index);
    }

    public byte[] getActiveFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        return federationSupport.getActiveFederatorPublicKeyOfType(index, keyType);
    }

    public Instant getActiveFederationCreationTime() {
        return federationSupport.getActiveFederationCreationTime();
    }

    public long getActiveFederationCreationBlockNumber() {
        return federationSupport.getActiveFederationCreationBlockNumber();
    }

    public Address getRetiringFederationAddress() {
        return federationSupport.getRetiringFederationAddress();
    }

    public Integer getRetiringFederationSize() {
        return federationSupport.getRetiringFederationSize();
    }

    public Integer getRetiringFederationThreshold() {
        return federationSupport.getRetiringFederationThreshold();
    }

    public byte[] getRetiringFederatorBtcPublicKey(int index) {
        return federationSupport.getRetiringFederatorBtcPublicKey(index);
    }

    public byte[] getRetiringFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        return federationSupport.getRetiringFederatorPublicKeyOfType(index, keyType);
    }

    public Instant getRetiringFederationCreationTime() {
        return federationSupport.getRetiringFederationCreationTime();
    }

    public long getRetiringFederationCreationBlockNumber() {
        return federationSupport.getRetiringFederationCreationBlockNumber();
    }

    public Integer voteFederationChange(Transaction tx, ABICallSpec callSpec) {
        return federationSupport.voteFederationChange(tx, callSpec, signatureCache, eventLogger);
    }

    public Keccak256 getPendingFederationHash() {
        return federationSupport.getPendingFederationHash();
    }

    public Integer getPendingFederationSize() {
        return federationSupport.getPendingFederationSize();
    }

    public byte[] getPendingFederatorBtcPublicKey(int index) {
        return federationSupport.getPendingFederatorBtcPublicKey(index);
    }

    public byte[] getPendingFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        return federationSupport.getPendingFederatorPublicKeyOfType(index, keyType);
    }

    public Optional<Address> getProposedFederationAddress() {
        return federationSupport.getProposedFederationAddress();
    }

    public Optional<Integer> getProposedFederationSize() {
        return federationSupport.getProposedFederationSize();
    }

    public Optional<Instant> getProposedFederationCreationTime() {
        return federationSupport.getProposedFederationCreationTime();
    }

    public Optional<Long> getProposedFederationCreationBlockNumber() {
        return federationSupport.getProposedFederationCreationBlockNumber();
    }

    public Optional<byte[]> getProposedFederatorPublicKeyOfType(int index, FederationMember.KeyType keyType) {
        return federationSupport.getProposedFederatorPublicKeyOfType(index, keyType);
    }

    public Integer getLockWhitelistSize() {
        return whitelistSupport.getLockWhitelistSize();
    }

    public LockWhitelistEntry getLockWhitelistEntryByIndex(int index) {
        return whitelistSupport.getLockWhitelistEntryByIndex(index);
    }

    public LockWhitelistEntry getLockWhitelistEntryByAddress(String addressBase58) {
        return whitelistSupport.getLockWhitelistEntryByAddress(addressBase58);
    }

    public Integer addOneOffLockWhitelistAddress(Transaction tx, String addressBase58, BigInteger maxTransferValue) {
       return whitelistSupport.addOneOffLockWhitelistAddress(tx, addressBase58, maxTransferValue);
    }

    public Integer addUnlimitedLockWhitelistAddress(Transaction tx, String addressBase58) {
        return whitelistSupport.addUnlimitedLockWhitelistAddress(tx, addressBase58);
    }

    public Integer removeLockWhitelistAddress(Transaction tx, String addressBase58) {
        return whitelistSupport.removeLockWhitelistAddress(tx, addressBase58);
    }

    public Coin getFeePerKb() {
        return feePerKbSupport.getFeePerKb();
    }

    public Integer voteFeePerKbChange(Transaction tx, Coin feePerKb) {
        return feePerKbSupport.voteFeePerKbChange(tx, feePerKb, signatureCache);
    }

    public Integer setLockWhitelistDisableBlockDelay(Transaction tx, BigInteger disableBlockDelayBI)
        throws IOException, BlockStoreException {
        int btcBlockchainBestChainHeight = getBtcBlockchainBestChainHeight();
        return whitelistSupport.setLockWhitelistDisableBlockDelay(tx, disableBlockDelayBI, btcBlockchainBestChainHeight);
    }

    public Coin getLockingCap() {
        return lockingCapSupport.getLockingCap().orElse(null);
    }

    public Optional<Script> getActiveFederationRedeemScript() {
        return federationSupport.getActiveFederationRedeemScript();
    }

    public boolean increaseLockingCap(Transaction tx, Coin newLockingCap) throws LockingCapIllegalArgumentException {
        return lockingCapSupport.increaseLockingCap(tx, newLockingCap);
    }

    public void registerBtcCoinbaseTransaction(
        byte[] btcTxSerialized,
        Sha256Hash blockHash,
        byte[] pmtSerialized,
        Sha256Hash witnessMerkleRoot,
        byte[] witnessReservedValue
    ) throws VMException {
        Context.propagate(btcContext);
        try{
            this.ensureBtcBlockStore();
        }catch (BlockStoreException | IOException e) {
            String message = String.format("Exception in registerBtcCoinbaseTransaction. %s", e.getMessage());
            logger.warn("[registerBtcCoinbaseTransaction] {}", message);
            throw new VMException(message, e);
        }

        Sha256Hash btcTxHash = BtcTransactionFormatUtils.calculateBtcTxHash(btcTxSerialized);
        logger.debug("[registerBtcCoinbaseTransaction] Going to register coinbase information for btcTx: {}", btcTxHash);

        if (witnessReservedValue.length != 32) {
            String message = String.format(
                "Witness reserved value length can't be different than 32 bytes. Value received: %s",
                Bytes.of(witnessReservedValue)
            );
            logger.warn("[registerBtcCoinbaseTransaction] {}", message);
            throw new BridgeIllegalArgumentException(message);
        }
        logger.trace("[registerBtcCoinbaseTransaction] Witness reserved value: {}", Bytes.of(witnessReservedValue));

        if (!PartialMerkleTreeFormatUtils.hasExpectedSize(pmtSerialized)) {
            String message = String.format(
                "PartialMerkleTree doesn't have expected size. Value received: %s",
                Bytes.of(pmtSerialized)
            );
            logger.warn("[registerBtcCoinbaseTransaction] {}", message);
            throw new BridgeIllegalArgumentException(message);
        }

        Sha256Hash merkleRoot;
        try {
            PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, pmtSerialized, 0);
            List<Sha256Hash> hashesInPmt = new ArrayList<>();
            merkleRoot = pmt.getTxnHashAndMerkleRoot(hashesInPmt);
            if (!hashesInPmt.contains(btcTxHash)) {
                logger.warn(
                    "[registerBtcCoinbaseTransaction] Supplied btc tx {} is not in the supplied partial merkle tree {}",
                    btcTxHash,
                    pmt
                );
                return;
            }
        } catch (VerificationException e) {
            String message = String.format("Partial merkle tree could not be parsed. %s", Bytes.of(pmtSerialized));
            logger.warn("[registerBtcCoinbaseTransaction] {}", message);
            throw new BridgeIllegalArgumentException(message, e);
        }
        logger.trace("[registerBtcCoinbaseTransaction] Merkle root: {}", merkleRoot);

        // Check merkle root equals btc block merkle root at the specified height in the btc best chain
        // Btc blockstore is available since we've already queried the best chain height
        StoredBlock storedBlock = null;
        try {
            storedBlock = btcBlockStore.get(blockHash);
        } catch (BlockStoreException e) {
            logger.error(
                "[registerBtcCoinbaseTransaction] Error gettin block {} from block store. {}",
                blockHash,
                e.getMessage()
            );
        }

        if (storedBlock == null) {
            String message = String.format("Block %s not yet registered", blockHash);
            logger.warn("[registerBtcCoinbaseTransaction] {}", message);
            throw new BridgeIllegalArgumentException(message);
        }
        logger.trace(
            "[registerBtcCoinbaseTransaction] Found block with hash {} at height {}",
            blockHash,
            storedBlock.getHeight()
        );

        BtcBlock blockHeader = storedBlock.getHeader();
        if (!blockHeader.getMerkleRoot().equals(merkleRoot)) {
            String panicMessage = String.format(
                "Btc Tx %s Supplied merkle root %s does not match block's merkle root %s",
                btcTxHash,
                merkleRoot,
                blockHeader.getMerkleRoot()
            );
            logger.warn("[registerBtcCoinbaseTransaction] {}", panicMessage);
            panicProcessor.panic("btclock", panicMessage);
            return;
        }

        BtcTransaction btcTx = new BtcTransaction(networkParameters, btcTxSerialized);
        btcTx.verify();

        validateWitnessInformation(btcTx, witnessMerkleRoot, witnessReservedValue);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(blockHeader.getHash(), coinbaseInformation);

        logger.debug("[registerBtcCoinbaseTransaction] Registered coinbase information for btc tx {}", btcTxHash);
    }

    private void validateWitnessInformation(
        BtcTransaction coinbaseTransaction,
        Sha256Hash witnessMerkleRoot,
        byte[] witnessReservedValue
    ) throws BridgeIllegalArgumentException {
        Optional<Sha256Hash> expectedWitnessCommitment = findWitnessCommitment(coinbaseTransaction);
        Sha256Hash calculatedWitnessCommitment = Sha256Hash.twiceOf(witnessMerkleRoot.getReversedBytes(), witnessReservedValue);

        if (expectedWitnessCommitment.isEmpty() || !expectedWitnessCommitment.get().equals(calculatedWitnessCommitment)) {
            String message = String.format(
                "[btcTx: %s] Witness commitment does not match. Expected: %s, Calculated: %s",
                coinbaseTransaction.getHash(),
                expectedWitnessCommitment.orElse(null),
                calculatedWitnessCommitment
            );
            logger.warn("[validateWitnessInformation] {}", message);
            throw new BridgeIllegalArgumentException(message);
        }
        logger.debug("[validateWitnessInformation] Witness commitment {} validated for btc tx {}", calculatedWitnessCommitment, coinbaseTransaction.getHash());
    }

    public boolean hasBtcBlockCoinbaseTransactionInformation(Sha256Hash blockHash) {
        CoinbaseInformation coinbaseInformation = provider.getCoinbaseInformation(blockHash);
        return coinbaseInformation != null;
    }

    public long getActiveFederationCreationBlockHeight() {
        return federationSupport.getActiveFederationCreationBlockHeight();
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

        if (!activations.isActive(ConsensusRule.RSKIP385) &&
                (!activations.isActive(ConsensusRule.RSKIP271) || pegoutRequestsCount == 0)) {
            return Coin.ZERO;
        }

        int totalOutputs = pegoutRequestsCount + 2; // N + 2 outputs

        int pegoutTxSize = BridgeUtils.calculatePegoutTxSize(activations, getActiveFederation(), INPUT_MULTIPLIER, totalOutputs);

        Coin feePerKB = getFeePerKb();

        return feePerKB
                .multiply(pegoutTxSize) // times the size in bytes
                .divide(1000);
    }

    public BigInteger registerFlyoverBtcTransaction(
        Transaction rskTx,
        byte[] btcTxSerialized,
        int height,
        byte[] pmtSerialized,
        Keccak256 derivationArgumentsHash,
        Address userRefundAddress,
        RskAddress lbcAddress,
        Address lpBtcAddress,
        boolean shouldTransferToContract
    ) throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        if (!BridgeUtils.isContractTx(rskTx)) {
            logger.debug("[registerFlyoverBtcTransaction] (rskTx:{}) Sender not a contract", rskTx.getHash());
            return BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_NOT_CONTRACT_ERROR.value());
        }

        RskAddress sender = rskTx.getSender(signatureCache);

        if (!sender.equals(lbcAddress)) {
            logger.debug(
                "[registerFlyoverBtcTransaction] Expected sender to be the same as lbcAddress. (sender: {}) (lbcAddress:{})",
                sender,
                lbcAddress
            );
            return BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_INVALID_SENDER_ERROR.value());
        }

        Context.propagate(btcContext);
        Sha256Hash btcTxHash = BtcTransactionFormatUtils.calculateBtcTxHash(btcTxSerialized);
        logger.debug("[registerFlyoverBtcTransaction] btc tx hash: {}", btcTxHash);

        Keccak256 flyoverDerivationHash = getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundAddress,
            lpBtcAddress,
            lbcAddress
        );
        logger.debug("[registerFlyoverBtcTransaction] flyover derivation hash: {}", flyoverDerivationHash);

        if (provider.isFlyoverDerivationHashUsed(btcTxHash, flyoverDerivationHash)) {
            logger.debug(
                "[registerFlyoverBtcTransaction] Transaction {} and derivation hash {} already used.",
                btcTxHash,
                flyoverDerivationHash
            );
            return BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value());
        }

        if (!validationsForRegisterBtcTransaction(btcTxHash, height, pmtSerialized, btcTxSerialized)) {
            logger.debug(
                "[registerFlyoverBtcTransaction] (btcTx:{}) error during validationsForRegisterBtcTransaction",
                btcTxHash
            );
            return BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALIDATIONS_ERROR.value());
        }

        BtcTransaction btcTx = new BtcTransaction(networkParameters, btcTxSerialized);
        btcTx.verify();

        Sha256Hash btcTxHashWithoutWitness = btcTx.getHash(false);
        logger.debug("[registerFlyoverBtcTransaction] btc tx hash without witness: {}", btcTxHashWithoutWitness);

        if (!btcTxHashWithoutWitness.equals(btcTxHash) &&
            provider.isFlyoverDerivationHashUsed(btcTxHashWithoutWitness, flyoverDerivationHash)
        ) {
            logger.debug(
                "[registerFlyoverBtcTransaction] Transaction {} and derivation hash {} already used. Checking hash without witness",
                btcTxHashWithoutWitness,
                flyoverDerivationHash
            );
            return BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value());
        }

        FlyoverFederationInformation flyoverActiveFederationInformation = createFlyoverFederationInformation(flyoverDerivationHash);
        Address flyoverActiveFederationAddress = flyoverActiveFederationInformation.getFlyoverFederationAddress(networkParameters);
        Federation retiringFederation = getRetiringFederation();
        Optional<FlyoverFederationInformation> flyoverRetiringFederationInformation = Optional.empty();

        List<Address> addresses = new ArrayList<>(2);
        addresses.add(flyoverActiveFederationAddress);

        if (activations.isActive(RSKIP293) && retiringFederation != null) {
            flyoverRetiringFederationInformation = Optional.of(createFlyoverFederationInformation(flyoverDerivationHash, retiringFederation));
            Address flyoverRetiringFederationAddress = flyoverRetiringFederationInformation.get().getFlyoverFederationAddress(
                networkParameters
            );
            addresses.add(flyoverRetiringFederationAddress);
            logger.debug("[registerFlyoverBtcTransaction] flyover retiring federation address: {}", flyoverRetiringFederationAddress);
        }

        FlyoverTxResponseCodes txResponse = BridgeUtils.validateFlyoverPeginValue(
            activations,
            bridgeConstants,
            btcContext,
            btcTx,
            addresses
        );
        logger.debug("[registerFlyoverBtcTransaction] validate flyover pegin value response: {}", txResponse.value());

        if (txResponse != FlyoverTxResponseCodes.VALID_TX){
            return BigInteger.valueOf(txResponse.value());
        }

        Coin totalAmount = BridgeUtils.getAmountSentToAddresses(
            activations,
            networkParameters,
            btcContext,
            btcTx,
            addresses
        );
        logger.debug("[registerFlyoverBtcTransaction] total amount sent to flyover federations: {}", totalAmount);

        if (!verifyLockDoesNotSurpassLockingCap(btcTx, totalAmount)) {
            InternalTransaction internalTx = (InternalTransaction) rskTx;
            logger.info("[registerFlyoverBtcTransaction] Locking cap surpassed, going to return funds!");
            List<FlyoverFederationInformation> fbFederations = flyoverRetiringFederationInformation.isPresent() ?
                Arrays.asList(flyoverActiveFederationInformation, flyoverRetiringFederationInformation.get()) :
                Collections.singletonList(flyoverActiveFederationInformation);
            WalletProvider walletProvider = createFlyoverWalletProvider(fbFederations);

            provider.markFlyoverDerivationHashAsUsed(btcTxHashWithoutWitness, flyoverDerivationHash);

            if (shouldTransferToContract) {
                logger.debug("[registerFlyoverBtcTransaction] Returning to liquidity provider");
                generateRejectionRelease(btcTx, lpBtcAddress, addresses, new Keccak256(internalTx.getOriginHash()), totalAmount, walletProvider);
                return BigInteger.valueOf(FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value());
            } else {
                logger.debug("[registerFlyoverBtcTransaction] Returning to user");
                generateRejectionRelease(btcTx, userRefundAddress, addresses, new Keccak256(internalTx.getOriginHash()), totalAmount, walletProvider);
                return BigInteger.valueOf(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value());
            }
        }

        transferTo(lbcAddress, co.rsk.core.Coin.fromBitcoin(totalAmount));

        List<UTXO> utxosForFlyoverActiveFed = BridgeUtils.getUTXOsSentToAddresses(
            activations,
            networkParameters,
            btcContext,
            btcTx,
            Collections.singletonList(flyoverActiveFederationAddress)
        );
        logger.info(
            "[registerFlyoverBtcTransaction]  going to register {} utxos for active flyover federation",
            utxosForFlyoverActiveFed.size()
        );

        saveFlyoverActiveFederationDataInStorage(
            btcTxHashWithoutWitness,
            flyoverDerivationHash,
            flyoverActiveFederationInformation,
            utxosForFlyoverActiveFed
        );

        if (activations.isActive(RSKIP293) && flyoverRetiringFederationInformation.isPresent()) {
            List<UTXO> utxosForRetiringFed = BridgeUtils.getUTXOsSentToAddresses(
                activations,
                networkParameters,
                btcContext,
                btcTx,
                Collections.singletonList(
                    flyoverRetiringFederationInformation.get().getFlyoverFederationAddress(networkParameters)
                )
            );

            if (!utxosForRetiringFed.isEmpty()){
                logger.info(
                    "[registerFlyoverBtcTransaction]  going to register {} utxos for retiring flyover federation",
                    utxosForRetiringFed.size()
                );
                saveFlyoverRetiringFederationDataInStorage(
                    btcTxHashWithoutWitness,
                    flyoverDerivationHash,
                    flyoverRetiringFederationInformation.get(),
                    utxosForRetiringFed
                );
            }
        }

        logger.info(
            "[registerFlyoverBtcTransaction] (btcTx:{}) transaction registered successfully",
            btcTxHashWithoutWitness
        );

        return co.rsk.core.Coin.fromBitcoin(totalAmount).asBigInteger();
    }

    protected FlyoverFederationInformation createFlyoverFederationInformation(Keccak256 flyoverDerivationHash) {
        return createFlyoverFederationInformation(flyoverDerivationHash, getActiveFederation());
    }

    protected FlyoverFederationInformation createFlyoverFederationInformation(Keccak256 flyoverDerivationHash, Federation federation) {
        Script federationRedeemScript = federation.getRedeemScript();
        Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            flyoverDerivationHash,
            federationRedeemScript
        );

        Script flyoverP2shOutputScript = ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript);

        return new FlyoverFederationInformation(
            flyoverDerivationHash,
            federation.getP2SHScript().getPubKeyHash(),
            flyoverP2shOutputScript.getPubKeyHash()
        );
    }

    private WalletProvider createFlyoverWalletProvider(
        List<FlyoverFederationInformation> fbFederations) {
        return (BtcTransaction btcTx, List<Address> addresses) -> {
            List<UTXO> utxosList = BridgeUtils.getUTXOsSentToAddresses(
                activations,
                networkParameters,
                btcContext,
                btcTx,
                addresses
            );
            return getFlyoverWallet(btcContext, utxosList, fbFederations);
        };
    }

    protected Wallet getFlyoverWallet(Context btcContext, List<UTXO> utxos, List<FlyoverFederationInformation> fbFederations) {
        Wallet wallet = new FlyoverCompatibleBtcWalletWithMultipleScripts(
            btcContext,
            federationSupport.getLiveFederations(),
            fbFederations
        );
        RskUTXOProvider utxoProvider = new RskUTXOProvider(btcContext.getParams(), utxos);
        wallet.setUTXOProvider(utxoProvider);
        wallet.setCoinSelector(new RskAllowUnconfirmedCoinSelector());

        return wallet;
    }

    protected Keccak256 getFlyoverDerivationHash(
        Keccak256 derivationArgumentsHash,
        Address userRefundAddress,
        Address lpBtcAddress,
        RskAddress lbcAddress
    ) {
        byte[] flyoverDerivationHashData = derivationArgumentsHash.getBytes();
        byte[] userRefundAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, userRefundAddress);
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, lpBtcAddress);
        byte[] lbcAddressBytes = lbcAddress.getBytes();
        byte[] result = new byte[
            flyoverDerivationHashData.length +
            userRefundAddressBytes.length +
            lpBtcAddressBytes.length +
            lbcAddressBytes.length
        ];

        int dstPosition = 0;

        System.arraycopy(
            flyoverDerivationHashData,
            0,
            result,
            dstPosition,
            flyoverDerivationHashData.length
        );
        dstPosition += flyoverDerivationHashData.length;

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

   // This method will be used by registerBtcTransfer to save all the data required on storage (utxos, btcTxHash-derivationHash),
   // and will look like.
    protected void saveFlyoverActiveFederationDataInStorage(
            Sha256Hash btcTxHash,
            Keccak256 derivationHash,
            FlyoverFederationInformation flyoverFederationInformation,
            List<UTXO> utxosList
    ) {
        provider.markFlyoverDerivationHashAsUsed(btcTxHash, derivationHash);
        provider.setFlyoverFederationInformation(flyoverFederationInformation);
        federationSupport.getActiveFederationBtcUTXOs().addAll(utxosList);
    }

    protected void saveFlyoverRetiringFederationDataInStorage(
        Sha256Hash btcTxHash,
        Keccak256 derivationHash,
        FlyoverFederationInformation flyoverRetiringFederationInformation,
        List<UTXO> utxosList
    ) {
        provider.markFlyoverDerivationHashAsUsed(btcTxHash, derivationHash);
        provider.setFlyoverRetiringFederationInformation(flyoverRetiringFederationInformation);
        federationSupport.getRetiringFederationBtcUTXOs().addAll(utxosList);
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
            BtcBlock genesis = networkParameters.getGenesisBlock();
            return new StoredBlock(genesis, genesis.getWork(), 0);
        }
        CheckpointManager manager = new CheckpointManager(networkParameters, checkpoints);
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
            if (activations.isActive(ConsensusRule.RSKIP376)){
                migrationBtcTx.setVersion(BTC_TX_VERSION_2);
            }

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
                    .getUTXOProvider()
                    .getOpenTransactionOutputs(originWallet.getWatchedAddresses())
                    .stream()
                    .filter(utxo -> migrationBtcTx.getInputs().stream().anyMatch(input ->
                        input.getOutpoint().getHash().equals(utxo.getHash()) && input.getOutpoint().getIndex() == utxo.getIndex()
                    )).collect(Collectors.toList());

                return Pair.of(migrationBtcTx, selectedUTXOs);
            } catch (InsufficientMoneyException | Wallet.ExceededMaxTransactionSize | Wallet.CouldNotAdjustDownwards e) {
                logger.debug(
                    "[createMigrationTransaction] Error while creating migration transaction. Exception type {}. Message {}",
                    e.getClass(),
                    e.getMessage()
                );
                expectedMigrationValue = expectedMigrationValue.divide(2);
            } catch(Wallet.DustySendRequested e) {
                throw new IllegalStateException("[createMigrationTransaction] Retiring federation wallet cannot be emptied", e);
            } catch (UTXOProviderException e) {
                throw new RuntimeException("[createMigrationTransaction] Unexpected UTXO provider error", e);
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
            if (this.btcBlockStore.getChainHead().getHeader().getHash().equals(networkParameters.getGenesisBlock().getHash())) {
                // We are building the blockstore for the first time, so we have not set the checkpoints yet.
                long time = federationSupport.getActiveFederation().getCreationTime().toEpochMilli();
                InputStream checkpoints = this.getCheckPoints();
                if (time > 0 && checkpoints != null) {
                    CheckpointManager.checkpoint(networkParameters, checkpoints, this.btcBlockStore, time);
                }
            }
        }
    }

    private void generateRejectionRelease(
        BtcTransaction btcTx,
        Address btcRefundAddress,
        List<Address> spendingAddresses,
        Keccak256 rskTxHash,
        Coin totalAmount,
        WalletProvider walletProvider) throws IOException {

        ReleaseTransactionBuilder txBuilder = new ReleaseTransactionBuilder(
            btcContext.getParams(),
            walletProvider.provide(btcTx, spendingAddresses),
            btcRefundAddress,
            getFeePerKb(),
            activations
        );

        ReleaseTransactionBuilder.BuildResult buildReturnResult = txBuilder.buildEmptyWalletTo(btcRefundAddress);
        if (buildReturnResult.getResponseCode() != ReleaseTransactionBuilder.Response.SUCCESS) {
            logger.warn(
                "[generateRejectionRelease] Rejecting peg-in tx could not be built due to {}: Btc peg-in txHash {}. Refund to address: {}. RskTxHash: {}. Value: {}",
                buildReturnResult.getResponseCode(),
                btcTx.getHash(),
                btcRefundAddress,
                rskTxHash,
                totalAmount
            );
            panicProcessor.panic("peg-in-refund", String.format("peg-in money return tx build for btc tx %s error. Return was to %s. Tx %s. Value %s. Reason %s", btcTx.getHash(), btcRefundAddress, rskTxHash, totalAmount, buildReturnResult.getResponseCode()));
            return;
        }

        BtcTransaction refundPegoutTransaction = buildReturnResult.getBtcTx();

        logger.info(
            "[generateRejectionRelease] Rejecting peg-in tx built successfully: Refund to address: {}. RskTxHash: {}. Value {}.",
            btcRefundAddress,
            rskTxHash,
            totalAmount
        );

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = provider.getPegoutsWaitingForConfirmations();
        settleReleaseRejection(pegoutsWaitingForConfirmations, refundPegoutTransaction, rskTxHash, totalAmount);
    }

    private void settleReleaseRejection(PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations, BtcTransaction pegoutTransaction, Keccak256 releaseCreationTxHash, Coin requestedAmount) {
        addPegoutToPegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations, pegoutTransaction, releaseCreationTxHash);
        logReleaseRequested(releaseCreationTxHash, pegoutTransaction, requestedAmount);
        logPegoutTransactionCreated(pegoutTransaction);
    }

    private void generateRejectionRelease(
        BtcTransaction btcTx,
        Address senderBtcAddress,
        Keccak256 rskTxHash,
        Coin totalAmount
    ) throws IOException {
        WalletProvider createWallet = (BtcTransaction btcTransaction, List<Address> addresses) -> {
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

        generateRejectionRelease(btcTx, senderBtcAddress, null, rskTxHash, totalAmount, createWallet);
    }

    private boolean verifyLockDoesNotSurpassLockingCap(BtcTransaction btcTx, Coin totalAmount) {
        Optional<Coin> lockingCap = lockingCapSupport.getLockingCap();
        if (!lockingCap.isPresent()) {
            return true;
        }

        Coin fedCurrentFunds = getBtcLockedInFederation();
        logger.trace("Evaluating locking cap for: TxId {}. Value to lock {}. Current funds {}. Current locking cap {}", btcTx.getHash(true), totalAmount, fedCurrentFunds, lockingCap);
        Coin fedUTXOsAfterThisLock = fedCurrentFunds.add(totalAmount);
        // If the federation funds (including this new UTXO) are smaller than or equals to the current locking cap, we are fine.
        if (fedUTXOsAfterThisLock.compareTo(lockingCap.get()) <= 0) {
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
            if (!BridgeUtils.validateHeightAndConfirmations(
                height,
                getBtcBlockchainBestChainHeight(),
                acceptableConfirmationsAmount,
                btcTxHash)) {
                return false;
            }
        } catch (Exception e) {
            String panicMessage = String.format("[validationsForRegisterBtcTransaction] Btc Tx %s Supplied Height is %d but should be greater than 0", btcTxHash, height);
            logger.warn(panicMessage);
            panicProcessor.panic("btclock", panicMessage);
            return false;
        }

        // Validates pmt size
        if (!PartialMerkleTreeFormatUtils.hasExpectedSize(pmtSerialized)) {
            String message = "PartialMerkleTree doesn't have expected size";
            logger.warn(message);
            throw new BridgeIllegalArgumentException(message);
        }

        // Calculates merkleRoot
        Sha256Hash merkleRoot;
        try {
            merkleRoot = BridgeUtils.calculateMerkleRoot(networkParameters, pmtSerialized, btcTxHash);
            if (merkleRoot == null) {
                return false;
            }
        } catch (VerificationException e) {
            throw new BridgeIllegalArgumentException(e.getMessage(), e);
        }

        // Validates inputs count
        logger.info("[validationsForRegisterBtcTransaction] Going to validate inputs for btc tx {}", btcTxHash);
        BridgeUtils.validateInputsCount(btcTxSerialized, activations.isActive(ConsensusRule.RSKIP143));

        // Check the merkle root equals merkle root of btc block at specified height in the btc best chain
        // BTC blockstore is available since we've already queried the best chain height
        logger.trace("[validationsForRegisterBtcTransaction] Getting btc block at height: {}", height);
        BtcBlock blockHeader = btcBlockStore.getStoredBlockAtMainChainHeight(height).getHeader();
        logger.trace("[validationsForRegisterBtcTransaction] Validating block merkle root at height: {}", height);
        if (!isBlockMerkleRootValid(merkleRoot, blockHeader)){
            String panicMessage = String.format(
                "[validationsForRegisterBtcTransaction] Btc Tx %s Supplied merkle root %s does not match block's merkle root %s",
                btcTxHash.toString(),
                merkleRoot,
                blockHeader.getMerkleRoot()
            );
            logger.warn(panicMessage);
            panicProcessor.panic("btclock", panicMessage);
            return false;
        }

        logger.trace("[validationsForRegisterBtcTransaction] Btc tx: {} successfully validated", btcTxHash);
        return true;
    }

    private Coin computeTotalAmountSent(BtcTransaction btcTx) {
        // Compute the total amount sent. Value could have been sent both to the
        // currently active federation and to the currently retiring federation.
        // Add both amounts up in that case.
        Coin amountToActive = btcTx.getValueSentToMe(getActiveFederationWallet(false));
        logger.debug("[computeTotalAmountSent] Amount sent to the active federation {}", amountToActive);

        Coin amountToRetiring = Coin.ZERO;
        Wallet retiringFederationWallet = getRetiringFederationWallet(false);
        if (retiringFederationWallet != null) {
            amountToRetiring = btcTx.getValueSentToMe(retiringFederationWallet);
        }
        logger.debug("[computeTotalAmountSent] Amount sent to the retiring federation {}", amountToRetiring);

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

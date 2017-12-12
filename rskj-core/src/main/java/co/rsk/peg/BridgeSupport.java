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
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Sha3Hash;
import co.rsk.panic.PanicProcessor;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.Denomination;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.RLP;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

import static org.ethereum.util.BIUtil.toBI;
import static org.ethereum.util.BIUtil.transfer;

/**
 * Helper class to move funds from btc to rsk and rsk to btc
 * @author Oscar Guindzberg
 */
public class BridgeSupport {
    public static final Integer FEDERATION_CHANGE_GENERIC_ERROR_CODE = -10;
    public static final Integer LOCK_WHITELIST_GENERIC_ERROR_CODE = -10;

    private static final Logger logger = LoggerFactory.getLogger("BridgeSupport");
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final Coin MINIMUM_FUNDS_TO_MIGRATE = Coin.CENT;

    final List<String> FEDERATION_CHANGE_FUNCTIONS = Collections.unmodifiableList(Arrays.asList(new String[]{
            "create",
            "add",
            "commit",
            "rollback"
    }));

    private final String contractAddress;
    private BridgeConstants bridgeConstants;
    private Context btcContext;

    private BtcBlockStore btcBlockStore;
    private BtcBlockChain btcBlockChain;

    private BridgeStorageProvider provider;
    private List<LogInfo> logs;

    private Repository rskRepository;

    private org.ethereum.core.Block rskExecutionBlock;
    private ReceiptStore rskReceiptStore;
    private org.ethereum.db.BlockStore rskBlockStore;

    private StoredBlock initialBtcStoredBlock;

    // Used by bridge
    public BridgeSupport(Repository repository, String contractAddress, Block rskExecutionBlock, ReceiptStore rskReceiptStore, BlockStore rskBlockStore, BridgeConstants bridgeConstants, List<LogInfo> logs) throws IOException, BlockStoreException {
        this(repository, contractAddress, new BridgeStorageProvider(repository, contractAddress), rskExecutionBlock, rskReceiptStore, rskBlockStore, bridgeConstants, logs);
    }


    // Used by unit tests
    public BridgeSupport(Repository repository, String contractAddress, BridgeStorageProvider provider, Block rskExecutionBlock, ReceiptStore rskReceiptStore, BlockStore rskBlockStore, BridgeConstants bridgeConstants, List<LogInfo> logs) throws IOException, BlockStoreException {
        this.provider = provider;

        this.bridgeConstants = bridgeConstants;
        NetworkParameters btcParams = bridgeConstants.getBtcParams();
        btcContext = new Context(btcParams);

        btcBlockStore = new RepositoryBlockStore(repository, contractAddress);
        if (btcBlockStore.getChainHead().getHeader().getHash().equals(btcParams.getGenesisBlock().getHash())) {
            // We are building the blockstore for the first time, so we have not set the checkpoints yet.
            long time = getActiveFederation().getCreationTime().toEpochMilli();
            InputStream checkpoints = this.getCheckPoints();
            if (time > 0 && checkpoints != null) {
                CheckpointManager.checkpoint(btcParams, checkpoints, btcBlockStore, time);
            }
        }
        btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);

        rskRepository = repository;

        this.initialBtcStoredBlock = this.getLowestBlock();
        this.logs = logs;
        this.rskExecutionBlock = rskExecutionBlock;
        this.rskReceiptStore = rskReceiptStore;
        this.rskBlockStore = rskBlockStore;
        this.contractAddress = contractAddress;
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

    // Used by unit tests
    public BridgeSupport(Repository repository, String contractAddress, BridgeStorageProvider provider, BtcBlockStore btcBlockStore, BtcBlockChain btcBlockChain) {
        this.provider = provider;

        bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        btcContext = new Context(bridgeConstants.getBtcParams());

        this.btcBlockStore = btcBlockStore;
        this.btcBlockChain = btcBlockChain;
        this.contractAddress = contractAddress;

        rskRepository = repository;
    }

    public void save() throws IOException {
        provider.save();
    }

    /**
     * Receives an array of serialized Bitcoin block headers and adds them to the internal BlockChain structure.
     * @param headers The bitcoin headers
     */
    public void receiveHeaders(BtcBlock[] headers) {
        if (headers.length > 0)
            logger.debug("Received {} headers. First {}, last {}.", headers.length, headers[0].getHash(), headers[headers.length - 1].getHash());
        else
            logger.warn("Received 0 headers");
        Context.propagate(btcContext);
        for (int i = 0; i < headers.length; i++) {
            try {
                btcBlockChain.add(headers[i]);
            } catch (Exception e) {
                // If we tray to add an orphan header bitcoinj throws an exception
                // This catches that case and any other exception that may be thrown
                logger.warn("Exception adding btc header", e);
            }
        }
    }

    /**
     * Get the wallet for the currently active federation
     * @return A BTC wallet for the currently active federation
     *
     * @throws IOException
     */
    public Wallet getActiveFederationWallet() throws IOException {
        Federation federation = getActiveFederation();
        List<UTXO> utxos = provider.getActiveFederationBtcUTXOs();

        return BridgeUtils.getFederationSpendWallet(btcContext, federation, utxos);
    }

    /**
     * Get the wallet for the currently retiring federation
     * or null if there's currently no retiring federation
     * @return A BTC wallet for the currently active federation
     *
     * @throws IOException
     */
    public Wallet getRetiringFederationWallet() throws IOException {
        Federation federation = getRetiringFederation();
        if (federation == null)
            return null;

        List<UTXO> utxos = provider.getRetiringFederationBtcUTXOs();

        return BridgeUtils.getFederationSpendWallet(btcContext, federation, utxos);
    }

    /**
     * In case of a lock tx: Transfers some SBTCs to the sender of the btc tx and keeps track of the new UTXOs available for spending.
     * In case of a release tx: Keeps track of the change UTXOs, now available for spending.
     * @param btcTx The bitcoin transaction
     * @param height The height of the bitcoin block that contains the tx
     * @param pmt Partial Merklee Tree that proves the tx is included in the btc block
     * @throws BlockStoreException
     * @throws IOException
     */
    public void registerBtcTransaction(Transaction rskTx, BtcTransaction btcTx, int height, PartialMerkleTree pmt) throws BlockStoreException, IOException {
        Context.propagate(btcContext);

        Federation federation = getActiveFederation();

        // Check the tx was not already processed
        if (provider.getBtcTxHashesAlreadyProcessed().keySet().contains(btcTx.getHash())) {
            logger.warn("Supplied tx was already processed");
            return;
        }

        // Check the tx is in the partial merkle tree
        List<Sha256Hash> hashesInPmt = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashesInPmt);
        if (!hashesInPmt.contains(btcTx.getHash())) {
            logger.warn("Supplied tx is not in the supplied partial merkle tree");
            panicProcessor.panic("btclock", "Supplied tx is not in the supplied partial merkle tree");
            return;
        }

        if (height < 0) {
            logger.warn("Height is " + height + " but should be greater than 0");
            panicProcessor.panic("btclock", "Height is " + height + " but should be greater than 0");
            return;
        }

        // Check there are at least N blocks on top of the supplied height
        int headHeight = btcBlockChain.getBestChainHeight();
        if ((headHeight - height + 1) < bridgeConstants.getBtc2RskMinimumAcceptableConfirmations()) {
            logger.warn("At least " + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations() + " confirmations are required, but there are only " + (headHeight - height) + " confirmations");
            return;
        }

        // Check the the merkle root equals merkle root of btc block at specified height in the btc best chain
        BtcBlock blockHeader = BridgeUtils.getStoredBlockAtHeight(btcBlockStore, height).getHeader();
        if (!blockHeader.getMerkleRoot().equals(merkleRoot)) {
            logger.warn("Supplied merkle root " + merkleRoot + "does not match block's merkle root " + blockHeader.getMerkleRoot());
            panicProcessor.panic("btclock", "Supplied merkle root " + merkleRoot + "does not match block's merkle root " + blockHeader.getMerkleRoot());
            return;
        }

        // Checks the transaction contents for sanity
        btcTx.verify();
        if (btcTx.getInputs().isEmpty()) {
            logger.warn("Tx has no inputs " + btcTx);
            panicProcessor.panic("btclock", "Tx has no inputs " + btcTx);
            return;
        }

        // Specific code for lock/release/none txs
        if (BridgeUtils.isLockTx(btcTx, getLiveFederations(), btcContext, bridgeConstants)) {
            logger.debug("This is a lock tx {}", btcTx);
            Script scriptSig = btcTx.getInput(0).getScriptSig();
            if (scriptSig.getChunks().size() != 2) {
                logger.warn("First input does not spend a Pay-to-PubkeyHash " + btcTx.getInput(0));
                panicProcessor.panic("btclock", "First input does not spend a Pay-to-PubkeyHash " + btcTx.getInput(0));
                return;
            }

            // Compute the total amount sent. Value could have been sent both to the
            // currently active federation as well as to the currently retiring federation.
            // Add both amounts up in that case.
            Coin amountToActive = btcTx.getValueSentToMe(getActiveFederationWallet());
            Coin amountToRetiring = Coin.ZERO;
            Wallet retiringFederationWallet = getRetiringFederationWallet();
            if (retiringFederationWallet != null) {
                amountToRetiring = btcTx.getValueSentToMe(retiringFederationWallet);
            }
            Coin totalAmount = amountToActive.add(amountToRetiring);

            // Get the sender public key
            byte[] data = scriptSig.getChunks().get(1).data;

            // Tx is a lock tx, check whether the sender is whitelisted
            BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);
            Address senderBtcAddress = new Address(btcContext.getParams(), senderBtcKey.getPubKeyHash());

            // If the address is not whitelisted, then return the funds
            // That is, get them in the release cycle
            // otherwise, transfer SBTC to the sender of the BTC
            // The RSK account to update is the one that matches the pubkey "spent" on the first bitcoin tx input
            if (!provider.getLockWhitelist().isWhitelisted(senderBtcAddress)) {
                boolean addResult = addToReleaseCycle(rskTx, senderBtcAddress, totalAmount);

                if (addResult) {
                    logger.info("whitelist money return succesful to {}. Tx {}. Value {}.", senderBtcAddress, rskTx, totalAmount);
                } else {
                    logger.warn("whitelist money return ignored because value is considered dust. To {}. Tx {}. Value {}.", senderBtcAddress, rskTx, totalAmount);
                }
            } else {
                org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(data);
                byte[] sender = key.getAddress();

                transfer(
                        rskRepository,
                        Hex.decode(PrecompiledContracts.BRIDGE_ADDR),
                        sender,
                        Denomination.satoshisToWeis(BigInteger.valueOf(totalAmount.getValue()))
                );
            }
        } else if (BridgeUtils.isReleaseTx(btcTx, federation, bridgeConstants)) {
            logger.debug("This is a release tx {}", btcTx);
            // do-nothing
            // We could call removeUsedUTXOs(btcTx) here, but we decided to not do that.
            // Used utxos should had been removed when we created the release tx.
            // Invoking removeUsedUTXOs() here would make "some" sense in theses scenarios:
            // a) In testnet, devnet or local: we restart the RSK blockchain whithout changing the federation address. We don't want to have utxos that were already spent.
            // Open problem: TxA spends TxB. registerBtcTransaction() for TxB is called, it spends a utxo the bridge is not yet aware of,
            // so nothing is removed. Then registerBtcTransaction() for TxA and the "already spent" utxo is added as it was not spent.
            // When is not guaranteed to be called in the chronological order, so a Federator can inform
            // b) In prod: Federator created a tx manually or the federation was compromised and some utxos were spent. Better not try to spend them.
            // Open problem: For performance removeUsedUTXOs() just removes 1 utxo
        } else {
            logger.warn("This is not a lock nor a release tx {}", btcTx);
            panicProcessor.panic("btclock", "This is not a lock nor a release tx " + btcTx);
            return;
        }

        Sha256Hash btcTxHash = btcTx.getHash();

        // Mark tx as processed on this block
        provider.getBtcTxHashesAlreadyProcessed().put(btcTxHash, rskExecutionBlock.getNumber());

        // Save UTXOs from the federation(s)
        saveNewUTXOs(btcTx);
        logger.info("BTC Tx {} processed in RSK", btcTxHash);
    }

    /*
      Add the btcTx outputs that send btc to the federation(s) to the UTXO list
     */
    private void saveNewUTXOs(BtcTransaction btcTx) throws IOException {
        // Outputs to the active federation
        List<TransactionOutput> outputsToTheActiveFederation = btcTx.getWalletOutputs(getActiveFederationWallet());
        for (TransactionOutput output : outputsToTheActiveFederation) {
            UTXO utxo = new UTXO(btcTx.getHash(), output.getIndex(), output.getValue(), 0, btcTx.isCoinBase(), output.getScriptPubKey());
            provider.getActiveFederationBtcUTXOs().add(utxo);
        }

        // Outputs to the retiring federation (if any)
        Wallet retiringFederationWallet = getRetiringFederationWallet();
        if (retiringFederationWallet != null) {
            List<TransactionOutput> outputsToTheRetiringFederation = btcTx.getWalletOutputs(retiringFederationWallet);
            for (TransactionOutput output : outputsToTheRetiringFederation) {
                UTXO utxo = new UTXO(btcTx.getHash(), output.getIndex(), output.getValue(), 0, btcTx.isCoinBase(), output.getScriptPubKey());
                provider.getRetiringFederationBtcUTXOs().add(utxo);
            }
        }
    }

    /*
      Removes the outputs spent by btcTx inputs from the UTXO list
     */
    private void removeUsedUTXOs(BtcTransaction btcTx) throws IOException {
        for (TransactionInput transactionInput : btcTx.getInputs()) {
            Iterator<UTXO> iter = provider.getActiveFederationBtcUTXOs().iterator();
            while (iter.hasNext()) {
                UTXO utxo = iter.next();
                if (utxo.getHash().equals(transactionInput.getOutpoint().getHash()) && utxo.getIndex() == transactionInput.getOutpoint().getIndex()) {
                    iter.remove();
                    break;
                }
            }

            provider.getRetiringFederationBtcUTXOs().removeIf(
                utxo -> utxo.getHash().equals(transactionInput.getOutpoint().getHash())
                    && utxo.getIndex() == transactionInput.getOutpoint().getIndex()
            );
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
        byte[] senderCode = rskRepository.getCode(rskTx.getSender());

        //as we can't send btc from contracts we want to send them back to the sender
        if (senderCode != null && senderCode.length > 0) {
            logger.trace("Contract {} tried to release funds. Release is just allowed from standard accounts.", Hex.toHexString(rskTx.getSender()));
            throw new Program.OutOfGasException("Contract calling releaseBTC");
        }

        Context.propagate(btcContext);
        NetworkParameters btcParams = bridgeConstants.getBtcParams();
        Address btcDestinationAddress = BridgeUtils.recoverBtcAddressFromEthTransaction(rskTx, btcParams);
        BigInteger valueInWeis = toBI(rskTx.getValue());
        Coin value = Coin.valueOf(Denomination.weisToSatoshis(valueInWeis).longValue());
        boolean addResult = addToReleaseCycle(rskTx, btcDestinationAddress, value);

        if (addResult) {
            logger.info("releaseBtc succesful to {}. Tx {}. Value {}.", btcDestinationAddress, rskTx, value);
        } else {
            logger.warn("releaseBtc ignored because value is considered dust. To {}. Tx {}. Value {}.", btcDestinationAddress, rskTx, value);
        }
    }

    /**
     * Creates a BTC transaction for BTC release and
     * adds it to the confirm/sign/release cycle.
     *
     * @param rskTx the RSK transaction that caused this BTC release.
     * @param destinationAddress the destination BTC address.
     * @param value the amount of BTC to release.
     * @return true if the btc transaction was successfully added, false if the value to release was
     * considered dust and therefore ignored.
     * @throws IOException
     */
    private boolean addToReleaseCycle(Transaction rskTx, Address destinationAddress, Coin value) throws IOException {
        // Track tx in the contract memory as "waiting for 100 rsk blocks in order to release BTC
        BtcTransaction btcTx = new BtcTransaction(btcContext.getParams());
        btcTx.addOutput(value, destinationAddress);
        TransactionOutput btcTxOutput = btcTx.getOutput(0);

        if (btcTxOutput.getValue().isGreaterThan(bridgeConstants.getMinimumReleaseTxValue())) {
            provider.getRskTxsWaitingForConfirmations().put(new Sha3Hash(rskTx.getHash()), btcTx);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Executed every now and then.
     * Iterates rskTxsWaitingForConfirmations map searching for txs with enough confirmations in the RSK blockchain.
     * If it finds one, adds unsigned inputs and change output and moves it to rskTxsWaitingForSignatures map.
     * @throws IOException
     * @param rskTx current RSK transaction
     */
    public void updateCollections(Transaction rskTx) throws IOException {
        Context.propagate(btcContext);
        boolean pendingSignatures = !provider.getRskTxsWaitingForSignatures().isEmpty();
        long activeFederationAge = rskExecutionBlock.getNumber() - getFederationCreationBlockNumber();
        Wallet retiringFederationWallet = getRetiringFederationWallet();

        if (activeFederationAge > bridgeConstants.getFundsMigrationAgeBegin() && activeFederationAge < bridgeConstants.getFundsMigrationAgeEnd()
                && retiringFederationWallet != null
                && retiringFederationWallet.getBalance().isGreaterThan(MINIMUM_FUNDS_TO_MIGRATE)
                && !pendingSignatures) {

            BtcTransaction btcTx = createMigrationTransaction(retiringFederationWallet, getActiveFederation().getAddress());
            provider.getRskTxsWaitingForSignatures().put(new Sha3Hash(rskTx.getHash()), btcTx);
        }

        if (retiringFederationWallet != null && activeFederationAge >= bridgeConstants.getFundsMigrationAgeEnd() && !pendingSignatures) {
            if (retiringFederationWallet.getBalance().isGreaterThan(Coin.ZERO)) {
                try {
                    BtcTransaction btcTx = createMigrationTransaction(retiringFederationWallet, getActiveFederation().getAddress());
                    provider.getRskTxsWaitingForSignatures().put(new Sha3Hash(rskTx.getHash()), btcTx);
                } catch (Exception e) {
                    logger.error("Unable to complete retiring federation migration. Left balance: {} in {}", retiringFederationWallet.getBalance(), getRetiringFederationAddress());
                    panicProcessor.panic("updateCollection", "Unable to complete retiring federation migration.");
                }
            }
            provider.setRetiringFederation(null);
        }
        
        Iterator<Map.Entry<Sha3Hash, BtcTransaction>> iter = provider.getRskTxsWaitingForConfirmations().entrySet().iterator();
        while (iter.hasNext() && provider.getRskTxsWaitingForSignatures().isEmpty()) {
            Map.Entry<Sha3Hash, BtcTransaction> entry = iter.next();
            // We copy the entry's key and value to temporary variables because iter.remove() changes the values of entry's key and value
            Sha3Hash rskTxHash = entry.getKey();
            if (hasEnoughConfirmations(rskTxHash)) {
                // We create a new Transaction instance because if ExceededMaxTransactionSize is thrown, the tx included in the SendRequest
                // is altered and then we would try to persist the altered version in the waiting for confirmations collection
                // Inputs would not be disconnected, so we would have a second problem trying to persist non java serializable objects.
                BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
                btcTx.parseNoInputs(entry.getValue().bitcoinSerialize());
                try {
                    SendRequest sr = SendRequest.forTx(btcTx);
                    sr.feePerKb = Coin.MILLICOIN;
                    sr.missingSigsMode = Wallet.MissingSigsMode.USE_OP_ZERO;
                    sr.changeAddress = getActiveFederation().getAddress();
                    sr.shuffleOutputs = false;
                    sr.recipientsPayFees = true;
                    getActiveFederationWallet().completeTx(sr);
                } catch (InsufficientMoneyException e) {
                    logger.warn("Not enough confirmed BTC in the federation wallet to complete " + rskTxHash + " " + btcTx, e);
                    // Comment out panic logging for now
                    // panicProcessor.panic("nomoney", "Not enough confirmed BTC in the federation wallet to complete " + rskTxHash + " " + btcTx);
                    continue;
                } catch (Wallet.CouldNotAdjustDownwards e) {
                    logger.warn("A user output could not be adjusted downwards to pay tx fees " + rskTxHash + " " + btcTx, e);
                    // Comment out panic logging for now
                    // panicProcessor.panic("couldnotadjustdownwards", "A user output could not be adjusted downwards to pay tx fees " + rskTxHash + " " + btcTx);
                    continue;
                } catch (Wallet.ExceededMaxTransactionSize e) {
                    logger.warn("Tx size too big " + rskTxHash + " " + btcTx, e);
                    // Comment out panic logging for now
                    // panicProcessor.panic("exceededmaxtransactionsize", "Tx size too big " + rskTxHash + " " + btcTx);
                    continue;
                }
                adjustBalancesIfChangeOutputWasDust(btcTx, entry.getValue().getOutput(0).getValue());


                // Disconnect input from output because we don't need the reference and it interferes serialization
                for (TransactionInput transactionInput : btcTx.getInputs()) {
                    transactionInput.disconnect();
                }
                iter.remove();
                provider.getRskTxsWaitingForSignatures().put(rskTxHash, btcTx);
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
        Coin change = btcTx.getOutput(1).getValue();
        Coin spentByFederation = sumInputs.subtract(change);
        if (spentByFederation.isLessThan(sentByUser)) {
            Coin coinsToBurn = sentByUser.subtract(spentByFederation);
            byte[] burnAddress = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBurnAddress();
            transfer(rskRepository, Hex.decode(PrecompiledContracts.BRIDGE_ADDR), burnAddress, Denomination.satoshisToWeis(BigInteger.valueOf(coinsToBurn.getValue())));
        }
    }

    /**
     * Return true if txToSendToBtc has enough confirmations so bitcoins can be released
     */
    boolean hasEnoughConfirmations(Sha3Hash rskTxHash) {
        //Search the TransactionInfo using the parent block because execution block may not be in the blockstore yet.
        TransactionInfo info = rskReceiptStore.get(rskTxHash.getBytes(), rskExecutionBlock.getParentHash(), rskBlockStore);
        if (info == null) {
            return false;
        }
        
        byte[] includedBlockHash = info.getBlockHash();
        org.ethereum.core.Block includedBlock = rskBlockStore.getBlockByHash(includedBlockHash);

        if (includedBlock == null)
            return false;

        return (rskExecutionBlock.getNumber() - includedBlock.getNumber() + 1) >= bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations();
    }

    /**
     * Adds a federator signature to a btc release tx.
     * The hash for the signature must be calculated with Transaction.SigHash.ALL and anyoneCanPay=false. The signature must be canonical.
     * If enough signatures were added, ask federators to broadcast the btc release tx.
     *
     * @param executionBlockNumber The block number of the block that is currently being procesed
     * @param federatorPublicKey   Federator who is signing
     * @param signatures           1 signature per btc tx input
     * @param rskTxHash            The id of the rsk tx
     */
    public void addSignature(long executionBlockNumber, BtcECKey federatorPublicKey, List<byte[]> signatures, byte[] rskTxHash) throws Exception {
        Context.propagate(btcContext);
        Federation retiringFederation = getRetiringFederation();
        if (!getActiveFederation().getPublicKeys().contains(federatorPublicKey) && (retiringFederation == null || !retiringFederation.getPublicKeys().contains(federatorPublicKey))) {
            logger.warn("Supplied federator public key {} does not belong to any of the federators.", federatorPublicKey);
            return;
        }
        BtcTransaction btcTx = provider.getRskTxsWaitingForSignatures().get(new Sha3Hash(rskTxHash));
        if (btcTx == null) {
            logger.warn("No tx waiting for signature for hash {}. Probably fully signed already.", new Sha3Hash(rskTxHash));
            return;
        }
        if (btcTx.getInputs().size() != signatures.size()) {
            logger.warn("Expected {} signatures but received {}.", btcTx.getInputs().size(), signatures.size());
            return;
        }
        processSigning(executionBlockNumber, federatorPublicKey, signatures, rskTxHash, btcTx);
    }

    private void processSigning(long executionBlockNumber, BtcECKey federatorPublicKey, List<byte[]> signatures, byte[] rskTxHash, BtcTransaction btcTx) throws IOException {
        int i = 0;
        for (TransactionInput txIn : btcTx.getInputs()) {
            Script inputScript = txIn.getScriptSig();
            List<ScriptChunk> chunks = inputScript.getChunks();
            byte[] program = chunks.get(chunks.size() - 1).data;
            Script redeemScript = new Script(program);
            Sha256Hash sighash = btcTx.hashForSignature(i, redeemScript, BtcTransaction.SigHash.ALL, false);
            BtcECKey.ECDSASignature sig = BtcECKey.ECDSASignature.decodeFromDER(signatures.get(i));

            if (!federatorPublicKey.verify(sighash, sig)) {
                logger.warn("Signature {} {} is not valid for hash {} and public key {}", i, Hex.toHexString(sig.encodeToDER()), sighash, federatorPublicKey);
                return;
            }
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
            if (!txSig.isCanonical()) {
                logger.warn("Signature {} is not canonical.", Hex.toHexString(signatures.get(i)));
                return;
            }

            boolean alreadySignedByThisFederator = isSignatureSignedByThisFederator(
                    federatorPublicKey,
                    sighash,
                    inputScript.getChunks());

            if (!alreadySignedByThisFederator) {
                try {
                    int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
                    inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex, 1, 1);
                    txIn.setScriptSig(inputScript);
                    logger.debug("Tx input for tx {} signed.", new Sha3Hash(rskTxHash));
                } catch (IllegalStateException e) {
                    Federation retiringFederation = getRetiringFederation();
                    if (getActiveFederation().hasPublicKey(federatorPublicKey)) {
                        logger.debug("A member of the active federation is trying to sign a tx of the retiring one");
                        return;
                    } else if (retiringFederation != null && retiringFederation.hasPublicKey(federatorPublicKey)) {
                        logger.debug("A member of the retiring federation is trying to sign a tx of the active one");
                        return;
                    }
                    throw e;
                }
            } else {
                logger.warn("Tx {} already signed by this federator.", new Sha3Hash(rskTxHash));
                break;
            }

            i++;
        }
        // If tx fully signed
        if (hasEnoughSignatures(btcTx)) {
            logger.info("Tx fully signed {}. Hex: {}", btcTx, Hex.toHexString(btcTx.bitcoinSerialize()));
            removeUsedUTXOs(btcTx);
            provider.getRskTxsWaitingForSignatures().remove(new Sha3Hash(rskTxHash));
            logs.add(
                new LogInfo(
                    TypeConverter.stringToByteArray(contractAddress),
                    Collections.singletonList(Bridge.RELEASE_BTC_TOPIC),
                    RLP.encodeElement(btcTx.bitcoinSerialize())
                )
            );
        } else {
            logger.debug("Tx not yet fully signed {}.", new Sha3Hash(rskTxHash));
        }
    }

    /**
     * Check the p2sh multisig scriptsig represented by chunkList was already signed by federatorPublicKey.
     * @param federatorPublicKey The key that may have been used to sign
     * @param sighash the sighash that may have been signed
     * @param chunkList The scriptsig
     * @return true if it was already signed by the specified key, false otherwise.
     */
    private boolean isSignatureSignedByThisFederator(BtcECKey federatorPublicKey, Sha256Hash sighash, List<ScriptChunk> chunkList) {
        for (int j = 1; j < chunkList.size() - 1; j++) {
            ScriptChunk scriptChunk = chunkList.get(j);

            if (scriptChunk.data.length == 0)
                continue;

            TransactionSignature sig2 = TransactionSignature.decodeFromBitcoin(scriptChunk.data, false, false);

            if (federatorPublicKey.verify(sighash, sig2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a btc tx has been signed by the required number of federators.
     * @param btcTx The btc tx to check
     * @return True if was signed by the required number of federators, false otherwise
     */
    private boolean hasEnoughSignatures(BtcTransaction btcTx) {
        // When the tx is constructed OP_0 are placed where signature should go.
        // Check all OP_0 have been replaced with actual signatures
        Context.propagate(btcContext);
        Script scriptSig = btcTx.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = scriptSig.getChunks();
        for (int i = 1; i < chunks.size(); i++) {
            ScriptChunk chunk = chunks.get(i);
            if (!chunk.isOpCode() && chunk.data.length == 0) {
                return false;
            }
        }
        return true;
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
    public byte[] getStateForDebugging() throws IOException {
        BridgeState stateForDebugging = new BridgeState(getBtcBlockchainBestChainHeight(), provider);

        return stateForDebugging.getEncoded();
    }

    /**
     * Returns the bitcoin blockchain best chain height know by the bridge contract
     */
    public int getBtcBlockchainBestChainHeight() throws IOException {
        return btcBlockChain.getChainHead().getHeight();
    }

    /**
     * Returns an array of block hashes known by the bridge contract. Federators can use this to find what is the latest block in the mainchain the bridge has.
     * @return a List of bitcoin block hashes
     */
    public List<Sha256Hash> getBtcBlockchainBlockLocator() throws IOException {
        final int maxHashesToInform = 100;
        List<Sha256Hash> blockLocator = new ArrayList<>();
        StoredBlock cursor = btcBlockChain.getChainHead();
        int bestBlockHeight = cursor.getHeight();
        blockLocator.add(cursor.getHeader().getHash());
        if (bestBlockHeight > this.initialBtcStoredBlock.getHeight()) {
            boolean stop = false;
            int i = 0;
            try {
                while (blockLocator.size() <= maxHashesToInform && !stop) {
                    int blockHeight = (int) (bestBlockHeight - Math.pow(2, i));
                    if (blockHeight <= this.initialBtcStoredBlock.getHeight()) {
                        blockLocator.add(this.initialBtcStoredBlock.getHeader().getHash());
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
                blockLocator.add(this.initialBtcStoredBlock.getHeader().getHash());
            }
        }
        return blockLocator;
    }

    private StoredBlock getPrevBlockAtHeight(StoredBlock cursor, int height) throws BlockStoreException {
        if (cursor.getHeight() == height)
            return cursor;

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
        return provider.getBtcTxHashesAlreadyProcessed().containsKey(btcTxHash);
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
        Map<Sha256Hash, Long> btcTxHashes = provider.getBtcTxHashesAlreadyProcessed();

        // Return -1 if the transaction hasn't been processed
        if (!btcTxHashes.containsKey(btcTxHash)) {
            return -1L;
        }

        return btcTxHashes.get(btcTxHash);
    }

    /**
     * Returns the active federation.
     * @return the active federation.
     */
    public Federation getActiveFederation() {
        Federation currentFederation = provider.getActiveFederation();

        if (currentFederation == null)
            currentFederation = bridgeConstants.getGenesisFederation();

        return currentFederation;
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
        return getActiveFederation().getPublicKeys().size();
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
        List<BtcECKey> publicKeys = getActiveFederation().getPublicKeys();

        if (index < 0 || index >= publicKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Federator index must be between 0 and {}", publicKeys.size() - 1));
        }

        return publicKeys.get(index).getPubKey();
    }

    /**
     * Returns the federation's creation time
     * @return the federation creation time
     */
    public Instant getFederationCreationTime() {
        return getActiveFederation().getCreationTime();
    }


    public long getFederationCreationBlockNumber() {
        return getActiveFederation().getCreationBlockNumber();
    }

    public long getRetiringFederationCreationBlockNumber() {
        Federation retiringFederation = provider.getRetiringFederation();
        if (retiringFederation == null) {
            return -1L;
        }
        return retiringFederation.getCreationBlockNumber();
    }

    /**
     * Returns the retiring federation bitcoin address.
     * @return the retiring federation bitcoin address, null if no retiring federation exists
     */
    public Address getRetiringFederationAddress() {
        Federation retiringFederation = provider.getRetiringFederation();
        if (retiringFederation == null)
            return null;

        return retiringFederation.getAddress();
    }

    /**
     * Returns the retiring federation's size
     * @return the retiring federation size, -1 if no retiring federation exists
     */
    public Integer getRetiringFederationSize() {
        Federation retiringFederation = provider.getRetiringFederation();
        if (retiringFederation == null)
            return -1;

        return retiringFederation.getPublicKeys().size();
    }

    /**
     * Returns the retiring federation's minimum required signatures
     * @return the retiring federation minimum required signatures, -1 if no retiring federation exists
     */
    public Integer getRetiringFederationThreshold() {
        Federation retiringFederation = provider.getRetiringFederation();
        if (retiringFederation == null)
            return -1;

        return retiringFederation.getNumberOfSignaturesRequired();
    }

    /**
     * Returns the public key of the retiring federation's federator at the given index
     * @param index the retiring federator's index (zero-based)
     * @return the retiring federator's public key, null if no retiring federation exists
     */
    public byte[] getRetiringFederatorPublicKey(int index) {
        Federation retiringFederation = provider.getRetiringFederation();
        if (retiringFederation == null)
            return null;

        List<BtcECKey> publicKeys = retiringFederation.getPublicKeys();

        if (index < 0 || index >= publicKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Retiring federator index must be between 0 and {}", publicKeys.size() - 1));
        }

        return publicKeys.get(index).getPubKey();
    }

    /**
     * Returns the retiring federation's creation time
     * @return the retiring federation creation time, null if no retiring federation exists
     */
    public Instant getRetiringFederationCreationTime() {
        Federation retiringFederation = provider.getRetiringFederation();
        if (retiringFederation == null)
            return null;

        return retiringFederation.getCreationTime();
    }

    /**
     * Builds and returns the retiring federation (if one exists)
     * @return the retiring federation, null if none exists
     */
    @Nullable
    private Federation getRetiringFederation() {
        Integer size = getRetiringFederationSize();
        if (size == -1)
            return null;

        List<BtcECKey> publicKeys = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            publicKeys.add(BtcECKey.fromPublicOnly(getRetiringFederatorPublicKey(i)));
        }
        Instant creationTime = getRetiringFederationCreationTime();
        long creationBlockNumber = getRetiringFederationCreationBlockNumber();

        return new Federation(publicKeys, creationTime, creationBlockNumber, bridgeConstants.getBtcParams());
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
     * Otherwise, -1 is returned if there's already a pending federation
     * or -2 is returned if funds are left from a previous one.
     * @param dryRun whether to just do a dry run
     * @return 1 upon success, -1 when a pending federation is present, -2 when funds are still
     * to be moved between federations.
     */
    private Integer createFederation(boolean dryRun) throws IOException {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation != null) {
            return -1;
        }

        if (provider.getRetiringFederation() != null) {
            return -2;
        }

        if (dryRun)
            return 1;

        currentPendingFederation = new PendingFederation(Collections.emptyList());

        provider.setPendingFederation(currentPendingFederation);

        // Clear votes on election
        provider.getFederationElection(bridgeConstants.getFederationChangeAuthorizer()).clear();

        return 1;
    }

    /**
     * Adds the given key to the current pending federation
     * @param dryRun whether to just do a dry run
     * @param key the public key to add
     * @return 1 upon success, -1 if there was no pending federation, -2 if the key was already in the pending federation
     */
    private Integer addFederatorPublicKey(boolean dryRun, BtcECKey key) {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return -1;
        }

        if (currentPendingFederation.getPublicKeys().contains(key)) {
            return -2;
        }

        if (dryRun)
            return 1;

        currentPendingFederation = currentPendingFederation.addPublicKey(key);

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
    private Integer commitFederation(boolean dryRun, Sha3Hash hash) throws IOException {
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

        if (dryRun)
            return 1;

        // Move UTXOs from the active federation into the retiring federation
        // and clear the active federation's UTXOs
        List<UTXO> utxosToMove = new ArrayList<>(provider.getActiveFederationBtcUTXOs());
        provider.getActiveFederationBtcUTXOs().clear();
        List<UTXO> retiringFederationUTXOs = provider.getRetiringFederationBtcUTXOs();
        retiringFederationUTXOs.clear();
        utxosToMove.forEach(utxo -> retiringFederationUTXOs.add(utxo));

        // Network parameters for the new federation are taken from the bridge constants.
        // Creation time is the block's timestamp.
        Instant creationTime = Instant.ofEpochMilli(rskExecutionBlock.getTimestamp());
        provider.setRetiringFederation(getActiveFederation());
        provider.setActiveFederation(currentPendingFederation.buildFederation(creationTime, rskExecutionBlock.getNumber(), bridgeConstants.getBtcParams()));
        provider.setPendingFederation(null);

        // Clear votes on election
        provider.getFederationElection(bridgeConstants.getFederationChangeAuthorizer()).clear();

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

        if (dryRun)
            return 1;

        provider.setPendingFederation(null);

        // Clear votes on election
        provider.getFederationElection(bridgeConstants.getFederationChangeAuthorizer()).clear();

        return 1;
    }

    public Integer voteFederationChange(Transaction tx, ABICallSpec callSpec) {
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
        } catch (IOException e) {
            result = new ABICallVoteResult(false, FEDERATION_CHANGE_GENERIC_ERROR_CODE);
        } catch (BridgeIllegalArgumentException e) {
            result = new ABICallVoteResult(false, FEDERATION_CHANGE_GENERIC_ERROR_CODE);
        }

        // Return if the dry run failed or we are on a reversible execution
        if (!result.wasSuccessful()) {
            return (Integer) result.getResult();
        }

        ABICallElection election = provider.getFederationElection(authorizer);
        // Register the vote. It is expected to succeed, since all previous checks succeeded
        if (!election.vote(callSpec, TxSender.fromTx(tx))) {
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

    private ABICallVoteResult executeVoteFederationChangeFunction(boolean dryRun, ABICallSpec callSpec) throws IOException {
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
                byte[] publicKeyBytes = (byte[]) callSpec.getArguments()[0];
                BtcECKey publicKey;
                try {
                    publicKey = BtcECKey.fromPublicOnly(publicKeyBytes);
                } catch (Exception e) {
                    throw new BridgeIllegalArgumentException("Public key could not be parsed " + Hex.toHexString(publicKeyBytes), e);
                }
                executionResult = addFederatorPublicKey(dryRun, publicKey);
                result = new ABICallVoteResult(executionResult == 1, executionResult);
                break;
            case "commit":
                Sha3Hash hash = new Sha3Hash((byte[]) callSpec.getArguments()[0]);
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

        return currentPendingFederation.getPublicKeys().size();
    }

    /**
     * Returns the currently pending federation threshold, or null if none exists
     * @param index the federator's index (zero-based)
     * @return the pending federation's federator public key
     */
    public byte[] getPendingFederatorPublicKey(int index) {
        PendingFederation currentPendingFederation = provider.getPendingFederation();

        if (currentPendingFederation == null) {
            return null;
        }

        List<BtcECKey> publicKeys = currentPendingFederation.getPublicKeys();

        if (index < 0 || index >= publicKeys.size()) {
            throw new IndexOutOfBoundsException(String.format("Federator index must be between 0 and {}", publicKeys.size() - 1));
        }

        return publicKeys.get(index).getPubKey();
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
    public String getLockWhitelistAddress(int index) {
        List<Address> addresses = provider.getLockWhitelist().getAddresses();

        if (index < 0 || index >= addresses.size()) {
            return null;
        }

        return addresses.get(index).toBase58();
    }

    /**
     * Adds the given address to the lock whitelist.
     * Returns 1 upon success, or -1 if the address was
     * already in the whitelist.
     * @param addressBase58 the base58-encoded address to add to the whitelist
     * @return 1 upon success, -1 if the address was already
     * in the whitelist, -2 if address is invalid
     * LOCK_WHITELIST_GENERIC_ERROR_CODE otherwise.
     */
    public Integer addLockWhitelistAddress(Transaction tx, String addressBase58) {
        if (!isLockWhitelistChangeAuthorized(tx))
            return LOCK_WHITELIST_GENERIC_ERROR_CODE;

        LockWhitelist whitelist = provider.getLockWhitelist();

        try {
            Address address = Address.fromBase58(btcContext.getParams(), addressBase58);

            if (!whitelist.add(address)) {
                return -1;
            }

            return 1;
        } catch (AddressFormatException e) {
            return -2;
        } catch (Exception e) {
            logger.error("Unexpected error in addLockWhitelistAddress: {}", e.getMessage());
            panicProcessor.panic("lock-whitelist", e.getMessage());
            return 0;
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
        if (!isLockWhitelistChangeAuthorized(tx))
            return LOCK_WHITELIST_GENERIC_ERROR_CODE;

        LockWhitelist whitelist = provider.getLockWhitelist();

        try {
            Address address = Address.fromBase58(btcContext.getParams(), addressBase58);

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
    public Coin getMinimumLockTxValue() {
        return bridgeConstants.getMinimumLockTxValue();
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

    @VisibleForTesting
    BtcBlockStore getBtcBlockStore() {
        return btcBlockStore;
    }

    private static BtcTransaction createMigrationTransaction(Wallet originWallet, Address destinationAddress) {
        Coin expectedMigrationValue = originWallet.getBalance();
        for(;;) {
            BtcTransaction migrationBtcTx = new BtcTransaction(originWallet.getParams());
            migrationBtcTx.addOutput(expectedMigrationValue, destinationAddress);

            SendRequest sr = SendRequest.forTx(migrationBtcTx);
            sr.changeAddress = destinationAddress;
            sr.feePerKb = Coin.MILLICOIN;
            sr.missingSigsMode = Wallet.MissingSigsMode.USE_OP_ZERO;
            sr.recipientsPayFees = true;
            try {
                originWallet.completeTx(sr);
                for (TransactionInput transactionInput : migrationBtcTx.getInputs()) {
                    transactionInput.disconnect();
                }
                return migrationBtcTx;
            } catch (InsufficientMoneyException | Wallet.ExceededMaxTransactionSize | Wallet.CouldNotAdjustDownwards e) {
                expectedMigrationValue = expectedMigrationValue.divide(2);
            } catch(Wallet.DustySendRequested e) {
                throw new IllegalStateException("Retiring federation wallet cannot be emptied", e);
            }
        }
    }
}


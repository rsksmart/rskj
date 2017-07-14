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
import co.rsk.crypto.Sha3Hash;
import co.rsk.panic.PanicProcessor;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.SendRequest;
import co.rsk.bitcoinj.wallet.Wallet;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Denomination;
import org.ethereum.core.Repository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

import static org.ethereum.util.BIUtil.toBI;
import static org.ethereum.util.BIUtil.transfer;

/**
 * Helper class to move funds from btc to rsk and rsk to btc
 */
public class BridgeSupport {
    private static final Logger logger = LoggerFactory.getLogger("BridgeSupport");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private BridgeConstants bridgeConstants;
    private Context btcContext;

    private BtcBlockStore btcBlockStore;
    private BtcBlockChain btcBlockChain;

    private BridgeStorageProvider provider;

    private Repository rskRepository;

    private org.ethereum.core.Block rskExecutionBlock;
    private ReceiptStore rskReceiptStore;
    private org.ethereum.db.BlockStore rskBlockStore;

    private StoredBlock initialBtcStoredBlock;

    // Used by bridge
    public BridgeSupport(Repository repository, String contractAddress, org.ethereum.core.Block rskExecutionBlock, ReceiptStore rskReceiptStore, org.ethereum.db.BlockStore rskBlockStore) throws IOException, BlockStoreException {
        this(repository, contractAddress, new BridgeStorageProvider(repository, contractAddress), rskExecutionBlock, rskReceiptStore, rskBlockStore);
    }


    // Used by unit tests
    public BridgeSupport(Repository repository, String contractAddress, BridgeStorageProvider provider, org.ethereum.core.Block rskExecutionBlock, ReceiptStore rskReceiptStore, org.ethereum.db.BlockStore rskBlockStore) throws IOException, BlockStoreException {
        this.provider = provider;

        bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        NetworkParameters btcParams = bridgeConstants.getBtcParams();
        btcContext = new Context(btcParams);

        btcBlockStore = new RepositoryBlockStore(repository, contractAddress);
        if (btcBlockStore.getChainHead().getHeader().getHash().equals(btcParams.getGenesisBlock().getHash())) {
            // We are building the blockstore for the first time, so we have not set the checkpoints yet.
            long time = bridgeConstants.getFederationAddressCreationTime();
            InputStream checkpoints = this.getCheckPoints();
            if (time > 0 && checkpoints != null) {
                CheckpointManager.checkpoint(btcParams, checkpoints, btcBlockStore, time);
            }
        }
        btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);

        rskRepository = repository;

        this.initialBtcStoredBlock = this.getLowestBlock();

        this.rskExecutionBlock = rskExecutionBlock;
        this.rskReceiptStore = rskReceiptStore;
        this.rskBlockStore = rskBlockStore;
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

        bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        btcContext = new Context(bridgeConstants.getBtcParams());

        this.btcBlockStore = btcBlockStore;
        this.btcBlockChain = btcBlockChain;

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
     * In case of a lock tx: Transfers some SBTCs to the sender of the btc tx and keeps track of the new UTXOs available for spending.
     * In case of a release tx: Keeps track of the change UTXOs, now available for spending.
     * @param btcTx The bitcoin transaction
     * @param height The height of the bitcoin block that contains the tx
     * @param pmt Partial Merklee Tree that proves the tx is included in the btc block
     * @throws BlockStoreException
     * @throws IOException
     */
    public void registerBtcTransaction(BtcTransaction btcTx, int height, PartialMerkleTree pmt) throws BlockStoreException, IOException {
        Context.propagate(btcContext);

        // Check the tx was not already processed
        if (provider.getBtcTxHashesAlreadyProcessed().contains(btcTx.getHash())) {
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
        if (BridgeUtils.isLockTx(btcTx, provider.getWallet(), bridgeConstants)) {
            logger.debug("This is a lock tx {}", btcTx);
            Script scriptSig = btcTx.getInput(0).getScriptSig();
            if (scriptSig.getChunks().size() != 2) {
                logger.warn("First input does not spend a Pay-to-PubkeyHash " + btcTx.getInput(0));
                panicProcessor.panic("btclock", "First input does not spend a Pay-to-PubkeyHash " + btcTx.getInput(0));
                return;
            }
            // Tx is a lock tx, transfer SBTC to the sender of the BTC
            // The RSK account to update is the one that matches the pubkey "spent" on the first bitcoin tx input
            byte[] data = scriptSig.getChunks().get(1).data;
            org.ethereum.crypto.ECKey key = org.ethereum.crypto.ECKey.fromPublicOnly(data);
            byte[] sender = key.getAddress();
            Coin amount = btcTx.getValueSentToMe(provider.getWallet());
            transfer(rskRepository, Hex.decode(PrecompiledContracts.BRIDGE_ADDR), sender, Denomination.satoshisToWeis(BigInteger.valueOf(amount.getValue())));
        } else if (BridgeUtils.isReleaseTx(btcTx, bridgeConstants)) {
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

        // Mark tx as processed
        provider.getBtcTxHashesAlreadyProcessed().add(btcTxHash);

        saveNewUTXOs(btcTx);
        logger.info("BTC Tx {} processed in RSK", btcTxHash);
    }

    /*
      Add the btcTx outputs that send btc to the federation to the UTXO list
     */
    private void saveNewUTXOs(BtcTransaction btcTx) throws IOException {
        List<TransactionOutput> outputsToTheFederation = btcTx.getWalletOutputs(provider.getWallet());
        for (TransactionOutput output : outputsToTheFederation) {
            UTXO utxo = new UTXO(btcTx.getHash(), output.getIndex(), output.getValue(), 0, btcTx.isCoinBase(), output.getScriptPubKey());
            provider.getBtcUTXOs().add(utxo);
        }
    }


    /*
      Removes the outputs spent by btcTx inputs from the UTXO list
     */
    private void removeUsedUTXOs(BtcTransaction btcTx) throws IOException {
        for (TransactionInput transactionInput : btcTx.getInputs()) {
            Iterator<UTXO> iter = provider.getBtcUTXOs().iterator();
            while (iter.hasNext()) {
                UTXO utxo = iter.next();
                if (utxo.getHash().equals(transactionInput.getOutpoint().getHash()) && utxo.getIndex() == transactionInput.getOutpoint().getIndex()) {
                    iter.remove();
                    break;
                }
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
    public void releaseBtc(org.ethereum.core.Transaction rskTx) throws IOException {
        byte[] senderCode = rskRepository.getCode(rskTx.getSender());

        if (senderCode != null && senderCode.length > 0) {
            logger.warn("Contract {} tried to release funds. Release is just allowed from standard accounts.", Hex.toHexString(rskTx.getSender()));
            return;
        }

        Context.propagate(btcContext);
        NetworkParameters btcParams = bridgeConstants.getBtcParams();
        Address btcDestinationAddress = BridgeUtils.recoverBtcAddressFromEthTransaction(rskTx, btcParams);
        BigInteger valueInWeis = toBI(rskTx.getValue());
        // Track tx in the contract memory as "waiting for 100 rsk blocks in order to release BTC
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        Coin value = Coin.valueOf(Denomination.weisToSatoshis(valueInWeis).longValue());
        btcTx.addOutput(value, btcDestinationAddress);
        TransactionOutput btcTxOutput = btcTx.getOutput(0);

        if (btcTxOutput.getValue().isGreaterThan(bridgeConstants.getMinimumReleaseTxValue())) {
            provider.getRskTxsWaitingForConfirmations().put(new Sha3Hash(rskTx.getHash()), btcTx);
            logger.info("releaseBtc succesful to {}. Tx {}. Value {}.", btcDestinationAddress, rskTx, value);
        } else {
            logger.warn("releaseBtc ignored because value is considered dust. To {}. Tx {}. Value {}.", btcDestinationAddress, rskTx, value);
        }
    }

    /**
     * Executed every now and then.
     * Iterates rskTxsWaitingForConfirmations map searching for txs with enough confirmations in the RSK blockchain.
     * If it finds one, adds unsigned inputs and change output and moves it to rskTxsWaitingForSignatures map.
     * @throws IOException
     */
    public void updateCollections() throws IOException {
        Context.propagate(btcContext);
        Iterator<Map.Entry<Sha3Hash, BtcTransaction>> iter = provider.getRskTxsWaitingForConfirmations().entrySet().iterator();
        while (iter.hasNext() && provider.getRskTxsWaitingForSignatures().size() == 0) {
            Map.Entry<Sha3Hash, BtcTransaction> entry = iter.next();
            // We copy the entry's key and value to temporary variables because iter.remove() changes the values of entry's key and value
            Sha3Hash rskTxHash = entry.getKey();
            if (hasEnoughConfirmations(rskTxHash)) {
                // We create a new Transaction instance because if ExceededMaxTransactionSize is thrown, the tx included in the SendRequest
                // is altered and then we would try to persist the altered version in the waiting for confirmations collection
                // Inputs would not be disconnected, so we would have a second problem trying to persist non java serializable objects.
                BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams(), entry.getValue().bitcoinSerialize());
                try {
                    SendRequest sr = SendRequest.forTx(btcTx);
                    sr.feePerKb = Coin.MILLICOIN;
                    sr.missingSigsMode = Wallet.MissingSigsMode.USE_OP_ZERO;
                    sr.changeAddress = bridgeConstants.getFederationAddress();
                    sr.shuffleOutputs = false;
                    sr.recipientsPayFees = true;
                    provider.getWallet().completeTx(sr);
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

        Iterator<Map.Entry<Sha3Hash, Pair<BtcTransaction, Long>>> iter2 = provider.getRskTxsWaitingForBroadcasting().entrySet().iterator();
        while (iter2.hasNext()) {
            Map.Entry<Sha3Hash, Pair<BtcTransaction, Long>> entry = iter2.next();
            if (hadEnoughTimeToBroadcast(entry.getKey())) {
                iter2.remove();
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
            byte[] burnAddress = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBurnAddress();
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
     * Return true if enough blocks has passed, so federators had for sure enough time to broadcast it to the btc network
     */
    private boolean hadEnoughTimeToBroadcast(Sha3Hash rskTxHash) throws IOException {
        long bestBlockNumber = rskExecutionBlock.getNumber();
        long broadcastingInclusionBlock = provider.getRskTxsWaitingForBroadcasting().get(rskTxHash).getRight();
        return (bestBlockNumber - broadcastingInclusionBlock) > bridgeConstants.getBtcBroadcastingMinimumAcceptableBlocks();
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
        if (!bridgeConstants.getFederatorPublicKeys().contains(federatorPublicKey)) {
            logger.warn("Supplied federatory public key {} does not belong to any of the federators.", federatorPublicKey);
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
                int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
                inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex, 1, 1);
                txIn.setScriptSig(inputScript);
                logger.debug("Tx input for tx {} signed.", new Sha3Hash(rskTxHash));
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
            provider.getRskTxsWaitingForBroadcasting().put(new Sha3Hash(rskTxHash), new ImmutablePair(btcTx, executionBlockNumber));
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
        StateForFederator stateForFederator = new StateForFederator(provider.getRskTxsWaitingForSignatures(), provider.getRskTxsWaitingForBroadcasting());
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
     * Returns an set of btc txs hashes the bridge already knows about.
     * @return a Set of tx hashes.
     * @throws IOException
     */
    public Set<Sha256Hash> getBtcTxHashesAlreadyProcessed() throws IOException {
        return provider.getBtcTxHashesAlreadyProcessed();
    }

    /**
     * Returns the federation bitcoin address.
     * @return the federation bitcoin address.
     */
    public Address getFederationAddress() {
        return bridgeConstants.getFederationAddress();
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
        if(checkpoints == null) {
            BtcBlock genesis = bridgeConstants.getBtcParams().getGenesisBlock();
            return new StoredBlock(genesis, genesis.getWork(), 0);
        }
        CheckpointManager manager = new CheckpointManager(bridgeConstants.getBtcParams(), checkpoints);
        long time = bridgeConstants.getFederationAddressCreationTime();
        // Go back 1 week to match CheckpointManager.checkpoint() behaviour
        time -= 86400 * 7;
        return manager.getCheckpointBefore(time);
    }

    @VisibleForTesting
    BtcBlockStore getBtcBlockStore() {
        return btcBlockStore;
    }


}


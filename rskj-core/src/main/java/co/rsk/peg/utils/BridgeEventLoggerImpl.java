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
package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.*;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.pegin.RejectedPeginReason;
import java.util.List;
import java.util.function.Function;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.*;

/**
 * Responsible for logging events triggered by BridgeContract.
 *
 * @author martin.medina
 */
public class BridgeEventLoggerImpl implements BridgeEventLogger {
    private final BridgeConstants bridgeConstants;
    private final List<LogInfo> logs;
    private final ActivationConfig.ForBlock activations;

    public BridgeEventLoggerImpl(BridgeConstants bridgeConstants, ActivationConfig.ForBlock activations, List<LogInfo> logs) {
        this.activations = activations;
        this.bridgeConstants = bridgeConstants;
        this.logs = logs;
    }

    @Override
    public void logUpdateCollections(RskAddress sender) {
        CallTransaction.Function event = BridgeEvents.UPDATE_COLLECTIONS.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics();
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(sender.toHexString());

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logAddSignature(FederationMember federationMember, BtcTransaction btcTx, byte[] rskTxHash) {
        ECKey federatorRskPublicKey = getFederatorRskPublicKey(federationMember);
        String federatorRskAddress = ByteUtil.toHexString(federatorRskPublicKey.getAddress());
        BtcECKey federatorBtcPublicKey = federationMember.getBtcPublicKey();

        logAddSignatureInSolidityFormat(rskTxHash, federatorRskAddress, federatorBtcPublicKey);
    }

    private ECKey getFederatorRskPublicKey(FederationMember federationMember) {
        if (!shouldUseRskPublicKey()) {
            return ECKey.fromPublicOnly(federationMember.getBtcPublicKey().getPubKey());
        }

        return federationMember.getRskPublicKey();
    }

    private boolean shouldUseRskPublicKey() {
        return activations.isActive(ConsensusRule.RSKIP415);
    }

    private void logAddSignatureInSolidityFormat(byte[] rskTxHash, String federatorRskAddress, BtcECKey federatorPublicKey) {
        CallTransaction.Function event = BridgeEvents.ADD_SIGNATURE.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics(rskTxHash, federatorRskAddress);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] federatorPublicKeySerialized = federatorPublicKey.getPubKey();
        byte[] encodedData = event.encodeEventData(federatorPublicKeySerialized);

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logReleaseBtc(BtcTransaction btcTx, byte[] rskTxHash) {
        CallTransaction.Function event = BridgeEvents.RELEASE_BTC.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics(rskTxHash);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] rawBtcTxSerialized = btcTx.bitcoinSerialize();
        byte[] encodedData = event.encodeEventData(rawBtcTxSerialized);

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logCommitFederation(Block executionBlock, Federation oldFederation, Federation newFederation) {
        CallTransaction.Function event = BridgeEvents.COMMIT_FEDERATION.getEvent();

        // Convert old federation public keys in bytes array
        byte[] oldFederationFlatPubKeys = flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();

        byte[] newFederationFlatPubKeys = flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();

        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        long newFedActivationBlockNumber = executionBlock.getNumber() + federationConstants.getFederationActivationAge(activations);

        byte[][] encodedTopicsSerialized = event.encodeEventTopics();
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(
            oldFederationFlatPubKeys,
            oldFederationBtcAddress,
            newFederationFlatPubKeys,
            newFederationBtcAddress,
            newFedActivationBlockNumber
        );

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logCommitFederationFailure(Block executionBlock, Federation proposedFederation) {
        CallTransaction.Function event = BridgeEvents.COMMIT_FEDERATION_FAILED.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics();
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] proposedFederationRedeemScriptSerialized = proposedFederation.getRedeemScript().getProgram();
        long executionBlockNumber = executionBlock.getNumber();

        byte[] encodedData = event.encodeEventData(
            proposedFederationRedeemScriptSerialized,
            executionBlockNumber
        );

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logLockBtc(RskAddress receiver, BtcTransaction btcTx, Address senderBtcAddress, Coin amount) {
        CallTransaction.Function event = BridgeEvents.LOCK_BTC.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics(receiver.toString());
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] btcTxHashSerialized = btcTx.getHash().getBytes();
        byte[] encodedData = event.encodeEventData(
            btcTxHashSerialized,
            senderBtcAddress.toString(),
            amount.getValue()
        );

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logPeginBtc(RskAddress receiver, BtcTransaction btcTx, Coin amount, int protocolVersion) {
        CallTransaction.Function event = BridgeEvents.PEGIN_BTC.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics(receiver.toString(), btcTx.getHash().getBytes());
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(amount.getValue(), protocolVersion);

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logReleaseBtcRequested(byte[] rskTransactionHash, BtcTransaction btcTx, Coin amount) {
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUESTED.getEvent();

        byte[] btcTxHashSerialized = btcTx.getHash().getBytes();
        byte[][] encodedTopicsSerialized = event.encodeEventTopics(rskTransactionHash, btcTxHashSerialized);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(amount.getValue());

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logRejectedPegin(BtcTransaction btcTx, RejectedPeginReason reason) {
        CallTransaction.Function event = BridgeEvents.REJECTED_PEGIN.getEvent();

        byte[] btcTxHashSerialized = btcTx.getHash().getBytes();
        byte[][] encodedTopicsSerialized = event.encodeEventTopics(btcTxHashSerialized);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(reason.getValue());

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logNonRefundablePegin(BtcTransaction btcTx, NonRefundablePeginReason reason) {
        CallTransaction.Function event = BridgeEvents.UNREFUNDABLE_PEGIN.getEvent();

        byte[] btcTxHashSerialized = btcTx.getHash().getBytes();
        byte[][] encodedTopicsSerialized = event.encodeEventTopics(btcTxHashSerialized);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(reason.getValue());

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logReleaseBtcRequestReceived(RskAddress sender, Address btcDestinationAddress, co.rsk.core.Coin amountInWeis) {
        if (activations.isActive(ConsensusRule.RSKIP326)) {
            logReleaseBtcRequestReceived(sender.toHexString(), btcDestinationAddress.toString(), amountInWeis);
        } else {
            logReleaseBtcRequestReceived(sender.toHexString(), btcDestinationAddress.getHash160(), amountInWeis.toBitcoin());
        }
    }

    private void logReleaseBtcRequestReceived(String sender, byte[] btcDestinationAddress, Coin amountInSatoshis) {
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED_LEGACY.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics(sender);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(btcDestinationAddress, amountInSatoshis.getValue());

        addLog(encodedTopics, encodedData);
    }

    private void logReleaseBtcRequestReceived(String sender, String btcDestinationAddress, co.rsk.core.Coin amountInWeis) {
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_RECEIVED.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics(sender);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = activations.isActive(ConsensusRule.RSKIP427) ?
            event.encodeEventData(btcDestinationAddress, amountInWeis.asBigInteger()) :
            event.encodeEventData(btcDestinationAddress, amountInWeis.toBitcoin().getValue());

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logReleaseBtcRequestRejected(RskAddress sender, co.rsk.core.Coin amountInWeis, RejectedPegoutReason reason) {
        CallTransaction.Function event = BridgeEvents.RELEASE_REQUEST_REJECTED.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics(sender.toHexString());
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = activations.isActive(ConsensusRule.RSKIP427) ?
            event.encodeEventData(amountInWeis.asBigInteger(), reason.getValue()) :
            event.encodeEventData(amountInWeis.toBitcoin().getValue(), reason.getValue());

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logBatchPegoutCreated(Sha256Hash btcTxHash, List<Keccak256> rskTxHashes) {
        CallTransaction.Function event = BridgeEvents.BATCH_PEGOUT_CREATED.getEvent();

        byte[] btcTxHashSerialized = btcTxHash.getBytes();
        byte[][] encodedTopicsSerialized = event.encodeEventTopics(btcTxHashSerialized);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] serializedRskTxHashes = serializeRskTxHashes(rskTxHashes);
        byte[] encodedData = event.encodeEventData(serializedRskTxHashes);

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logPegoutConfirmed(Sha256Hash btcTxHash, long pegoutCreationRskBlockNumber) {
        CallTransaction.Function event = BridgeEvents.PEGOUT_CONFIRMED.getEvent();

        byte[] btcTxHashSerialized = btcTxHash.getBytes();
        byte[][] encodedTopicsSerialized = event.encodeEventTopics(btcTxHashSerialized);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(pegoutCreationRskBlockNumber);

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logPegoutTransactionCreated(Sha256Hash btcTxHash, List<Coin> outpointValues) {
        if (btcTxHash == null){
            throw new IllegalArgumentException("btcTxHash param cannot be null");
        }

        CallTransaction.Function event = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();

        byte[] btcTxHashSerialized = btcTxHash.getBytes();
        byte[][] encodedTopicsSerialized = event.encodeEventTopics(btcTxHashSerialized);
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] serializedOutpointValues = UtxoUtils.encodeOutpointValues(outpointValues);
        byte[] encodedData = event.encodeEventData(serializedOutpointValues);

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logUnionRbtcRequested(RskAddress requester, co.rsk.core.Coin amount) {
        if (requester == null || amount == null) {
            throw new IllegalArgumentException("Requester and amount cannot be null");
        }

        CallTransaction.Function event = BridgeEvents.UNION_RBTC_REQUESTED.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics(requester.toHexString());
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(amount.asBigInteger());

        addLog(encodedTopics, encodedData);
    }

    @Override
    public void logUnionRbtcReleased(RskAddress receiver, co.rsk.core.Coin amount) {
        if (receiver == null || amount == null) {
            throw new IllegalArgumentException("Receiver and amount cannot be null");
        }

        CallTransaction.Function event = BridgeEvents.UNION_RBTC_RELEASED.getEvent();

        byte[][] encodedTopicsSerialized = event.encodeEventTopics(receiver.toHexString());
        List<DataWord> encodedTopics = getEncodedTopics(encodedTopicsSerialized);

        byte[] encodedData = event.encodeEventData(amount.asBigInteger());

        addLog(encodedTopics, encodedData);
    }

    private byte[] flatKeys(List<BtcECKey> keys, Function<BtcECKey, byte[]> parser) {
        List<byte[]> pubKeys = keys.stream()
                .map(parser)
                .toList();
        int pubKeysLength = pubKeys.stream().mapToInt(key -> key.length).sum();

        byte[] flatPubKeys = new byte[pubKeysLength];
        int copyPos = 0;
        for (byte[] key : pubKeys) {
            System.arraycopy(key, 0, flatPubKeys, copyPos, key.length);
            copyPos += key.length;
        }

        return flatPubKeys;
    }

    private byte[] flatKeysAsByteArray(List<BtcECKey> keys) {
        return flatKeys(keys, BtcECKey::getPubKey);
    }

    private byte[] serializeRskTxHashes(List<Keccak256> rskTxHashes) {
        List<byte[]> rskTxHashesList = rskTxHashes.stream()
            .map(Keccak256::getBytes)
            .toList();
        int rskTxHashesLength = rskTxHashesList.stream().mapToInt(key -> key.length).sum();

        byte[] serializedRskTxHashes = new byte[rskTxHashesLength];
        int copyPos = 0;
        for (byte[] rskTxHash : rskTxHashesList) {
            System.arraycopy(rskTxHash, 0, serializedRskTxHashes, copyPos, rskTxHash.length);
            copyPos += rskTxHash.length;
        }

        return serializedRskTxHashes;
    }

    private List<DataWord> getEncodedTopics(byte[][] encodedTopicsSerialized) {
        return LogInfo.byteArrayToList(encodedTopicsSerialized);
    }

    private void addLog(List<DataWord> eventEncodedTopics, byte[] eventEncodedData) {
        RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
        LogInfo newLog = new LogInfo(bridgeContractAddress.getBytes(), eventEncodedTopics, eventEncodedData);

        this.logs.add(newLog);
    }
}

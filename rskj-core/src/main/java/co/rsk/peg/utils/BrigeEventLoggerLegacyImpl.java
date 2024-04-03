/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.Bridge;
import co.rsk.peg.DeprecatedMethodCallException;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Responsible for logging events triggered by BridgeContract in RLP format.
 *
 * @author kelvin.isievwore
 * @deprecated Methods included in this class are to be used only prior to RSKIP146 activation
 */
@Deprecated
public class BrigeEventLoggerLegacyImpl implements BridgeEventLogger {

    private static final byte[] BRIDGE_CONTRACT_ADDRESS = PrecompiledContracts.BRIDGE_ADDR.getBytes();

    private final BridgeConstants bridgeConstants;
    private final ActivationConfig.ForBlock activations;
    private final SignatureCache signatureCache;
    private final List<LogInfo> logs;

    public BrigeEventLoggerLegacyImpl(BridgeConstants bridgeConstants, ActivationConfig.ForBlock activations, List<LogInfo> logs, SignatureCache signatureCache) {
        this.bridgeConstants = bridgeConstants;
        this.activations = activations;
        this.signatureCache = signatureCache;
        this.logs = logs;
    }

    @Override
    public void logUpdateCollections(Transaction rskTx) {
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            throw new DeprecatedMethodCallException(
                "Calling BrigeEventLoggerLegacyImpl.logUpdateCollections method after RSKIP146 activation"
            );
        }
        this.logs.add(
            new LogInfo(BRIDGE_CONTRACT_ADDRESS,
                Collections.singletonList(Bridge.UPDATE_COLLECTIONS_TOPIC),
                RLP.encodeElement(rskTx.getSender(signatureCache).getBytes())
            )
        );
    }

    @Override
    public void logAddSignature(FederationMember federationMember, BtcTransaction btcTx, byte[] rskTxHash) {
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            throw new DeprecatedMethodCallException(
                "Calling BrigeEventLoggerLegacyImpl.logAddSignature method after RSKIP146 activation"
            );
        }
        List<DataWord> topics = Collections.singletonList(Bridge.ADD_SIGNATURE_TOPIC);
        byte[] data = RLP.encodeList(RLP.encodeString(btcTx.getHashAsString()),
            RLP.encodeElement(federationMember.getBtcPublicKey().getPubKeyHash()),
            RLP.encodeElement(rskTxHash));

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, topics, data));
    }

    @Override
    public void logReleaseBtc(BtcTransaction btcTx, byte[] rskTxHash) {
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            throw new DeprecatedMethodCallException(
                "Calling BrigeEventLoggerLegacyImpl.logReleaseBtc method after RSKIP146 activation"
            );
        }
        List<DataWord> topics = Collections.singletonList(Bridge.RELEASE_BTC_TOPIC);
        byte[] data = RLP.encodeList(RLP.encodeString(btcTx.getHashAsString()), RLP.encodeElement(btcTx.bitcoinSerialize()));

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, topics, data));
    }

    @Override
    public void logCommitFederation(Block executionBlock, Federation oldFederation, Federation newFederation) {
        if (activations.isActive(ConsensusRule.RSKIP146)) {
            throw new DeprecatedMethodCallException(
                "Calling BrigeEventLoggerLegacyImpl.logCommitFederation method after RSKIP146 activation"
            );
        }
        List<DataWord> topics = Collections.singletonList(Bridge.COMMIT_FEDERATION_TOPIC);

        byte[] oldFedFlatPubKeys = flatKeysAsRlpCollection(oldFederation.getBtcPublicKeys());
        byte[] oldFedData = RLP.encodeList(RLP.encodeElement(oldFederation.getAddress().getHash160()), RLP.encodeList(oldFedFlatPubKeys));

        byte[] newFedFlatPubKeys = flatKeysAsRlpCollection(newFederation.getBtcPublicKeys());
        byte[] newFedData = RLP.encodeList(RLP.encodeElement(newFederation.getAddress().getHash160()), RLP.encodeList(newFedFlatPubKeys));

        long newFedActivationBlockNumber = executionBlock.getNumber() + this.bridgeConstants.getFederationActivationAge(activations);

        byte[] data = RLP.encodeList(oldFedData, newFedData, RLP.encodeString(Long.toString(newFedActivationBlockNumber)));

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, topics, data));
    }

    private byte[] flatKeysAsRlpCollection(List<BtcECKey> keys) {
        return flatKeys(keys, (k -> RLP.encodeElement(k.getPubKey())));
    }

    private byte[] flatKeys(List<BtcECKey> keys, Function<BtcECKey, byte[]> parser) {
        List<byte[]> pubKeys = keys.stream()
            .map(parser)
            .collect(Collectors.toList());
        int pubKeysLength = pubKeys.stream().mapToInt(key -> key.length).sum();

        byte[] flatPubKeys = new byte[pubKeysLength];
        int copyPos = 0;
        for (byte[] key : pubKeys) {
            System.arraycopy(key, 0, flatPubKeys, copyPos, key.length);
            copyPos += key.length;
        }

        return flatPubKeys;
    }
}

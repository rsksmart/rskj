/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.core;

import java.security.SignatureException;
import java.util.Map;

import co.rsk.crypto.Keccak256;
import co.rsk.panic.PanicProcessor;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SignatureCache {
    private static final Logger logger = LoggerFactory.getLogger(SignatureCache.class);
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final int MAX_BROADCAST_TX_SIZE = 6000;
    private static final int TX_IN_THREE_BLOCKS = 900;

    private final Map<ByteArrayWrapper, RskAddress> sendersByBroadcastTx;
    private final Map<ByteArrayWrapper, RskAddress> sendersByTxOnBlock;

    public SignatureCache(){
        sendersByBroadcastTx = new MaxSizeHashMap<>(MAX_BROADCAST_TX_SIZE,true);
        sendersByTxOnBlock = new MaxSizeHashMap<>(TX_IN_THREE_BLOCKS,false);
    }

    public RskAddress getSenderCacheInBlock(byte[] rawHashBytes, ECKey.ECDSASignature signature){
        RskAddress senderAddr;
        ByteArrayWrapper key = getKey(rawHashBytes,signature);
        if (sendersByTxOnBlock.containsKey(key)){
            senderAddr = sendersByTxOnBlock.get(key);
        } else if (sendersByBroadcastTx.containsKey(key)){
            senderAddr = sendersByBroadcastTx.get(key);
            txFromBroadcastToBlock(key, senderAddr);
        } else {
            senderAddr = computeTxSender(rawHashBytes, signature);
            if(!senderAddr.equals(RskAddress.nullAddress())) sendersByTxOnBlock.put(key,senderAddr);
        }
        return senderAddr;
    }

    public RskAddress getSenderCacheInBroadcastTx(byte[] rawHashBytes, ECKey.ECDSASignature signature){
        RskAddress senderAddr;
        ByteArrayWrapper key = getKey(rawHashBytes,signature);
        if (sendersByBroadcastTx.containsKey(key)){
            senderAddr = sendersByBroadcastTx.get(key);
        } else {
            senderAddr = computeTxSender(rawHashBytes, signature);
            sendersByBroadcastTx.put(key,senderAddr);
        }
        return senderAddr;
    }

    public boolean isInBroadcastTxCache(byte[] rawHashBytes, ECKey.ECDSASignature signature) {
        return sendersByBroadcastTx.containsKey(getKey(rawHashBytes,signature));
    }

    public boolean isInBlockTxCache(byte[] rawHashBytes, ECKey.ECDSASignature signature) {
        return sendersByTxOnBlock.containsKey(getKey(rawHashBytes,signature));
    }

    private RskAddress computeTxSender(byte[] rawHashBytes, ECKey.ECDSASignature signature) {
        RskAddress senderAddr;
        try {
            senderAddr = getTxSender(rawHashBytes, signature);
        } catch (SignatureException e) {
            logger.error(e.getMessage(), e);
            panicProcessor.panic("invalid_signature", String.format("Invalid signature %s, %s", signature, e.getMessage()));
            senderAddr = RskAddress.nullAddress();
        }
        return senderAddr;
    }


    private void txFromBroadcastToBlock(ByteArrayWrapper key, RskAddress addr) {
        sendersByBroadcastTx.remove(key);
        sendersByTxOnBlock.put(key,addr);
    }

    private RskAddress getTxSender(byte[] rawHashBytes, ECKey.ECDSASignature signature) throws SignatureException {
        ECKey senderKey = ECKey.signatureToKey(rawHashBytes, signature);
        RskAddress senderAddr = new RskAddress(senderKey.getAddress());
        return senderAddr;
    }

    private ByteArrayWrapper getKey(byte[] rawHashBytes, ECKey.ECDSASignature signature) {
        byte [] rsvArray = getRsvArray(signature);
        Keccak256 key = getSenderCacheKey(rawHashBytes, rsvArray);
        return new ByteArrayWrapper(key.getBytes());
    }

    private Keccak256 getSenderCacheKey(byte[] rawHashBytes, byte[] rsvArray) {
        return new Keccak256(HashUtil.keccak256(ByteUtil.merge(rawHashBytes, rsvArray)));
    }

    private byte[] getRsvArray(ECKey.ECDSASignature signature) {
        return ByteUtil.appendByte(ByteUtil.merge(signature.r.toByteArray(), signature.s.toByteArray()), signature.v);
    }
}

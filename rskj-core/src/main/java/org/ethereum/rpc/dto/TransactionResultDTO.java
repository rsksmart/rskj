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
package org.ethereum.rpc.dto;

import org.ethereum.core.Block;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionType;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import co.rsk.core.Coin;
import co.rsk.remasc.RemascTransaction;
import co.rsk.util.HexUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Ruben on 8/1/2016.
 *
 * Fields defined for legacy transactions (blockHash, blockNumber, transactionIndex, etc.) are always
 * serialized, including as JSON null, per the Ethereum JSON-RPC contract. Only the EIP-2718 / EIP-1559
 * typed-transaction fields below are annotated with {@link JsonInclude.Include#NON_NULL} so they are
 * omitted entirely for legacy transactions that do not have them.
 */
public class TransactionResultDTO {

    private static final Logger logger = LoggerFactory.getLogger(TransactionResultDTO.class);
    private static final String HEX_ZERO = "0x0";

    private String type;
    private String hash;
    private String nonce;
    private String blockHash;
    private String blockNumber;
    private String transactionIndex;
    private String from;
    private String to;
    private String gas;
    private String gasPrice;
    private String value;
    private String input;
    private String v;
    private String r;
    private String s;

    // Typed transaction fields (omitted from JSON for legacy transactions)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String chainId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<AccessListEntryDTO> accessList;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String yParity;

    // Type 2 only fields (omitted from JSON for legacy and Type 1)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String maxPriorityFeePerGas;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String maxFeePerGas;

    public TransactionResultDTO(Block b, Integer index, Transaction tx, boolean zeroSignatureIfRemasc, SignatureCache signatureCache) {
        type = tx.getTypeAsHex();

        hash = tx.getHash().toJsonString();

        nonce = HexUtils.toQuantityJsonHex(tx.getNonce());

        blockHash = b != null ? b.getHashJsonString() : null;
        blockNumber = b != null ? HexUtils.toQuantityJsonHex(b.getNumber()) : null;
        transactionIndex = index != null ? HexUtils.toQuantityJsonHex(index) : null;

        from = tx.getSender(signatureCache).toJsonString();
        to = tx.getReceiveAddress().toJsonString();
        gas = HexUtils.toQuantityJsonHex(tx.getGasLimit());

        gasPrice = HexUtils.toQuantityJsonHex(tx.getGasPrice().getBytes());

        if (Coin.ZERO.equals(tx.getValue())) {
            value = "0x0";
        } else {
            value = HexUtils.toQuantityJsonHex(tx.getValue().getBytes());
        }

        input = HexUtils.toUnformattedJsonHex(tx.getData());

        boolean isRemasc = tx instanceof RemascTransaction;
        if (!isRemasc) {
            ECDSASignature signature = tx.getSignature();

            v = HexUtils.toQuantityJsonHex(tx.getEncodedV());

            r = HexUtils.toQuantityJsonHex(signature.getR());
            s = HexUtils.toQuantityJsonHex(signature.getS());
        } else if (zeroSignatureIfRemasc) {
            v = HEX_ZERO;
            r = HEX_ZERO;
            s = HEX_ZERO;
        }

        // Populate typed transaction fields
        TransactionType txType = tx.getType();
        boolean isType1OrStandardType2 = (txType == TransactionType.TYPE_1)
                || (txType == TransactionType.TYPE_2 && !tx.getTypePrefix().isRskNamespace());

        if (isType1OrStandardType2) {
            chainId = HexUtils.toQuantityJsonHex(tx.getChainId() & 0xFF);
            accessList = decodeAccessList(tx.getAccessListBytes());
            yParity = HexUtils.toQuantityJsonHex(tx.getEncodedV() & 0xFF);

            if (txType == TransactionType.TYPE_2) {
                Coin maxP = tx.getMaxPriorityFeePerGas();
                Coin maxF = tx.getMaxFeePerGas();
                if (maxP != null) {
                    maxPriorityFeePerGas = HexUtils.toQuantityJsonHex(maxP.getBytes());
                }
                if (maxF != null) {
                    maxFeePerGas = HexUtils.toQuantityJsonHex(maxF.getBytes());
                }
            }
        }
    }

    /**
     * Decodes the RLP-encoded access list bytes into a list of {@link AccessListEntryDTO} objects.
     * The access list RLP format is: {@code [[address, [storageKey, ...]], ...]}
     * where {@code address} is 20 bytes and each {@code storageKey} is 32 bytes.
     *
     * <p>Since the access-list RLP is already validated at transaction ingress
     * ({@code Transaction.validateAccessListRlp}), a decoding failure here indicates data corruption
     * or an encoder bug. We log at ERROR with full context so the incident is visible, and still
     * return an empty list to avoid breaking the RPC response for other clients.
     */
    private static List<AccessListEntryDTO> decodeAccessList(byte[] accessListBytes) {
        if (accessListBytes == null || accessListBytes.length == 0) {
            return Collections.emptyList();
        }
        try {
            RLPList outer = RLP.decodeList(accessListBytes);
            List<AccessListEntryDTO> result = new ArrayList<>(outer.size());
            for (int i = 0; i < outer.size(); i++) {
                RLPElement entryElem = outer.get(i);
                RLPList entry = RLP.decodeList(entryElem.getRLPRawData());

                byte[] addressBytes = entry.get(0).getRLPData();
                String address = HexUtils.toUnformattedJsonHex(addressBytes);

                RLPList keysList = RLP.decodeList(entry.get(1).getRLPRawData());
                List<String> storageKeys = new ArrayList<>(keysList.size());
                for (int k = 0; k < keysList.size(); k++) {
                    byte[] keyBytes = keysList.get(k).getRLPData();
                    storageKeys.add(HexUtils.toUnformattedJsonHex(keyBytes));
                }
                result.add(new AccessListEntryDTO(address, storageKeys));
            }
            return result;
        } catch (Exception e) {
            logger.error("Failed to decode access list bytes (length={}); returning empty list. This indicates stored access-list RLP is corrupt or was encoded incorrectly.",
                    accessListBytes.length, e);
            return Collections.emptyList();
        }
    }

    /**
     * Represents a single entry in an EIP-2930 / EIP-1559 access list.
     * Serializes as {@code {"address": "0x...", "storageKeys": ["0x...", ...]}} in JSON.
     */
    public static class AccessListEntryDTO {
        private final String address;
        private final List<String> storageKeys;

        public AccessListEntryDTO(String address, List<String> storageKeys) {
            this.address = address;
            this.storageKeys = Collections.unmodifiableList(storageKeys);
        }

        public String getAddress() {
            return address;
        }

        public List<String> getStorageKeys() {
            return storageKeys;
        }
    }

    public String getHash() {
        return hash;
    }

    public String getNonce() {
        return nonce;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public String getBlockNumber() {
        return blockNumber;
    }

    public String getTransactionIndex() {
        return transactionIndex;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getGas() {
        return gas;
    }

    public String getGasPrice() {
        return gasPrice;
    }

    public String getValue() {
        return value;
    }

    public String getInput() {
        return input;
    }

    public String getV() {
        return v;
    }

    public String getR() {
        return r;
    }

    public String getS() {
        return s;
    }

    public String getType() {
        return type;
    }

    public String getChainId() {
        return chainId;
    }

    public List<AccessListEntryDTO> getAccessList() {
        return accessList;
    }

    public String getYParity() {
        return yParity;
    }

    public String getMaxPriorityFeePerGas() {
        return maxPriorityFeePerGas;
    }

    public String getMaxFeePerGas() {
        return maxFeePerGas;
    }
}

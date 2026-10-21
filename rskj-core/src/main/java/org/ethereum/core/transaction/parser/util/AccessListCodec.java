/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core.transaction.parser.util;

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.ethereum.core.transaction.parser.util.CommonParsingUtils.reencodeCanonical;
import static org.ethereum.core.transaction.parser.util.CommonParsingUtils.requireByteStringFramed;
import static org.ethereum.core.transaction.parser.util.CommonParsingUtils.requireListFramed;

public final class AccessListCodec {

    private AccessListCodec() {}

    /** Canonical RLP encoding of an empty list (RSKIP-546 access list field when absent). */
    private static final byte[] EMPTY_ACCESS_LIST_RLP = new byte[]{(byte) 0xc0};

    /**
     * Validates that the access list field contains well-formed RLP and normalizes a missing
     * value to the canonical empty-list encoding. Structured ingress only; raw ingress uses
     * {@link #requireRawAccessListBytes}.
     *
     * <p>Per RSKIP-546, Type 1 and standard Type 2 transactions reserve an access-list slot in
     * their RLP layout. That slot must always be a (possibly empty) RLP list — the canonical
     * encoding of which is the single byte {@code 0xc0}.
     */
    public static byte[] defaultAccessListBytes(byte[] accessListBytes) {
        if (accessListBytes == null || accessListBytes.length == 0) {
            return EMPTY_ACCESS_LIST_RLP.clone();
        }
        rethrowingMalformedRlp(() -> {
            RLPElement decoded = RLP.decode2(accessListBytes).get(0);
            if (!(decoded instanceof RLPList accessList)) {
                throw new IllegalArgumentException("Access list must be an RLP list");
            }
            validateAccessListEntries(accessList);
            if (!Arrays.equals(accessListBytes, reencodeCanonical(accessList))) {
                throw new IllegalArgumentException("Access list is not canonically encoded");
            }
        });
        return accessListBytes;
    }

    /** Raw-ingress form of {@link #defaultAccessListBytes}; canonical framing is left to the envelope check. */
    public static byte[] requireRawAccessListBytes(RLPElement accessListField) {
        requireListFramed(accessListField, "Access list");
        rethrowingMalformedRlp(() -> validateAccessListEntries((RLPList) accessListField));
        return accessListField.getRLPRawData();
    }

    /** Runs a check, reporting a decoder failure as malformed access-list RLP. */
    private static void rethrowingMalformedRlp(Runnable check) {
        try {
            check.run();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Access list contains invalid RLP encoding", e);
        }
    }

    /** Checks each {@code [address, [storageKey, ...]]} entry while its elements are still typed. */
    private static void validateAccessListEntries(RLPList accessList) {
        for (int i = 0; i < accessList.size(); i++) {
            RLPElement entryElement = accessList.get(i);
            requireListFramed(entryElement, "Access list entry at index " + i);
            RLPList entry = (RLPList) entryElement;
            if (entry.size() != 2) {
                throw new IllegalArgumentException("Access list entry at index " + i + " must have exactly 2 elements");
            }

            requireByteStringFramed(entry.get(0), "Access list entry address at index " + i);
            byte[] addressData = entry.get(0).getRLPData();
            if (addressData == null || addressData.length != RskAddress.LENGTH_IN_BYTES) {
                throw new IllegalArgumentException(
                        "Access list entry address at index " + i + " must be exactly 20 bytes");
            }

            requireListFramed(entry.get(1), "Access list storage keys at index " + i);
            RLPList storageKeys = (RLPList) entry.get(1);
            for (int k = 0; k < storageKeys.size(); k++) {
                requireByteStringFramed(
                        storageKeys.get(k), "Access list storage key at entry " + i + ", key " + k);
                byte[] keyData = storageKeys.get(k).getRLPData();
                if (keyData == null || keyData.length != Transaction.DATAWORD_LENGTH) {
                    throw new IllegalArgumentException(
                            "Access list storage key at entry " + i + ", key " + k + " must be exactly 32 bytes");
                }
            }
        }
    }

    /**
     * Encodes an access list (from JSON-RPC call arguments) to RLP bytes.
     * The resulting format is {@code rlp([[address, [storageKey, ...]], ...])} per EIP-2930.
     * Returns {@code null} if the access list is null or empty (no access list field).
     */
    public static byte[] encodeAccessList(List<CallArguments.AccessListEntry> accessList) {
        if (accessList == null || accessList.isEmpty()) {
            return null;
        }
        byte[][] encodedEntries = new byte[accessList.size()][];
        for (int i = 0; i < accessList.size(); i++) {
            CallArguments.AccessListEntry entry = accessList.get(i);
            if (entry.getAddress() == null) {
                throw RskJsonRpcRequestException.invalidParamError("Access list entry missing address at index " + i);
            }
            byte[] addressBytes = HexUtils.stringHexToByteArray(entry.getAddress());
            if (addressBytes == null || addressBytes.length != 20) {
                throw RskJsonRpcRequestException.invalidParamError(
                        "Access list entry address must be a 20-byte hex value at index " + i);
            }
            byte[] encodedAddress = RLP.encodeElement(addressBytes);

            List<String> storageKeys = entry.getStorageKeys() != null ? entry.getStorageKeys() : Collections.emptyList();
            byte[][] encodedKeys = new byte[storageKeys.size()][];
            for (int k = 0; k < storageKeys.size(); k++) {
                byte[] keyBytes = HexUtils.stringHexToByteArray(storageKeys.get(k));
                if (keyBytes == null || keyBytes.length != 32) {
                    throw RskJsonRpcRequestException.invalidParamError(
                            "Access list storage key must be a 32-byte hex value at entry " + i + ", key " + k);
                }
                encodedKeys[k] = RLP.encodeElement(keyBytes);
            }
            byte[] encodedKeyList = RLP.encodeList(encodedKeys);
            encodedEntries[i] = RLP.encodeList(encodedAddress, encodedKeyList);
        }
        return RLP.encodeList(encodedEntries);
    }
}

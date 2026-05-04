package org.ethereum.core.transaction.parser.util;

import co.rsk.util.HexUtils;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;

import java.util.Collections;
import java.util.List;

public final class AccessListCodec {

    private AccessListCodec() {}

    /** Canonical RLP encoding of an empty list (RSKIP-546 access list field when absent). */
    private static final byte[] EMPTY_ACCESS_LIST_RLP = new byte[]{(byte) 0xc0};

    /**
     * Validates that the access list field contains well-formed RLP and normalizes a missing
     * value to the canonical empty-list encoding.
     *
     * <p>Per RSKIP-546, Type 1 and standard Type 2 transactions reserve an access-list slot in
     * their RLP layout. That slot must always be a (possibly empty) RLP list — the canonical
     * encoding of which is the single byte {@code 0xc0}.
     */
    public static byte[] defaultAccessListBytes(byte[] accessListBytes) {
        if (accessListBytes == null || accessListBytes.length == 0) {
            return EMPTY_ACCESS_LIST_RLP.clone();
        }
        try {
            RLP.decode2(accessListBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Access list contains invalid RLP encoding", e);
        }
        return accessListBytes;
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

package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;


public abstract class AbstractRawTransactionTypeParser<T extends ParsedRawTransaction> implements RawTransactionTypeParser<T> {

    public static final String ERR_INVALID_CHAIN_ID = "Invalid chainId: ";

    /**
     * Since EIP-155, we could encode chainId in V
     */
    public static final byte CHAIN_ID_INC = 35;
    public static final byte LOWER_REAL_V = 27;


    /**
     * Parses the chain ID for typed transactions (Type 1 / Type 2).
     * Per EIP-2718, the chain ID must be a canonical integer that fits in a single unsigned byte
     * (values 1–255). A zero chain ID is rejected: typed transactions require a chain ID.
     * Values larger than 255 are rejected to prevent cross-chain replay via silent truncation.
     */
    protected byte parseTypedTxChainId(byte[] chainIdData) {
        if (chainIdData == null || chainIdData.length == 0) {
            throw new IllegalArgumentException("Typed transaction chainId must not be zero or absent");
        }
        BigInteger chainIdValue = new BigInteger(1, chainIdData);
        if (chainIdValue.signum() == 0) {
            throw new IllegalArgumentException("Typed transaction chainId must not be zero");
        }
        if (chainIdValue.compareTo(BigInteger.valueOf(255)) > 0) {
            throw new IllegalArgumentException("Typed transaction chainId exceeds maximum supported value of 255, got: " + chainIdValue);
        }
        return chainIdValue.byteValue();
    }

    /**
     * Validates that yParity is strictly 0 or 1 as required by EIP-2930 and EIP-1559.
     * Silently masking with {@code & 1} would allow malformed transactions (e.g. yParity=5)
     * to be accepted, which deviates from the spec and could indicate signature manipulation.
     */
    // yParity=0 is RLP-encoded as 0x80, so getRLPData() returns null; treat null as 0
    protected byte parseTypedYParity(byte[] yParityData) {
        byte yParity = (yParityData != null && yParityData.length > 0) ? yParityData[0] : 0;
        if (yParity != 0 && yParity != 1) {
            throw new IllegalArgumentException("Typed transaction yParity must be 0 or 1, got: " + (yParity & 0xFF));
        }
        return yParity;
    }

    /**
     * Validates that the access list field contains well-formed RLP.
     * Rootstock does not interpret access list contents, but an unparseable blob would be stored
     * on-chain and could cause issues in downstream tooling. A null or empty-list (0xc0) value
     * is always valid.
     */
    protected void validateAccessListRlp(byte[] accessListBytes) {
        if (accessListBytes == null || accessListBytes.length == 0) {
            return;
        }
        try {
            RLP.decode2(accessListBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Access list contains invalid RLP encoding", e);
        }
    }

    protected byte extractChainIdFromV(byte v) {
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return 0;
        }
        return (byte) (((0x00FF & v) - CHAIN_ID_INC) / 2);
    }

    //validate
    protected byte getRealV(byte v) {
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return v;
        }
        byte realV = LOWER_REAL_V;
        int inc = 0;
        if ((int) v % 2 == 0) {
            inc = 1;
        }
        return (byte) (realV + inc);
    }

    protected byte[] nullToEmpty(byte[] value) {
        return value == null ? new byte[0] : value;
    }


    protected Coin defaultValue(Coin value) {
        return value == null ? Coin.ZERO : value;
    }

    protected RskAddress defaultAddress(RskAddress receiveAddress) {
        return receiveAddress == null ? RskAddress.nullAddress() : receiveAddress;
    }

    protected void requireFieldCount(RLPList txFields, int expected, String typeName) {
        if (txFields.size() != expected) {
            throw new IllegalArgumentException(typeName + " transaction must have exactly " + expected + " elements");
        }
    }

    protected static Coin strHexOrStrNumberToBigInteger(String value) {
        if (value == null || value.isEmpty()) {
            return Coin.ZERO;
        }
        return new Coin(HexUtils.strHexOrStrNumberToBigInteger(value));

    }

    protected static BigInteger strHexOrStrNumberToBigInteger(String value, Supplier<BigInteger> getDefaultValue) {
        return Optional.ofNullable(value).map(HexUtils::strHexOrStrNumberToBigInteger).orElseGet(getDefaultValue);
    }

    protected static byte hexToChainId(String hex, byte defaultChainId) {
        if (hex == null) {
            return defaultChainId;
        }
        try {
            byte[] bytes = HexUtils.strHexOrStrNumberToByteArray(hex);
            if (bytes.length != 1) {
                throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex);
            }

            return bytes[0] == 0 ? defaultChainId : bytes[0];
        } catch (Exception e) {
            throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex, e);
        }
    }

    protected static byte[] stringHexToByteArray(String value) {
        return Optional.ofNullable(value).map(HexUtils::stringHexToByteArray).orElse(null);
    }

    /**
     * Encodes an access list (from JSON-RPC call arguments) to RLP bytes.
     * The resulting format is {@code rlp([[address, [storageKey, ...]], ...])} per EIP-2930.
     * Returns {@code null} if the access list is null or empty (no access list field).
     */
    static byte[] encodeAccessList(List<CallArguments.AccessListEntry> accessList) {
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

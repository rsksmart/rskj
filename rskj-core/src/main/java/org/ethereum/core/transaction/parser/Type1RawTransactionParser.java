package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionType;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

/** Type 1 (EIP-2930): 11 elements */
public class Type1RawTransactionParser extends AbstractRawTransactionTypeParser<ParsedType1Transaction> {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);

    private static final Logger logger = LoggerFactory.getLogger(Type1RawTransactionParser.class);

    private static final int TYPE_1_FIELD_COUNT = 11;

    @Override
    public ParsedType1Transaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
        requireFieldCount(txFields, TYPE_1_FIELD_COUNT, "Type 1");
        byte chainId = parseTypedTxChainId(txFields.get(0).getRLPData());
        byte[] nonce = txFields.get(1).getRLPData();
        Coin gasPrice = RLP.parseCoinNonNullZero(txFields.get(2).getRLPData());
        byte[] gasLimit = txFields.get(3).getRLPData();
        RskAddress receiveAddress = RLP.parseRskAddress(txFields.get(4).getRLPData());
        Coin value = RLP.parseCoinNullZero(txFields.get(5).getRLPData());
        byte[] data = nullToEmpty(txFields.get(6).getRLPData());
        byte[] accessListBytes = txFields.get(7).getRLPRawData();
        validateAccessListRlp(accessListBytes);

        byte yParity = parseTypedYParity(txFields.get(8).getRLPData());
        byte v = (byte) (LOWER_REAL_V + yParity);

        byte[] r = txFields.get(9).getRLPData();
        byte[] s = txFields.get(10).getRLPData();
        ECDSASignature signature = (r != null || s != null) ? ECDSASignature.fromComponents(r, s, v) : null;

        return new ParsedType1Transaction(
                typePrefix,
                nullToEmpty(nonce),
                gasPrice == null ? Coin.ZERO : gasPrice,
                nullToEmpty(gasLimit),
                defaultAddress(receiveAddress),
                defaultValue(value),
                data,
                chainId,
                signature,
                accessListBytes == null ? new byte[0] : accessListBytes
        );
    }

    @Override
    public void validate(long bestBlock, ActivationConfig activationConfig, Constants constants) {
        ActivationConfig.ForBlock activations = activationConfig.forBlock(bestBlock);
        if (!activations.isActive(ConsensusRule.RSKIP543)) {
            throw invalidParamError("Typed transactions (type " + TransactionType.TYPE_1 + ") is not supported before RSKIP-543 activation");
        }
        if (!activations.isActive(ConsensusRule.RSKIP546)) {
            throw invalidParamError("Type 1 / Type 2 transactions are not supported before RSKIP-546 activation");
        }
    }

    @Override
    public ParsedType1Transaction parse(TransactionTypePrefix typePrefix, CallArguments argsParam) {
        Coin gasPrice = strHexOrStrNumberToBigInteger(argsParam.getGasPrice());
        BigInteger gasLimit = strHexOrStrNumberToBigInteger
                (argsParam.getGasLimit(), () ->   strHexOrStrNumberToBigInteger(
                        argsParam.getGasLimit(), () -> DEFAULT_GAS_LIMIT
                ));
        Coin value =  strHexOrStrNumberToBigInteger(argsParam.getValue());
        RskAddress receiveAddress = new RskAddress( stringHexToByteArray(argsParam.getTo()));

        String data  = argsParam.getData();
        if (data != null && data.startsWith("0x")) {
            data = argsParam.getData().substring(2);
        }

        BigInteger nonce = Optional.ofNullable(argsParam.getNonce())
                .map(HexUtils::strHexOrStrNumberToBigInteger)
                .orElse(null);

        byte [] accessListBytes = encodeAccessList(argsParam.getAccessList());
        return new ParsedType1Transaction(
                typePrefix,
                nonce.toByteArray(),
                gasPrice == null ? Coin.ZERO : gasPrice,
                gasLimit.toByteArray(),
                defaultAddress(receiveAddress),
                defaultValue(value),
                data.getBytes(),
                hexToChainId(argsParam.getChainId(), (byte) 0),
                null,
                accessListBytes == null ? new byte[0] : accessListBytes
        );
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

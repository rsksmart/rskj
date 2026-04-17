package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;

import javax.annotation.Nullable;

/**
 * Temporary adapter to bridge
 * ParsedRawTransaction -> Transaction constructor arguments.
 *
 * This isolates all subtype logic outside Transaction.
 */
public final class ParsedRawTransactionAdapter {

    private ParsedRawTransactionAdapter() {
    }

    public static ConstructorArguments from(ParsedRawTransaction parsed) {
        return new ConstructorArguments(
                parsed.nonce(),
                gasPrice(parsed),
                parsed.gasLimit(),
                parsed.receiveAddress(),
                parsed.value(),
                parsed.data(),
                parsed.chainId(),
                false,
                parsed.typePrefix(),
                accessListBytes(parsed),
                maxPriorityFeePerGas(parsed),
                maxFeePerGas(parsed),
                parsed.signature()
        );
    }


    private static Coin gasPrice(ParsedRawTransaction parsed) {
        if (parsed instanceof ParsedType0Transaction tx) {
            return tx.gasPrice();
        }
        if (parsed instanceof ParsedRskNamespaceType2Transaction tx) {
            return tx.gasPrice();
        }
        if (parsed instanceof ParsedType1Transaction tx) {
            return tx.gasPrice();
        }
        if (parsed instanceof ParsedType2Transaction tx) {
            return tx.effectiveGasPrice();
        }

        throw new IllegalArgumentException("Unsupported parsed type: " + parsed.getClass().getName());
    }

    @Nullable
    private static byte[] accessListBytes(ParsedRawTransaction parsed) {
        if (parsed instanceof ParsedType1Transaction tx) {
            return tx.accessListBytes();
        }
        if (parsed instanceof ParsedType2Transaction tx) {
            return tx.accessListBytes();
        }
        return null;
    }

    @Nullable
    private static Coin maxPriorityFeePerGas(ParsedRawTransaction parsed) {
        if (parsed instanceof ParsedType2Transaction tx) {
            return tx.maxPriorityFeePerGas();
        }
        return null;
    }

    @Nullable
    private static Coin maxFeePerGas(ParsedRawTransaction parsed) {
        if (parsed instanceof ParsedType2Transaction tx) {
            return tx.maxFeePerGas();
        }
        return null;
    }

    // Temp DTO
    public record ConstructorArguments(
            byte[] nonce,
            Coin gasPrice,
            byte[] gasLimit,
            RskAddress receiveAddress,
            Coin value,
            @Nullable byte[] data,
            byte chainId,
            boolean localCall,
            TransactionTypePrefix typePrefix,
            @Nullable byte[] accessListBytes,
            @Nullable Coin maxPriorityFeePerGas,
            @Nullable Coin maxFeePerGas,
            @Nullable ECDSASignature signature
    ) {
    }
}

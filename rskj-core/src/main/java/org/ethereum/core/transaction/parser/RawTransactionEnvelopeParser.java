package org.ethereum.core.transaction.parser;

import co.rsk.core.types.bytes.BytesSlice;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.function.Supplier;

public final class RawTransactionEnvelopeParser {

    private static final Type0RawTransactionParser type0Parser = new Type0RawTransactionParser();
    private static final Type1RawTransactionParser type1Parser = new Type1RawTransactionParser();
    private static final Type2RawTransactionParser type2Parser = new Type2RawTransactionParser();

    public static final String ERR_INVALID_RSK_SUBTYPE = "Invalid RSK subtype: ";

    public static ParsedRawTransaction parse(byte[] rawData) {
        if (rawData == null || rawData.length == 0) {
            throw new IllegalArgumentException("Transaction raw data cannot be null or empty");
        }

        TransactionTypePrefix typePrefix = TransactionTypePrefix.fromRawData(rawData);
        BytesSlice payload = TransactionTypePrefix.stripPrefix(rawData, typePrefix);
        RLPList txFields = RLP.decodeList(payload);

        return resolveParser(typePrefix).parse(typePrefix, txFields);
    }

    public static ParsedRawTransaction parse(CallArguments argsParam, Supplier<String> nonceSupplier) {
        if (argsParam == null ) {
            throw new IllegalArgumentException("Transaction argsParam cannot be null or empty");
        }
        if (argsParam.getNonce()==null && nonceSupplier != null) {
            argsParam.setNonce(nonceSupplier.get());
        }
        TransactionTypePrefix typePrefix = TransactionTypePrefix.fromHex(argsParam.getType(), argsParam.getRskSubtype());
        return resolveParser(typePrefix).parse(typePrefix, argsParam);
    }

    private static RawTransactionTypeParser<? extends ParsedRawTransaction> resolveParser(TransactionTypePrefix typePrefix) {
        TransactionType type = typePrefix.type();
        return switch (type) {
            case LEGACY -> type0Parser;
            case TYPE_1 -> type1Parser;
            case TYPE_2 -> typePrefix.isRskNamespace() ? null : type2Parser;
            case TYPE_3, TYPE_4 -> null;
        };
    }
}

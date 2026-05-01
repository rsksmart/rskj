package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.util.AccessListCodec;
import org.ethereum.core.transaction.parser.util.CommonParsingUtils;
import org.ethereum.core.transaction.parser.util.TypedTransactionCodec;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class Type2RawTransactionParser implements RawTransactionTypeParser<ParsedType2Transaction>{

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);
    private static final int FIELD_COUNT = 12;

    private static final int CHAIN_ID_INDEX = 0;
    private static final int NONCE_INDEX = 1;
    private static final int MAX_PRIORITY_FEE_PER_GAS_INDEX = 2;
    private static final int MAX_FEE_PER_GAS_INDEX = 3;
    private static final int GAS_LIMIT_INDEX = 4;
    private static final int TO_INDEX = 5;
    private static final int VALUE_INDEX = 6;
    private static final int DATA_INDEX = 7;
    private static final int ACCESS_LIST_INDEX = 8;
    private static final int Y_PARITY_INDEX = 9;
    private static final int R_INDEX = 10;
    private static final int S_INDEX = 11;



    @Override
    public ParsedType2Transaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
        CommonParsingUtils.requireFieldCount(txFields, FIELD_COUNT, TransactionType.TYPE_2.getTypeName());

        byte[] nonce = CommonParsingUtils.nullToEmpty(txFields.get(NONCE_INDEX).getRLPData());
        byte[] gasLimit = CommonParsingUtils.nullToEmpty(txFields.get(GAS_LIMIT_INDEX).getRLPData());

        RskAddress receiveAddress = CommonParsingUtils.defaultAddress(RLP.parseRskAddress(txFields.get(TO_INDEX).getRLPData()));
        Coin value = CommonParsingUtils.defaultValue(RLP.parseCoinNullZero(txFields.get(VALUE_INDEX).getRLPData()));
        byte[] data = CommonParsingUtils.nullToEmpty(txFields.get(DATA_INDEX).getRLPData());
        byte[] accessListBytes = AccessListCodec.defaultAccessListBytes(txFields.get(ACCESS_LIST_INDEX).getRLPRawData());
        Coin maxPriorityFeePerGas = Objects.requireNonNull(RLP.parseCoinNonNullZero(txFields.get(MAX_PRIORITY_FEE_PER_GAS_INDEX).getRLPData()), "Type 2 maxPriorityFeePerGas");
        Coin maxFeePerGas = Objects.requireNonNull(RLP.parseCoinNonNullZero(txFields.get(MAX_FEE_PER_GAS_INDEX).getRLPData()), "Type 2 maxFeePerGas");

        validateFeeCapRelationship(maxPriorityFeePerGas, maxFeePerGas);


        return new ParsedType2Transaction(
                typePrefix,
                nonce,
                gasLimit,
                receiveAddress,
                value,
                data,
                TypedTransactionCodec.parseTypedSignatureState(txFields, CHAIN_ID_INDEX, Y_PARITY_INDEX, R_INDEX, S_INDEX),
                accessListBytes,
                maxPriorityFeePerGas,
                maxFeePerGas
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
    public ParsedType2Transaction parse(TransactionTypePrefix typePrefix, CallArguments argsParam, byte defaultChainId) {
        BigInteger nonce = Optional.ofNullable(argsParam.getNonce())
                .map(HexUtils::strHexOrStrNumberToBigInteger)
                .orElse(BigInteger.ZERO);

        BigInteger gasLimit = CommonParsingUtils.parseBigInteger(
                argsParam.getGasLimit(),
                () -> CommonParsingUtils.parseBigInteger(argsParam.getGasLimit(), () -> DEFAULT_GAS_LIMIT));
        Coin value = CommonParsingUtils.defaultValue(CommonParsingUtils.parseCoin(argsParam.getValue()));
        RskAddress receiveAddress = CommonParsingUtils.parseAddress(argsParam.getTo());
        byte[] data = CommonParsingUtils.parseHexData(argsParam.getData());
        byte[] accessListBytes = AccessListCodec.defaultAccessListBytes(AccessListCodec.encodeAccessList(argsParam.getAccessList()));
        byte chainId = TypedTransactionCodec.parseRequiredTypedChainId(argsParam.getChainId());
        Coin maxPriorityFeePerGas = parseRequiredCoin(
                argsParam.getMaxPriorityFeePerGas(),
                "Type 2 transaction requires maxPriorityFeePerGas"
        );
        Coin maxFeePerGas = parseRequiredCoin(
                argsParam.getMaxFeePerGas(),
                "Type 2 transaction requires maxFeePerGas"
        );

        validateFeeCapRelationship(maxPriorityFeePerGas, maxFeePerGas);

        return new ParsedType2Transaction(
                typePrefix,
                nonce.toByteArray(),
                gasLimit.toByteArray(),
                receiveAddress,
                value,
                data,
                new UnsignedSignature(chainId),
                accessListBytes,
                maxPriorityFeePerGas,
                maxFeePerGas
        );
    }

    private void validateFeeCapRelationship(Coin maxPriorityFeePerGas, Coin maxFeePerGas) {
        if (maxPriorityFeePerGas.compareTo(maxFeePerGas) > 0) {
            throw new IllegalArgumentException(
                    "Type 2 transaction maxPriorityFeePerGas (" + maxPriorityFeePerGas
                            + ") must not exceed maxFeePerGas (" + maxFeePerGas + ")"
            );
        }
    }

    private Coin parseRequiredCoin(String value, String errorMessage) {
        BigInteger parsed = CommonParsingUtils.parseBigInteger(value, () -> null);
        if (parsed == null) {
            throw invalidParamError(errorMessage);
        }
        return new Coin(parsed);
    }
}

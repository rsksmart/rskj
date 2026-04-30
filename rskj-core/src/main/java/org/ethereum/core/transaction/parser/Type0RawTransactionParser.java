package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.parser.util.CommonParsingUtils;
import org.ethereum.core.transaction.parser.util.Type0SignatureUtils;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;

import java.math.BigInteger;
import java.util.Optional;

public class Type0RawTransactionParser implements RawTransactionTypeParser<ParsedType0Transaction> {

    protected static final String ERR_INVALID_CHAIN_ID = "Invalid chainId: ";
    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);

    private static final int LEGACY_FIELD_COUNT = 9;

    private static final int NONCE_INDEX = 0;
    private static final int GAS_PRICE_INDEX = 1;
    private static final int GAS_LIMIT_INDEX = 2;
    private static final int RECEIVE_ADDRESS_INDEX = 3;
    private static final int VALUE_INDEX = 4;
    private static final int DATA_INDEX = 5;
    private static final int V_INDEX = 6;
    private static final int R_INDEX = 7;
    private static final int S_INDEX = 8;

    @Override
    public ParsedType0Transaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
        CommonParsingUtils.requireFieldCount(txFields, LEGACY_FIELD_COUNT, "Legacy-format");
         return new ParsedType0Transaction(
                typePrefix,
                CommonParsingUtils.nullToEmpty(txFields.get(NONCE_INDEX).getRLPData()),
                CommonParsingUtils.defaultValue(RLP.parseCoinNonNullZero(txFields.get(GAS_PRICE_INDEX).getRLPData())),
                CommonParsingUtils.nullToEmpty(txFields.get(GAS_LIMIT_INDEX).getRLPData()),
                CommonParsingUtils.defaultAddress(RLP.parseRskAddress(txFields.get(RECEIVE_ADDRESS_INDEX).getRLPData())),
                CommonParsingUtils.defaultValue(RLP.parseCoinNullZero(txFields.get(VALUE_INDEX).getRLPData())),
                CommonParsingUtils.nullToEmpty(txFields.get(DATA_INDEX).getRLPData()),
                Type0SignatureUtils.parseType0SignatureState(txFields, V_INDEX, R_INDEX, S_INDEX)
        );
    }

    @Override
    public void validate(long bestBlock, ActivationConfig activationConfig, Constants constants) {


    }

    @Override
    public ParsedType0Transaction parse(TransactionTypePrefix typePrefix, CallArguments argsParam, byte defaultChainId) {
        BigInteger nonce = Optional.ofNullable(argsParam.getNonce()).map(HexUtils::strHexOrStrNumberToBigInteger).orElse(null);
        BigInteger gasLimit = CommonParsingUtils.parseBigInteger(argsParam.getGas(), () -> DEFAULT_GAS_LIMIT);
        Coin gasPrice = CommonParsingUtils.defaultValue(CommonParsingUtils.parseCoin(argsParam.getGasPrice()));
        Coin value = CommonParsingUtils.defaultValue(CommonParsingUtils.parseCoin(argsParam.getValue()));
        RskAddress receiveAddress = CommonParsingUtils.parseAddress(argsParam.getTo());

        byte[] data = CommonParsingUtils.parseHexData(argsParam.getData());
        Byte chainId = hexToChainId(argsParam.getChainId(), defaultChainId);

        return new ParsedType0Transaction(
                typePrefix,
                nonce == null ? null : nonce.toByteArray(),
                gasPrice,
                gasLimit.toByteArray(),
                receiveAddress,
                value,
                data,
                new UnsignedSignature(chainId)
        );
    }





    private  byte hexToChainId(String hex, byte defaultChainId) {
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

}

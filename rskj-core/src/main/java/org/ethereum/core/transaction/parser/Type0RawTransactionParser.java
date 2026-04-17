package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.exception.TransactionException;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Optional;

public class Type0RawTransactionParser extends AbstractRawTransactionTypeParser<ParsedType0Transaction> {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);
    private static final Logger logger = LoggerFactory.getLogger(Type0RawTransactionParser.class);

    private static final int LEGACY_FIELD_COUNT = 9;

    @Override
    public ParsedType0Transaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
        requireFieldCount(txFields, LEGACY_FIELD_COUNT, "Legacy-format");
        byte[] nonce = txFields.get(0).getRLPData();
        Coin gasPrice = RLP.parseCoinNonNullZero(txFields.get(1).getRLPData());
        byte[] gasLimit = txFields.get(2).getRLPData();
        RskAddress receiveAddress = RLP.parseRskAddress(txFields.get(3).getRLPData());
        Coin value = RLP.parseCoinNullZero(txFields.get(4).getRLPData());
        //byte[] data = nullToEmpty(txFields.get(5).getRLPData()); //***** check this
        byte[] data = txFields.get(5).getRLPData(); //***** check this
        byte chainId = 0;
        ECDSASignature signature = null;
        byte[] vData = txFields.get(6).getRLPData();

        if (vData != null) {
            if (vData.length != 1) {
                throw new TransactionException("Signature V is invalid");
            }

            byte v = vData[0];
            chainId = extractChainIdFromV(v);
            byte[] r = txFields.get(7).getRLPData();
            byte[] s = txFields.get(8).getRLPData();
            signature = ECDSASignature.fromComponents(r, s, getRealV(v));
        } else {
            logger.trace("RLP encoded tx is not signed!");
        }
        return new ParsedType0Transaction(
                typePrefix,
                nullToEmpty(nonce),
                gasPrice == null ? Coin.ZERO : gasPrice,
                nullToEmpty(gasLimit),
                defaultAddress(receiveAddress),
                defaultValue(value),
                data,
                chainId,
                signature
        );
    }

    @Override
    public void validate(long bestBlock, ActivationConfig activationConfig, Constants constants) {


    }

    @Override
    public ParsedType0Transaction parse(TransactionTypePrefix typePrefix, CallArguments argsParam) {
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

        return new ParsedType0Transaction(
                typePrefix,
                nonce.toByteArray(),
                gasPrice == null ? Coin.ZERO : gasPrice,
                gasLimit.toByteArray(),
                defaultAddress(receiveAddress),
                value,
                data.getBytes(),
                hexToChainId(argsParam.getChainId(), (byte) 0),
                null
        );
    }


}

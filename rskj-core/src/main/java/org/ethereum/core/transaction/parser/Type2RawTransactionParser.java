package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.TransactionType;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.CallArguments;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

/** Parses Type 2 (EIP-1559) payload: 12 elements per RSKIP-546. */
public class Type2RawTransactionParser extends AbstractRawTransactionTypeParser<ParsedType2Transaction>{

    private static final int TYPE_2_FIELD_COUNT = 12;
    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);
    /*
    *    if (maxPriorityFeePerGas.compareTo(maxFeePerGas) > 0) {
            throw new IllegalArgumentException(
                    "Type 2 transaction maxPriorityFeePerGas (" + maxPriorityFeePerGas
                            + ") must not exceed maxFeePerGas (" + maxFeePerGas + ")");
        }
    * */


    @Override
    public ParsedType2Transaction parse(TransactionTypePrefix typePrefix, RLPList txFields) {
        requireFieldCount(txFields, TYPE_2_FIELD_COUNT, "Type 2");
        byte chainId = parseTypedTxChainId(txFields.get(0).getRLPData());
        byte[] nonce = txFields.get(1).getRLPData();

        Coin maxPriorityFeePerGas = Objects.requireNonNull(RLP.parseCoinNonNullZero(txFields.get(2).getRLPData()), "Type 2 maxPriorityFeePerGas");
        Coin maxFeePerGas = Objects.requireNonNull(RLP.parseCoinNonNullZero(txFields.get(3).getRLPData()), "Type 2 maxFeePerGas");

        byte[] gasLimit = txFields.get(4).getRLPData();
        RskAddress receiveAddress = RLP.parseRskAddress(txFields.get(5).getRLPData());
        Coin value = RLP.parseCoinNullZero(txFields.get(6).getRLPData());
        byte[] data = nullToEmpty(txFields.get(7).getRLPData());
        byte[] accessListBytes = txFields.get(8).getRLPRawData();
        validateAccessListRlp(accessListBytes);

        byte yParity = parseTypedYParity(txFields.get(9).getRLPData());
        byte v = (byte) (LOWER_REAL_V + yParity);

        byte[] r = txFields.get(10).getRLPData();
        byte[] s = txFields.get(11).getRLPData();
        ECDSASignature signature = (r != null || s != null) ? ECDSASignature.fromComponents(r, s, v) : null;

        return new ParsedType2Transaction(
                typePrefix,
                nullToEmpty(nonce),
                nullToEmpty(gasLimit),
                defaultAddress(receiveAddress),
                defaultValue(value),
                data,
                chainId,
                signature,
                accessListBytes == null ? new byte[0] : accessListBytes,
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
    public ParsedType2Transaction parse(TransactionTypePrefix typePrefix, CallArguments argsParam) {
        Coin gasPrice = strHexOrStrNumberToBigInteger(argsParam.getGasPrice());
        BigInteger gasLimit = strHexOrStrNumberToBigInteger
                (argsParam.getGasLimit(), () -> strHexOrStrNumberToBigInteger(
                        argsParam.getGasLimit(), () -> DEFAULT_GAS_LIMIT
                ));
        Coin value = strHexOrStrNumberToBigInteger(argsParam.getValue());
        RskAddress receiveAddress = new RskAddress(stringHexToByteArray(argsParam.getTo()));

        String data = argsParam.getData();
        if (data != null && data.startsWith("0x")) {
            data = argsParam.getData().substring(2);
        }

        BigInteger nonce = Optional.ofNullable(argsParam.getNonce())
                .map(HexUtils::strHexOrStrNumberToBigInteger)
                .orElse(null);

        byte[] accessListBytes = encodeAccessList(argsParam.getAccessList());

        return new ParsedType2Transaction(
                typePrefix,
                nonce.toByteArray(),
                gasLimit.toByteArray(),
                defaultAddress(receiveAddress),
                defaultValue(value),
                data.getBytes(),
                hexToChainId(argsParam.getChainId(), (byte) 0),
                null,
                accessListBytes == null ? new byte[0] : accessListBytes,
                new Coin(strHexOrStrNumberToBigInteger(argsParam.getMaxPriorityFeePerGas(), () -> null)),
                new Coin(strHexOrStrNumberToBigInteger(argsParam.getMaxFeePerGas(), () -> null))
        );
    }
}

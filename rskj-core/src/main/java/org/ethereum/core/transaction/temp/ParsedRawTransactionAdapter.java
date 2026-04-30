package org.ethereum.core.transaction.temp;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.parser.ParsedRawTransaction;
import org.ethereum.core.transaction.parser.ParsedType0Transaction;
import org.ethereum.core.transaction.parser.ParsedType1Transaction;
import org.ethereum.core.transaction.parser.ParsedType2RSKTransaction;
import org.ethereum.core.transaction.parser.ParsedType2Transaction;
import org.ethereum.core.transaction.parser.SignatureState;
import org.ethereum.core.transaction.parser.SignedSignature;
import org.ethereum.core.transaction.parser.UnsignedSignature;
import org.ethereum.crypto.signature.ECDSASignature;

public final class ParsedRawTransactionAdapter {

    private final ParsedRawTransaction tx;

    public ParsedRawTransactionAdapter(ParsedRawTransaction tx) {
        if (tx == null) {
            throw new IllegalArgumentException("ParsedRawTransaction cannot be null");
        }
        this.tx = tx;
    }

    public byte[] nonce() {
        return tx.nonce();
    }

    public byte[] gasLimit() {
        return tx.gasLimit();
    }

    public RskAddress receiveAddress() {
        return tx.receiveAddress();
    }

    public Coin value() {
        return tx.value();
    }

    public byte[] data() {
        return tx.data();
    }

    public TransactionTypePrefix typePrefix() {
        return tx.typePrefix();
    }

    public SignatureState signatureState() {
        return tx.signatureState();
    }

    public byte chainId() {
        SignatureState state = tx.signatureState();
        if (state instanceof SignedSignature signed) {
            return signed.chainId();
        }
        if (state instanceof UnsignedSignature unsigned) {
            return unsigned.chainId() == null ? 0 : unsigned.chainId();
        }
        return 0;
    }

    public ECDSASignature signature() {
        SignatureState state = tx.signatureState();
        return state instanceof SignedSignature signed ? signed.signature() : null;
    }

    public Coin effectiveGasPrice() {
        if (tx instanceof ParsedType2Transaction type2Tx) {
            return type2Tx.maxPriorityFeePerGas().compareTo(type2Tx.maxFeePerGas()) <= 0
                    ? type2Tx.maxPriorityFeePerGas()
                    : type2Tx.maxFeePerGas();
        }

        if (tx instanceof ParsedType1Transaction type1Tx) {
            return type1Tx.gasPrice();
        }

        if (tx instanceof ParsedType0Transaction type0Tx) {
            return type0Tx.gasPrice();
        }

        if (tx instanceof ParsedType2RSKTransaction type2RskTx) {
            return type2RskTx.gasPrice();
        }

        throw new IllegalArgumentException("Unsupported parsed transaction type: " + tx.getClass().getName());
    }

    public byte[] accessListBytes() {
        if (tx instanceof ParsedType1Transaction type1Tx) {
            return type1Tx.accessListBytes();
        }

        if (tx instanceof ParsedType2Transaction type2Tx) {
            return type2Tx.accessListBytes();
        }

        return new byte[0];
    }

    public Coin maxPriorityFeePerGas() {
        if (tx instanceof ParsedType2Transaction type2Tx) {
            return type2Tx.maxPriorityFeePerGas();
        }
        return null;
    }

    public Coin maxFeePerGas() {
        if (tx instanceof ParsedType2Transaction type2Tx) {
            return type2Tx.maxFeePerGas();
        }
        return null;
    }
}

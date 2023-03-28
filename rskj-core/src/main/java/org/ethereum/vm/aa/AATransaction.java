package org.ethereum.vm.aa;

import org.ethereum.core.Transaction;

public class AATransaction extends Transaction {

    protected AATransaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data) {
        super(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data);
    }

    public AATransaction(Transaction tx, byte[] data) {
        this(tx.getNonce(), tx.getGasPrice().getBytes(), tx.getGasLimit(), tx.getSender().getBytes(), tx.getValue().getBytes(), data);
        super.sender = tx.getSender();
    }
}

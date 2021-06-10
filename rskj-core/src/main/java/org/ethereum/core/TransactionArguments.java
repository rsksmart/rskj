package org.ethereum.core;

import java.math.BigInteger;

import java.util.Arrays;


public class TransactionArguments {

    public String from;
    public byte[] to;
    public BigInteger gas;
    public BigInteger gasLimit;
    public BigInteger gasPrice;
    public BigInteger value;
    public String data; // compiledCode
    public BigInteger nonce;
    public byte chainId;  //NOSONAR

    @Override
    public String toString() {
        return "TransactionArguments{" +
                "from='" + from + '\'' +
                ", to='" + Arrays.toString(to) + '\'' +
                ", gasLimit='" + ((gas != null)?gas:gasLimit) + '\'' +
                ", gasPrice='" + gasPrice + '\'' +
                ", value='" + value + '\'' +
                ", data='" + data + '\'' +
                ", nonce='" + nonce + '\'' +
                ", chainId='" + chainId + '\'' +
                '}';
    }
}
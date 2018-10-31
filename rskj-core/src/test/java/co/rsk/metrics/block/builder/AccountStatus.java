package co.rsk.metrics.block.builder;


import java.io.Serializable;
import java.math.BigInteger;

public class AccountStatus implements Serializable {
    private String accountName;
    private String accountAddress;
    private BigInteger lastNonce;

    public  AccountStatus(String accountAddress, String accountName){
        this.accountAddress = accountAddress;
        lastNonce = BigInteger.ZERO;
        this.accountName = accountName;
    }


    public String getAddress(){
        return this.accountAddress;
    }
    public String getAccountName(){return this.accountName;};

    public BigInteger nextNonce(){
        BigInteger nonce = this.lastNonce;
        this.lastNonce = this.lastNonce.add(BigInteger.ONE);
        return nonce;
    }
}
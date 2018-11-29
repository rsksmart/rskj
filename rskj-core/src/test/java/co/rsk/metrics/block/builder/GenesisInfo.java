package co.rsk.metrics.block.builder;

import java.io.Serializable;
import java.util.Vector;


public class GenesisInfo implements Serializable {
    private Vector<AccountStatus> generatedTokenContracts;
    private Vector<AccountStatus> generatedNormalAccounts;
    private Vector<AccountStatus> generatedRemascAccounts;
    private AccountStatus tokensOwner;
    private byte[] genesisFileHash;


    public GenesisInfo(Vector<AccountStatus> normalAccounts, Vector<AccountStatus> tokenAccounts, Vector<AccountStatus> remascAccounts, byte[] genesisConfigHash, AccountStatus tokensOwner){
        this.generatedNormalAccounts = normalAccounts;
        this.generatedTokenContracts = tokenAccounts;
        this.generatedRemascAccounts = remascAccounts;
        this.genesisFileHash = genesisConfigHash;
        this.tokensOwner = tokensOwner;
    }

    public Vector<AccountStatus> getRegularAccounts(){
        return this.generatedNormalAccounts;
    }

    public Vector<AccountStatus> getTokenContracts(){
        return this.generatedTokenContracts;
    }

    public Vector<AccountStatus> getRemascCoinbases(){ return  this.generatedRemascAccounts;}

    public AccountStatus getTokensOwner(){
        return  this.tokensOwner;
    }
    public byte[] getGenesisFileHash(){
        return this.genesisFileHash;
    }

}
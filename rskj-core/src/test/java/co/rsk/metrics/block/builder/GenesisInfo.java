package co.rsk.metrics.block.builder;

import java.io.Serializable;
import java.util.Vector;


public class GenesisInfo implements Serializable {
    private Vector<AccountStatus> generatedTokenContracts;
    private Vector<AccountStatus> generatedNormalAccounts;
    private AccountStatus tokensOwner;
    private byte[] genesisFileHash;


    public GenesisInfo(Vector<AccountStatus> normalAccounts, Vector<AccountStatus> tokenAccounts, byte[] genesisConfigHash, AccountStatus tokensOwner){
        this.generatedNormalAccounts = normalAccounts;
        this.generatedTokenContracts = tokenAccounts;
        this.genesisFileHash = genesisConfigHash;
        this.tokensOwner = tokensOwner;
    }

    public Vector<AccountStatus> getRegularAccounts(){
        return this.generatedNormalAccounts;
    }

    public Vector<AccountStatus> getTokenContracts(){
        return this.generatedTokenContracts;
    }

    public AccountStatus getTokensOwner(){
        return  this.tokensOwner;
    }
    public byte[] getGenesisFileHash(){
        return this.genesisFileHash;
    }

}
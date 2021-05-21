package co.rsk.rpc.modules.eth.getProof;

import java.util.List;

public class ProofDTO {

    private String balance;
    private String codeHash;
    private String nonce;
    private String storageHash;
    private List<String> accountProof;
    private List<StorageProofDTO> storageProof;

    public ProofDTO(String balance, String codeHash, String nonce, String storageHash, List<String> accountProof, List<StorageProofDTO> storageProof) {
        this.balance = balance;
        this.codeHash = codeHash;
        this.nonce = nonce;
        this.storageHash = storageHash;
        this.accountProof = accountProof;
        this.storageProof = storageProof;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getStorageHash() {
        return storageHash;
    }

    public void setStorageHash(String storageHash) {
        this.storageHash = storageHash;
    }

    public List<String> getAccountProof() {
        return accountProof;
    }

    public void setAccountProof(List<String> accountProof) {
        this.accountProof = accountProof;
    }

    public List<StorageProofDTO> getStorageProof() {
        return storageProof;
    }

    public void setStorageProof(List<StorageProofDTO> storageProof) {
        this.storageProof = storageProof;
    }
}

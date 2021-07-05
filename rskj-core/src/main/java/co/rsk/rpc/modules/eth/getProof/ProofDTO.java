package co.rsk.rpc.modules.eth.getProof;

import java.util.List;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProofDTO)) return false;
        ProofDTO proofDTO = (ProofDTO) o;
        return Objects.equals(getBalance(), proofDTO.getBalance()) &&
                Objects.equals(getCodeHash(), proofDTO.getCodeHash()) &&
                Objects.equals(getNonce(), proofDTO.getNonce()) &&
                Objects.equals(getStorageHash(), proofDTO.getStorageHash()) &&
                Objects.equals(getAccountProof(), proofDTO.getAccountProof()) &&
                Objects.equals(getStorageProof(), proofDTO.getStorageProof());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getBalance(), getCodeHash(), getNonce(),
                getStorageHash(), getAccountProof(), getStorageProof());
    }
}

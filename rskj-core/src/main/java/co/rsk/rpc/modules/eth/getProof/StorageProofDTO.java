package co.rsk.rpc.modules.eth.getProof;

import java.util.List;

public class StorageProofDTO {

    private String key;
    private String value;
    private List<String> proofs;

    public StorageProofDTO(String key, String value, List<String> proofs) {
        this.key = key;
        this.value = value;
        this.proofs = proofs;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public List<String> getProofs() {
        return proofs;
    }

    public void setProofs(List<String> proofs) {
        this.proofs = proofs;
    }
}

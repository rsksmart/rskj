package co.rsk.rpc.modules.eth.getProof;

import com.google.common.annotations.VisibleForTesting;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class StorageProofDTO {

    private String key;
    private String value;
    private List<String> proofs;

    public StorageProofDTO(String key, @Nullable String value, List<String> proofs) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StorageProofDTO)) return false;
        StorageProofDTO that = (StorageProofDTO) o;
        return Objects.equals(getKey(), that.getKey()) &&
                Objects.equals(getValue(), that.getValue()) &&
                Objects.equals(getProofs(), that.getProofs());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey(), getValue(), getProofs());
    }

    @VisibleForTesting
    public List<byte[]> getProofsAsByteArray() {
        return proofs
                .stream()
                // todo(fedejinich) strip proof properlly
                .map(proof -> Hex.decode(proof.substring(2)))
                .collect(Collectors.toList());
    }
}

package org.ethereum.rpc.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.util.Arrays;
import java.util.Objects;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

public class BlockParsedRequestDTO {

    // Convenience fields
    private Boolean useBlockNumber = true;
    private String blockHashAsString;

    // Actual Data fields
    private String blockNumber;
    private byte[] blockHash;
    private Boolean requireCanonical;

    // Added for any serialization requirements
    public BlockParsedRequestDTO() {}

    @JsonCreator
    public BlockParsedRequestDTO(@JsonProperty("blockNumber") String blockNumber,
                                 @JsonProperty("blockHash") String blockHash,
                                 @JsonProperty("requireCanonical") Boolean requireCanonical) {
        this.blockNumber = blockNumber;

        if(!StringUtils.isBlank(blockHash)) {
            this.blockHashAsString = blockHash;
            this.requireCanonical = (requireCanonical == null)
                ? false
                : requireCanonical;
            this.blockHash = stringHexToByteArray(blockHash);
            this.useBlockNumber = false;
        }
    }

    public BlockParsedRequestDTO(String blockNameOrString) {
        this.useBlockNumber = true;
        this.blockNumber = blockNameOrString;
    }

    public Boolean getUseBlockNumber() {
        return useBlockNumber;
    }

    public void setUseBlockNumber(Boolean useBlockNumber) {
        this.useBlockNumber = useBlockNumber;
    }

    public String getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(String blockNumber) {
        this.blockNumber = blockNumber;
    }

    public byte[] getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(byte[] blockHash) {
        this.blockHash = blockHash;
    }

    public Boolean getRequireCanonical() {
        return requireCanonical;
    }

    public void setRequireCanonical(Boolean requireCanonical) {
        this.requireCanonical = requireCanonical;
    }

    public String getBlockHashAsString() {
        return blockHashAsString;
    }

    public void setBlockHashAsString(String blockHashAsString) {
        this.blockHashAsString = blockHashAsString;
    }

    @Override
    public String toString() {
        return "BlockParsedRequestDTO{" +
            "useBlockNumber=" + useBlockNumber +
            ", blockHashAsString='" + blockHashAsString + '\'' +
            ", blockNumber='" + blockNumber + '\'' +
            ", requireCanonical=" + requireCanonical +
            '}';
    }

    /**
     * We ignore blockHashAsString and useBlockNumber
     * since they are convenience fields primarily
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlockParsedRequestDTO that = (BlockParsedRequestDTO) o;
        return Objects.equals(blockNumber, that.blockNumber) &&
                Arrays.equals(blockHash, that.blockHash) &&
                Objects.equals(requireCanonical, that.requireCanonical);
    }

    /**
     * We ignore blockHashAsString and useBlockNumber
     * since they are convenience fields primarily
     */
    @Override
    public int hashCode() {
        int result = Objects.hash(blockNumber, requireCanonical);
        result = 31 * result + Arrays.hashCode(blockHash);
        return result;
    }
}

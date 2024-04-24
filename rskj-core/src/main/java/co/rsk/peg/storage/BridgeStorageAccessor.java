package co.rsk.peg.storage;

import co.rsk.core.RskAddress;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;

import java.io.IOException;

public class BridgeStorageAccessor {

    private final Repository repository;
    private static final RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;

    public BridgeStorageAccessor(Repository repository) {
        this.repository = repository;
    }

    protected <T> T safeGetFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) {
        try {
            return getFromRepository(keyAddress, deserializer);
        } catch (IOException ioe) {
            throw new StorageAccessException("Unable to get from repository: " + keyAddress, ioe);
        }
    }

    private <T> T getFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) throws IOException {
        byte[] data = repository.getStorageBytes(contractAddress, keyAddress);
        return deserializer.deserialize(data);
    }

    protected <T> void safeSaveToRepository(DataWord addressKey, T object, RepositorySerializer<T> serializer) {
        try {
            saveToRepository(addressKey, object, serializer);
        } catch (IOException ioe) {
            throw new StorageAccessException("Unable to save to repository: " + addressKey, ioe);
        }
    }

    private <T> void saveToRepository(DataWord addressKey, T object, RepositorySerializer<T> serializer) throws IOException {
        byte[] data = null;
        if (object != null) {
            data = serializer.serialize(object);
        }
        repository.addStorageBytes(contractAddress, addressKey, data);
    }

    protected interface RepositoryDeserializer<T> {
        T deserialize(byte[] data) throws IOException;
    }

    protected interface RepositorySerializer<T> {
        byte[] serialize(T object) throws IOException;
    }

}

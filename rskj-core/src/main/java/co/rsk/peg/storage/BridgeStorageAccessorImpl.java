package co.rsk.peg.storage;

import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import java.io.IOException;

public class BridgeStorageAccessorImpl implements BridgeStorageAccessor {

    private final Repository repository;

    public BridgeStorageAccessorImpl(Repository repository) {
        this.repository = repository;
    }

    @Override
    public <T> T safeGetFromRepository(DataWord keyAddress, BridgeStorageAccessor.RepositoryDeserializer<T> deserializer) {
        try {
            return getFromRepository(keyAddress, deserializer);
        } catch (IOException ioe) {
            throw new StorageAccessException("Unable to get from repository: " + keyAddress, ioe);
        }
    }

    private <T> T getFromRepository(DataWord keyAddress, BridgeStorageAccessor.RepositoryDeserializer<T> deserializer) throws IOException {
        byte[] data = repository.getStorageBytes(contractAddress, keyAddress);
        return deserializer.deserialize(data);
    }

    @Override
    public <T> void safeSaveToRepository(DataWord addressKey, T object, BridgeStorageAccessor.RepositorySerializer<T> serializer) {
        try {
            saveToRepository(addressKey, object, serializer);
        } catch (IOException ioe) {
            throw new StorageAccessException("Unable to save to repository: " + addressKey, ioe);
        }
    }

    private <T> void saveToRepository(DataWord addressKey, T object, BridgeStorageAccessor.RepositorySerializer<T> serializer) throws IOException {
        byte[] data = null;
        if (object != null) {
            data = serializer.serialize(object);
        }
        repository.addStorageBytes(contractAddress, addressKey, data);
    }

}

package co.rsk.peg.storage;

import co.rsk.core.RskAddress;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;

import java.io.IOException;

public class BridgeStorageAccessorImpl implements StorageAccessor {

    private static final RskAddress CONTRACT_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;
    private final Repository repository;

    public BridgeStorageAccessorImpl(Repository repository) {
        this.repository = repository;
    }

    @Override
    public <T> T safeGetFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) {
        try {
            return getFromRepository(keyAddress, deserializer);
        } catch (IOException ioe) {
            throw new StorageAccessException("Unable to get from repository: " + keyAddress, ioe);
        }
    }

    private <T> T getFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) throws IOException {
        byte[] data = repository.getStorageBytes(CONTRACT_ADDRESS, keyAddress);
        return deserializer.deserialize(data);
    }

    @Override
    public <T> void safeSaveToRepository(DataWord addressKey, T object, RepositorySerializer<T> serializer) throws IOException {
        byte[] serializedData = getSerializedData(object, serializer);
        safeSaveToRepository(addressKey, serializedData);
    }

    private <T> byte[] getSerializedData(T object, RepositorySerializer<T> serializer) throws IOException {
        byte[] data = null;
        if (object != null) {
            data = serializer.serialize(object);
        }

        return data;
    }

    @Override
    public void safeSaveToRepository(DataWord addressKey, byte[] serializedData) throws IOException {
        try {
            repository.addStorageBytes(CONTRACT_ADDRESS, addressKey, serializedData);
        } catch (RuntimeException e) {
            throw new IOException("Unable to save serialized data " + serializedData + "to repository", e);
        }
    }

}

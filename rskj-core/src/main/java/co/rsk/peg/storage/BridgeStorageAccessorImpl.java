package co.rsk.peg.storage;

import co.rsk.core.RskAddress;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;

public class BridgeStorageAccessorImpl implements StorageAccessor {

    private static final RskAddress CONTRACT_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;
    private final Repository repository;

    public BridgeStorageAccessorImpl(Repository repository) {
        this.repository = repository;
    }

    @Override
    public <T> T getFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) {
        byte[] data = repository.getStorageBytes(CONTRACT_ADDRESS, keyAddress);
        return deserializer.deserialize(data);
    }

    @Override
    public <T> void saveToRepository(DataWord addressKey, T object, RepositorySerializer<T> serializer) {
        byte[] serializedData = getSerializedData(object, serializer);
        saveToRepository(addressKey, serializedData);
    }

    private <T> byte[] getSerializedData(T object, RepositorySerializer<T> serializer) {
        byte[] data = null;
        if (object != null) {
            data = serializer.serialize(object);
        }

        return data;
    }

    @Override
    public void saveToRepository(DataWord addressKey, byte[] serializedData) {
        repository.addStorageBytes(CONTRACT_ADDRESS, addressKey, serializedData);
    }
}

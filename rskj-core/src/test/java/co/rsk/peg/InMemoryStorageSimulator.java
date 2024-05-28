package co.rsk.peg;

import co.rsk.peg.storage.StorageAccessException;
import co.rsk.peg.storage.StorageAccessor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.ethereum.vm.DataWord;

public class InMemoryStorageSimulator implements StorageAccessor {

    private final Map<DataWord, byte[]> storage = new HashMap<>();

    @Override
    public <T> T safeGetFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) {
        try {
            byte[] data = storage.get(keyAddress);
            return deserializer.deserialize(data);
        } catch (IOException e) {
            throw new StorageAccessException("Unable to retrieve data from In-Memory Storage: " + keyAddress, e);
        }
    }

    @Override
    public <T> void safeSaveToRepository(DataWord addressKey, T object, RepositorySerializer<T> serializer) {
        try {
            if (object == null) {
                throw new IOException();
            }
            byte[] data = serializer.serialize(object);
            storage.put(addressKey, data);
        } catch (IOException e) {
            throw new StorageAccessException("Unable to store data on In-Memory Storage: " + addressKey, e);
        }
    }
}

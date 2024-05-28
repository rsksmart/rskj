package co.rsk.peg;

import co.rsk.peg.storage.StorageAccessException;
import co.rsk.peg.storage.StorageAccessor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.ethereum.vm.DataWord;

/**
 * This is an In-Memory Storage Simulator to be used in Unit Tests to reduce the use of mocks, thus
 * improving the Unit Tests reliability.
 */
public class InMemoryStorageSimulator implements StorageAccessor {

    private final Map<DataWord, byte[]> storage = new HashMap<>();

    @Override
    public <T> T safeGetFromRepository(DataWord key, RepositoryDeserializer<T> deserializer) {
        try {
            byte[] data = storage.get(key);
            return deserializer.deserialize(data);
        } catch (IOException e) {
            throw new StorageAccessException("Unable to retrieve data from In-Memory Storage: " + key, e);
        }
    }

    @Override
    public <T> void safeSaveToRepository(DataWord key, T value, RepositorySerializer<T> serializer) {
        try {
            if (Objects.isNull(value)) {
                throw new IOException();
            }
            byte[] data = serializer.serialize(value);
            storage.put(key, data);
        } catch (IOException e) {
            throw new StorageAccessException("Unable to store data on In-Memory Storage: " + key, e);
        }
    }
}

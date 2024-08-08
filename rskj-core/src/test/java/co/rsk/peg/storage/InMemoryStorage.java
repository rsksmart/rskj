package co.rsk.peg.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.ethereum.vm.DataWord;

/**
 * This is an In-Memory Storage to be used in Unit Tests to reduce the use of mocks, thus
 * improving the Unit Tests reliability.
 */
public class InMemoryStorage implements StorageAccessor {

    private final Map<DataWord, byte[]> storage = new HashMap<>();

    @Override
    public <T> T getFromRepository(DataWord key, RepositoryDeserializer<T> deserializer) {
            byte[] data = storage.get(key);
            return deserializer.deserialize(data);
    }

    @Override
    public <T> void saveToRepository(DataWord key, T value, RepositorySerializer<T> serializer) {
        byte[] data = null;
        if (!Objects.isNull(value)) {
            data = serializer.serialize(value);
        }

        saveToRepository(key, data);
    }

    @Override
    public void saveToRepository(DataWord key, byte[] data) {
        storage.put(key, data);
    }
}

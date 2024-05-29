package co.rsk.peg;

import co.rsk.peg.storage.StorageAccessor;
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
    public <T> T safeGetFromRepository(DataWord key, RepositoryDeserializer<T> deserializer) {
            byte[] data = storage.get(key);
            return deserializer.deserialize(data);
    }

    @Override
    public <T> void safeSaveToRepository(DataWord key, T value, RepositorySerializer<T> serializer) {
        byte[] data = null;
        if (!Objects.isNull(value)) {
            data = serializer.serialize(value);
        }
        storage.put(key, data);
    }
}

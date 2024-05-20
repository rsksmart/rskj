package co.rsk.peg.storage;

import java.io.IOException;
import org.ethereum.vm.DataWord;

public interface StorageAccessor {

    <T> T safeGetFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer);

    <T> void safeSaveToRepository(DataWord addressKey, T object, RepositorySerializer<T> serializer);

    void saveToRepository(DataWord addressKey, byte[] data) throws IOException;

    interface RepositoryDeserializer<T> {
        T deserialize(byte[] data) throws IOException;
    }

    interface RepositorySerializer<T> {
        byte[] serialize(T object) throws IOException;
    }
}

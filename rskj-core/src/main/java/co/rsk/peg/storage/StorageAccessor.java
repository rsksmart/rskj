package co.rsk.peg.storage;

import org.ethereum.vm.DataWord;

import java.io.IOException;

public interface StorageAccessor {

    <T> T safeGetFromRepository(DataWord key, RepositoryDeserializer<T> deserializer);

    <T> void safeSaveToRepository(DataWord key, T value, RepositorySerializer<T> serializer);

    void saveToRepository(DataWord key, byte[] data);

    interface RepositoryDeserializer<T> {
        T deserialize(byte[] value) throws IOException;
    }

    interface RepositorySerializer<T> {
        byte[] serialize(T value) throws IOException;
    }
}

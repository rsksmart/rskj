package co.rsk.peg.storage;

import org.ethereum.vm.DataWord;

public interface StorageAccessor {

    <T> T safeGetFromRepository(DataWord key, RepositoryDeserializer<T> deserializer);

    <T> void safeSaveToRepository(DataWord key, T value, RepositorySerializer<T> serializer);

    void safeSaveToRepository(DataWord key, byte[] data);

    interface RepositoryDeserializer<T> {
        T deserialize(byte[] value);
    }

    interface RepositorySerializer<T> {
        byte[] serialize(T value);
    }
}

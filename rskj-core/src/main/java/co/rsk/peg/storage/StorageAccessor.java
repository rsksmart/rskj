package co.rsk.peg.storage;

import org.ethereum.vm.DataWord;

public interface StorageAccessor {

    <T> T getFromRepository(DataWord key, RepositoryDeserializer<T> deserializer);

    <T> void saveToRepository(DataWord key, T value, RepositorySerializer<T> serializer);

    void saveToRepository(DataWord key, byte[] data);

    interface RepositoryDeserializer<T> {
        T deserialize(byte[] value);
    }

    interface RepositorySerializer<T> {
        byte[] serialize(T value);
    }
}

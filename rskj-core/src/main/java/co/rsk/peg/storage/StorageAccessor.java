package co.rsk.peg.storage;

import co.rsk.core.RskAddress;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;

import java.io.IOException;

public interface StorageAccessor {

    RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;

    <T> T safeGetFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer);

    <T> void safeSaveToRepository(DataWord addressKey, T object, RepositorySerializer<T> serializer);

    interface RepositoryDeserializer<T> {
        T deserialize(byte[] data) throws IOException;
    }

    interface RepositorySerializer<T> {
        byte[] serialize(T object) throws IOException;
    }

}

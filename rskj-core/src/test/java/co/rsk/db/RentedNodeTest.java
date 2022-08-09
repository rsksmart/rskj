package co.rsk.db;

import co.rsk.storagerent.RentedNode;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;
import static org.junit.Assert.assertEquals;

public class RentedNodeTest {

    public static final ByteArrayWrapper VALID_KEY = new ByteArrayWrapper(Keccak256Helper.keccak256("something".getBytes(StandardCharsets.UTF_8)));

}

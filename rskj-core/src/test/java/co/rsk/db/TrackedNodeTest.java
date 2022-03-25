package co.rsk.db;

import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrackedNode;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.db.OperationType.*;
import static org.junit.Assert.assertEquals;

public class TrackedNodeTest {

    public static final ByteArrayWrapper VALID_KEY = new ByteArrayWrapper(Keccak256Helper.keccak256("something".getBytes(StandardCharsets.UTF_8)));
    private static final String TRANSACTION_HASH = "this can be any non-empty string";

    @Test
    public void createValidTrackedNodes() {
        List<TrackedNode> validElements = Arrays.asList(
            new TrackedNode(VALID_KEY, READ_OPERATION,  TRANSACTION_HASH, true),
            new TrackedNode(VALID_KEY, READ_OPERATION,  TRANSACTION_HASH, false),
            new TrackedNode(VALID_KEY, WRITE_OPERATION, TRANSACTION_HASH, true),
            new TrackedNode(VALID_KEY, WRITE_OPERATION, TRANSACTION_HASH, true)
        );

        assertEquals(4, validElements.size());
    }

    @Test
    public void invalidTrackedNode() {
        try {
            new TrackedNode(VALID_KEY, WRITE_OPERATION, TRANSACTION_HASH, false);
        } catch (IllegalArgumentException e) {
            assertEquals("a WRITE_OPERATION should always have a true result", e.getMessage());
        }

        OperationType[] values = OperationType.values();
        for(int i = 0; i < values.length; i++) {
            try {
                new TrackedNode(VALID_KEY, values[i], "", true);
            } catch (IllegalArgumentException e) {
                assertEquals("transaction hash can not be empty", e.getMessage());
            }
        }
    }
}

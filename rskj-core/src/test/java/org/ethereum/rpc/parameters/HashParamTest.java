package org.ethereum.rpc.parameters;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashParamTest {

    @Test
    void testBlockHashParam() {
        String validHash = "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8";
        String invalidHash = "invalidhash";
        String shorterHash = "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a3568";
        String invalidCharHash = "0xc2b835124172db5bdzz1bb94fa123721eacac43b5cba2499b22c7583a3568";

        BlockHashParam blockHashParam = new BlockHashParam(validHash);

        assertEquals(validHash, blockHashParam.getHash().toJsonString());

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(null));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(""));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(invalidHash));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(shorterHash));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(invalidCharHash));
    }

    @Test
    void testTxHashParam() {
        String validHash = "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8";
        String invalidHash = "invalidhash";
        String shorterHash = "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a3568";
        String invalidCharHash = "0xc2b835124172db5bdzz1bb94fa123721eacac43b5cba2499b22c7583a3568";

        TxHashParam txHashParam = new TxHashParam(validHash);

        assertEquals(validHash, txHashParam.getHash().toJsonString());

        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(null));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(""));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(invalidHash));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(shorterHash));
        assertThrows(RskJsonRpcRequestException.class, () -> new BlockHashParam(invalidCharHash));
    }
}
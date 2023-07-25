package co.rsk.rpc.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.ethereum.rpc.Web3Impl;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonRpcWeb3ServerHandlerInvocationListenerTest {

    @Test
    void whenWillInvoke_throwInvalidParamError() throws NoSuchMethodException {
        Method mockedMethod = Web3Impl.class.getMethod("eth_getBlockByHash", String.class, Boolean.class);
        List<JsonNode> mockedJsonNodeList = new ArrayList<>();
        mockedJsonNodeList.add(new TextNode("0x8479281ade274e7d8372313a396482a4266ac38b9eae349a8924337b98f5e4fe"));
        mockedJsonNodeList.add(new IntNode(1));


        JsonRpcWeb3ServerHandlerInvocationListener listener = new JsonRpcWeb3ServerHandlerInvocationListener();

        RskJsonRpcRequestException expectedException = RskJsonRpcRequestException.invalidParamError("invalid argument 1 json: cannot unmarshal number into of type boolean");
Assertions.assertDoesNotThrow(() -> {});
        RskJsonRpcRequestException exception = Assertions.assertThrowsExactly(
                expectedException.getClass(),
                () -> listener.willInvoke(mockedMethod, mockedJsonNodeList)
        );

        assertEquals(expectedException.getCode(), exception.getCode());
    }

    @Test
    void whenWillInvoke_doNotThrowException() throws NoSuchMethodException {
        Method mockedMethod = Web3Impl.class.getMethod("eth_getBlockByHash", String.class, Boolean.class);
        List<JsonNode> mockedJsonNodeList = new ArrayList<>();
        mockedJsonNodeList.add(new TextNode("0x8479281ade274e7d8372313a396482a4266ac38b9eae349a8924337b98f5e4fe"));
        mockedJsonNodeList.add(BooleanNode.FALSE);

        JsonRpcWeb3ServerHandlerInvocationListener listener = new JsonRpcWeb3ServerHandlerInvocationListener();

        Assertions.assertDoesNotThrow(() -> listener.willInvoke(mockedMethod, mockedJsonNodeList));
    }
}

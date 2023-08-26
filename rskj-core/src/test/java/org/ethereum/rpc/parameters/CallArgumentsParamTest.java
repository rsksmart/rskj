package org.ethereum.rpc.parameters;

import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CallArgumentsParamTest {
    private CallArguments buildCallArguments() {
        CallArguments callArguments = new CallArguments();

        callArguments.setFrom("0x7986b3df570230288501eea3d890bd66948c9b79");
        callArguments.setTo("0xE7B8E91401bF4d1669f54Dc5f98109D7EfBC4EEa");
        callArguments.setGas("0x76c0");
        callArguments.setGasPrice("0x9184e72a000");
        callArguments.setValue("0x9184e72a");
        callArguments.setData("0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445675");
        callArguments.setNonce("0x1");
        callArguments.setChainId("0x539");

        return callArguments;
    }

    @Test
    public void testValidCallArgumentsParam() {
        CallArguments validCallArguments = buildCallArguments();

        CallArgumentsParam callArgumentsParam = new CallArgumentsParam(validCallArguments);

        assertEquals(validCallArguments.getFrom(), callArgumentsParam.getCallArguments().getFrom());
        assertEquals(validCallArguments.getTo(), callArgumentsParam.getCallArguments().getTo());
        assertEquals(validCallArguments.getGas(), callArgumentsParam.getCallArguments().getGas());
        assertEquals(validCallArguments.getGasPrice(), callArgumentsParam.getCallArguments().getGasPrice());
        assertEquals(validCallArguments.getValue(), callArgumentsParam.getCallArguments().getValue());
        assertEquals(validCallArguments.getData(), callArgumentsParam.getCallArguments().getData());
        assertEquals(validCallArguments.getNonce(), callArgumentsParam.getCallArguments().getNonce());
        assertEquals(validCallArguments.getChainId(), callArgumentsParam.getCallArguments().getChainId());
    }

    @Test
    public void testInvalidFromInCallArgumentsParam() {
        CallArguments invalidCallArguments = buildCallArguments();
        invalidCallArguments.setFrom("0x7986b3df570230288501eea3d890bd66948c9bzx");

        assertThrows(RskJsonRpcRequestException.class, () -> new CallArgumentsParam(invalidCallArguments));
    }

    @Test
    public void testInvalidToInCallArgumentsParam() {
        CallArguments invalidCallArguments = buildCallArguments();
        invalidCallArguments.setTo("0xE7B8E91401bF4d1669f54Dc5f98109D7EfBC4Eqw");

        assertThrows(RskJsonRpcRequestException.class, () -> new CallArgumentsParam(invalidCallArguments));
    }

    @Test
    public void testInvalidGasInCallArgumentsParam() {
        CallArguments invalidCallArguments = buildCallArguments();
        invalidCallArguments.setGas("0x76cZ");

        assertThrows(RskJsonRpcRequestException.class, () -> new CallArgumentsParam(invalidCallArguments));
    }

    @Test
    public void testInvalidGasPriceInCallArgumentsParam() {
        CallArguments invalidCallArguments = buildCallArguments();
        invalidCallArguments.setGasPrice("12tb45");

        assertThrows(RskJsonRpcRequestException.class, () -> new CallArgumentsParam(invalidCallArguments));
    }

    @Test
    public void testInvalidValueInCallArgumentsParam() {
        CallArguments invalidCallArguments = buildCallArguments();
        invalidCallArguments.setValue("0x9184e7gt");

        assertThrows(RskJsonRpcRequestException.class, () -> new CallArgumentsParam(invalidCallArguments));
    }

    @Test
    public void testInvalidDataInCallArgumentsParam() {
        CallArguments invalidCallArguments = buildCallArguments();
        invalidCallArguments.setData("0xd46e8dd67c5d32be8d46e8dd67c5d32be8058bb8eb970870f072445675058bb8eb970870f072445phm");

        assertThrows(RskJsonRpcRequestException.class, () -> new CallArgumentsParam(invalidCallArguments));
    }

    @Test
    public void testInvalidNonceInCallArgumentsParam() {
        CallArguments invalidCallArguments = buildCallArguments();
        invalidCallArguments.setNonce("0xJ");

        assertThrows(RskJsonRpcRequestException.class, () -> new CallArgumentsParam(invalidCallArguments));
    }

    @Test
    public void testInvalidChainIdInCallArgumentsParam() {
        CallArguments invalidCallArguments = buildCallArguments();
        invalidCallArguments.setChainId("0xB2R");

        assertThrows(RskJsonRpcRequestException.class, () -> new CallArgumentsParam(invalidCallArguments));
    }
}

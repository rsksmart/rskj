package org.ethereum.rpc.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Nazaret García on 26/05/2021
 */

public class RskErrorResolverTest {

    private RskErrorResolver rskErrorResolver;

    @Before
    public void setup() {
        rskErrorResolver = new RskErrorResolver();
    }

    @Test
    public void test_resolveError_givenRskJsonRpcRequestException_returnsJsonErrorAsExpected() throws NoSuchMethodException {
        // Given
        Integer code = 1;
        String message = "message";
        RskJsonRpcRequestException exception = new RskJsonRpcRequestException(code, message);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assert.assertNotNull(result);
        Assert.assertEquals(code, (Integer) result.code);
        Assert.assertEquals(message, result.message);
        Assert.assertNull(result.data);
    }

    @Test
    public void test_resolveError_givenInvalidFormatException_returnsJsonErrorAsExpected() throws NoSuchMethodException {
        // Given
        InvalidFormatException exception = mock(InvalidFormatException.class);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assert.assertNotNull(result);
        Assert.assertEquals(-32603, result.code);
        Assert.assertEquals("Internal server error, probably due to invalid parameter type", result.message);
        Assert.assertNull(result.data);
    }

    @Test
    public void test_resolveError_givenUnrecognizedPropertyException_nullExceptionMessage_returnsJsonErrorWithDefaultMessageAsExpected() throws NoSuchMethodException {
        // Given
        UnrecognizedPropertyException exception = mock(UnrecognizedPropertyException.class);
        when(exception.getMessage()).thenReturn(null);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assert.assertNotNull(result);
        Assert.assertEquals(-32603, result.code);
        Assert.assertEquals("Invalid parameters", result.message);
        Assert.assertNull(result.data);
    }

    @Test
    public void test_resolveError_givenUnrecognizedPropertyException_returnsJsonErrorWithDescriptiveMessageAsExpected() throws NoSuchMethodException {
        // Given
        String message = "Unrecognized field \"form\" (class org.ethereum.rpc.Web3$CallArguments), not marked as ignorable (8 known properties: \"gasPrice\", \"value\", \"gas\", \"from\", \"to\", \"chainId\", \"nonce\", \"data\"])\n at [Source: N/A; line: -1, column: -1] (through reference chain: org.ethereum.rpc.Web3$CallArguments[\"form\"])";
        UnrecognizedPropertyException exception = mock(UnrecognizedPropertyException.class);
        when(exception.getMessage()).thenReturn(message);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assert.assertNotNull(result);
        Assert.assertEquals(-32603, result.code);
        Assert.assertEquals("Unrecognized field \"form\" (class org.ethereum.rpc.Web3$CallArguments), not marked as ignorable (8 known properties: \"gasPrice\", \"value\", \"gas\", \"from\", \"to\", \"chainId\", \"nonce\", \"data\"])", result.message);
        Assert.assertNull(result.data);
    }

    @Test
    public void test_resolveError_givenGenericException_returnsJsonErrorWithDefaultMessageAsExpected() throws NoSuchMethodException {
        // Given
        Exception exception = mock(Exception.class);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assert.assertNotNull(result);
        Assert.assertEquals(-32603, result.code);
        Assert.assertEquals("Internal server error", result.message);
        Assert.assertNull(result.data);
    }

    public void mockMethod() { }

}

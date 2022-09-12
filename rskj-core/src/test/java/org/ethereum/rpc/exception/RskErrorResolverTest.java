package org.ethereum.rpc.exception;

import co.rsk.core.exception.InvalidRskAddressException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.googlecode.jsonrpc4j.ErrorResolver.JsonError;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Nazaret García on 26/05/2021
 */

class RskErrorResolverTest {

    private RskErrorResolver rskErrorResolver;

    @BeforeEach
    void setup() {
        rskErrorResolver = new RskErrorResolver();
    }

    @Test
    void test_resolveError_givenRskJsonRpcRequestException_returnsJsonErrorAsExpected() throws NoSuchMethodException {
        // Given
        Integer code = 1;
        String message = "message";
        RskJsonRpcRequestException exception = new RskJsonRpcRequestException(code, message);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(code, (Integer) result.code);
        Assertions.assertEquals(message, result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenInvalidFormatException_returnsJsonErrorAsExpected() throws NoSuchMethodException {
        // Given
        InvalidFormatException exception = mock(InvalidFormatException.class);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(-32603, result.code);
        Assertions.assertEquals("Internal server error, probably due to invalid parameter type", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenUnrecognizedPropertyException_nullPropertyName_returnsJsonErrorWithDefaultMessageAsExpected() throws NoSuchMethodException {
        // Given
        UnrecognizedPropertyException exception = mock(UnrecognizedPropertyException.class);
        when(exception.getPropertyName()).thenReturn(null);
        when(exception.getKnownPropertyIds()).thenReturn(new ArrayList<>());

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(-32602, result.code);
        Assertions.assertEquals("Invalid parameters", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenUnrecognizedPropertyException_nullKnownPropertyIds_returnsJsonErrorWithDefaultMessageAsExpected() throws NoSuchMethodException {
        // Given
        UnrecognizedPropertyException exception = mock(UnrecognizedPropertyException.class);
        when(exception.getPropertyName()).thenReturn("propertyName");
        when(exception.getKnownPropertyIds()).thenReturn(null);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(-32602, result.code);
        Assertions.assertEquals("Invalid parameters", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenUnrecognizedPropertyException_nullPropertyNameAndNullKnownPropertyIds_returnsJsonErrorWithDefaultMessageAsExpected() throws NoSuchMethodException {
        // Given
        UnrecognizedPropertyException exception = mock(UnrecognizedPropertyException.class);
        when(exception.getPropertyName()).thenReturn(null);
        when(exception.getKnownPropertyIds()).thenReturn(null);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(-32602, result.code);
        Assertions.assertEquals("Invalid parameters", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenUnrecognizedPropertyException_returnsJsonErrorWithDescriptiveMessageAsExpected() throws NoSuchMethodException {
        // Given
        String propertyName = "propertyName";

        String propertyId1 = "propertyId.1";
        String propertyId2 = "propertyId.2";
        List<Object> knownPropertyIds = new ArrayList<>();
        knownPropertyIds.add(propertyId1);
        knownPropertyIds.add(propertyId2);

        UnrecognizedPropertyException exception = mock(UnrecognizedPropertyException.class);
        when(exception.getPropertyName()).thenReturn(propertyName);
        when(exception.getKnownPropertyIds()).thenReturn(knownPropertyIds);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(-32602, result.code);
        Assertions.assertEquals("Unrecognized field \"propertyName\" (2 known properties: [\"propertyId.1\", \"propertyId.2\"])", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenUnrecognizedPropertyException_withZeroKnownProperties_returnsJsonErrorWithDescriptiveMessageAsExpected() throws NoSuchMethodException {
        // Given
        String propertyName = "propertyName";

        UnrecognizedPropertyException exception = mock(UnrecognizedPropertyException.class);
        when(exception.getPropertyName()).thenReturn(propertyName);
        when(exception.getKnownPropertyIds()).thenReturn(new ArrayList<>());

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(-32602, result.code);
        Assertions.assertEquals("Unrecognized field \"propertyName\" (0 known properties: [])", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenGenericException_returnsJsonErrorWithDefaultMessageAsExpected() throws NoSuchMethodException {
        // Given
        Exception exception = mock(Exception.class);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(-32603, result.code);
        Assertions.assertEquals("Internal server error", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenJsonMappingException_returnsJsonErrorAsExpected() throws NoSuchMethodException {
        // Given
        Integer code = -32602;
        String message = "Can not construct instance";
        JsonMappingException exception = JsonMappingException.from(new DefaultSerializerProvider.Impl(), message);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(code, (Integer) result.code);
        Assertions.assertEquals("invalid argument 0: json: cannot unmarshal string into value of input", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenUnsupportedOperationException_returnsJsonErrorAsExpected() throws NoSuchMethodException {
        // Given
        Integer code = -32601;
        String message = "message";
        UnsupportedOperationException exception = new UnsupportedOperationException(message);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(code, (Integer) result.code);
        Assertions.assertEquals("the method mockMethod does not exist/is not available", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenMethodNotSupportedExceptionMsg_returnsJsonErrorAsExpected() throws NoSuchMethodException {
        // Given
        Integer code = -32601;
        String message = "method not supported";
        Exception exception = new Exception(message);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(code, (Integer) result.code);
        Assertions.assertEquals("the method mockMethod does not exist/is not available", result.message);
        Assertions.assertNull(result.data);
    }

    @Test
    void test_resolveError_givenInvalidRskAddressExceptionMsg_returnsJsonErrorAsExpected() throws NoSuchMethodException {
        // Given
        Integer code = -32602;
        String message = "An RSK address must be 20 bytes long";
        Exception exception = new InvalidRskAddressException(message);

        Method methodMock = this.getClass().getMethod("mockMethod");
        List<JsonNode> jsonNodeListMock = new ArrayList<>();
        jsonNodeListMock.add(JsonNodeFactory.instance.textNode("0x123456789"));

        // When
        JsonError result = rskErrorResolver.resolveError(exception, methodMock, jsonNodeListMock);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(code, (Integer) result.code);
        Assertions.assertEquals("invalid argument 0: hex string has length 9, want 40 for RSK address", result.message);
        Assertions.assertNull(result.data);
    }

    public void mockMethod() { }

}

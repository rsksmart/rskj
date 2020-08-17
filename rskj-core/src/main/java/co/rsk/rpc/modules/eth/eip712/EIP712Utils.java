package co.rsk.rpc.modules.eth.eip712;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.web3j.abi.DefaultFunctionEncoder;
import org.web3j.abi.TypeDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.NumericType;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Encoding utilities for EIP712.
 *
 * Only EIP v4 implemented. Adding v3 and v1 are trivial, particularly
 * v3, which iboild down to v4 without array handling capabilities.
 *
 * @see <a href="https://github.com/ethereum/EIPs/blob/master/EIPS/eip-712.md">EIP 712 specification</a>
 * @author ppedemon
 */
public class EIP712Utils {

    private static final String EIP712Domain = "EIP712Domain";

    private static final List<String> SCHEMA_KEYS = ImmutableList.of(
            "types", "primaryType", "domain", "message"
    );

    private static final Pattern PRIMARY_TYPE_RE = Pattern.compile("^(\\w*).*");

    private static final byte[] BYTE32_ZERO = new byte[32];

    /**
     * Sanitize input.
     *
     * @param data typed data payload to sanitize, as a Json node
     * @return sanitized data
     */
    private JsonNode sanitize(JsonNode data) {
        ObjectMapper m = new ObjectMapper();
        ObjectNode sanitized = m.createObjectNode();
        for (String key : SCHEMA_KEYS) {
            if (data.has(key)) {
                sanitized.set(key, data.get(key));
            }
        }
        if (sanitized.has("types")) {
            ObjectNode types = sanitized.with("types");
            if (!types.has(EIP712Domain)) {
                types.set(EIP712Domain, m.createArrayNode());
            }
        }
        return sanitized;
    }

    /**
     * Compute the transitively closed set of types dependencies on the given primary type.
     *
     * @param primaryType  primary type name
     * @param types all types declared in typed data
     * @param results list of dependent types collected so far (used as output)
     * @return all dependent types for primary type
     */
    private List<String> findTyDeps(String primaryType, JsonNode types, List<String> results) {
        Matcher m = PRIMARY_TYPE_RE.matcher(primaryType);
        if (!m.matches()) {
            return results;
        }

        primaryType = m.group(1);

        if (results.contains(primaryType) || !types.has(primaryType)) {
            return results;
        }

        results.add(primaryType);
        for (JsonNode tyItem: types.withArray(primaryType)) {
            for (String tyDep: findTyDeps(tyItem.get("type").asText(), types, results)) {
                if (!results.contains(tyDep)) {
                    results.add(tyDep);
                }
            }
        }
        return results;
    }

    /**
     * Encode the given primary type.
     *
     * @param primaryType primary type name
     * @param types all types declared in typed data
     * @return string encoding for the given primary type
     */
    String encodeType(String primaryType, JsonNode types) {
        List<String> deps = new ArrayList<>();
        //noinspection RedundantOperationOnEmptyContainer
        deps = findTyDeps(primaryType, types, deps).stream()
                .filter(Predicate.isEqual(primaryType).negate())
                .sorted()
                .collect(Collectors.toList());
        LinkedList<String> normDeps = Lists.newLinkedList(deps);
        normDeps.addFirst(primaryType);

        StringBuilder sb = new StringBuilder();
        for (String type: normDeps) {
            if (!types.has(type)) {
                throw new IllegalStateException("No definition for type: " + type);
            }

            String tyArgs = StreamSupport.stream(types.withArray(type).spliterator(), false)
                    .map(tyDef -> tyDef.get("type").asText() + " " + tyDef.get("name").asText())
                    .collect(Collectors.joining(","));
            sb.append(String.format("%s(%s)", type, tyArgs));
        }
        return sb.toString();
    }

    /**
     * Encode a field, composed by a type and its value.
     *
     * @param type   field type
     * @param value  field value
     * @param types  all types declared in typed data
     * @return a {@link Type} modeling the given field.
     */
    private Type encodeField(String type, JsonNode value, JsonNode types) {
        if (types.has(type)) {
            return new Bytes32(value == null? BYTE32_ZERO : HashUtil.keccak256(encodeData(type, value, types)));
        }

        if (type.equals("bytes")) {
            String val = value.asText();
            if (val.startsWith("0x")) {
                val = val.substring(2);
            }
            return new Bytes32(HashUtil.keccak256(Hex.decode(val)));
        }

        if (type.equals("string")) {
            return new Bytes32(HashUtil.keccak256(value.asText().getBytes(StandardCharsets.UTF_8)));
        }

        if (type.lastIndexOf(']') == type.length() - 1) {
            if (!value.isArray()) {
                throw new IllegalStateException("Array type with no iterable value");
            }

            String arrayType = type.substring(0, type.lastIndexOf('['));

            Spliterator<JsonNode> it = value.spliterator();
            List<Type> tys = StreamSupport.stream(it, false)
                    .map(v -> encodeField(arrayType, v, types))
                    .collect(Collectors.toList());
            DefaultFunctionEncoder encoder = new DefaultFunctionEncoder();
            String abiEncoded = encoder.encodeParameters(tys);
            return new Bytes32(HashUtil.keccak256(Hex.decode(abiEncoded)));
        }

        try {
            // For numeric types web3j assumes that if the value is a string, it *must* be an
            // hexadecimal number. But that's no the case when the data comes from json, where
            // a string is assumed to be just the representation of a number, not necessarily
            // hexadecimal. Therefore, when in json we have a property like:
            //
            //    "gasPrice": "12"
            //
            // "gasPrice" gets encoded by web3j as "0x12", when it should be "0x0c". So the fix:
            //
            // Check for the case where we are instantiating a numeric type from a string. Assume
            // the value is hexadecimal *only* if it starts with "0x". That's implemented by the
            // method numericFromString.
            TypeReference tyRef = TypeReference.makeTypeReference(type);
            if (NumericType.class.isAssignableFrom(tyRef.getClassType()) && value.isTextual()) {
                return numericFromString(tyRef, value.asText());
            }
            return TypeDecoder.instantiateType(type, value.asText());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Construct a Solidity numeric type from a string value, inferring whether
     * the string is an hex or base 10 number.
     *
     * @param tyRef reference for numeric type to instantiate
     * @param value string representation of value to associate to the numeric type
     * @return instantiated Solidity numeric type
     * @throws Exception if instantiation fails
     */
    private Type numericFromString(TypeReference tyRef, String value) throws Exception {
        int radix = value.startsWith("0x")? 16 : 10;
        BigInteger n = new BigInteger(value, radix);
        return TypeDecoder.instantiateType(tyRef, n);
    }

    /**
     * Encode the given data node.
     *
     * @param primaryType type of the data node
     * @param data data node to encode
     * @param types all types declared in typed data
     *
     * @return encoding of the given data node
     */
    byte[] encodeData(String primaryType, JsonNode data, JsonNode types) {
        List<Type> encoded = new ArrayList<>();
        encoded.add(new Bytes32(hashType(primaryType, types)));

        for (JsonNode tyDef: types.withArray(primaryType)) {
            String name = tyDef.get("name").asText();
            String type = tyDef.get("type").asText();
            JsonNode value = data.has(name)? data.get(name) : null;
            encoded.add(encodeField(type, value, types));
        }

        DefaultFunctionEncoder encoder = new DefaultFunctionEncoder();
        String abiEncoded = encoder.encodeParameters(encoded);
        return Hex.decode(abiEncoded);
    }

    /**
     * Compute the hashed encoding of the given type.
     *
     * @param primaryType type to encode and hash
     * @param types all types declared in typed data
     * @return hashed encoding for the given type
     */
    byte[] hashType(String primaryType, JsonNode types) {
        return HashUtil.keccak256(encodeType(primaryType, types).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Compute the hashed encoding of the given data node.
     *
     * @param primaryType type of the data node
     * @param domain data node the encode and hash
     * @param types all types declared in typed data
     * @return hashed encoding for the given data node
     */
    byte[] hashStruct(String primaryType, JsonNode domain, JsonNode types) {
        return HashUtil.keccak256(encodeData(primaryType, domain, types));
    }

    /**
     * Encode the given type data according to the EIP712 specification, version 4.
     * @param typedData type data to encode, as a Json node
     * @return encoding of the given typed data
     */
    public byte[] epi712encode_v4(JsonNode typedData) {
        JsonNode sanitized = sanitize(typedData);
        ByteArrayDataOutput buf = ByteStreams.newDataOutput();
        buf.writeByte(0x19);
        buf.writeByte(0x01);

        buf.write(hashStruct(EIP712Domain, sanitized.get("domain"), sanitized.with("types")));
        if (!sanitized.get("primaryType").asText().equals(EIP712Domain)) {
            buf.write(hashStruct(
                    sanitized.get("primaryType").asText(),
                    sanitized.get("message"),
                    sanitized.with("types")));
        }
        return HashUtil.keccak256(buf.toByteArray());
    }
}

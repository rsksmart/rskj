/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ libr`ary, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.core;

import co.rsk.core.RskAddress;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.core.types.bytes.BytesSlice;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.crypto.HashUtil;
import org.ethereum.solidity.SolidityType;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.ethereum.util.ByteUtil.longToBytesNoLeadZeroes;

/**
 * Creates a contract function call transaction.
 * Serializes arguments according to the function ABI .
 * <p>
 * Created by Anton Nashatyrev on 25.08.2015.
 */
public class CallTransaction {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);

    public static Transaction createRawTransaction(long nonce, long gasPrice, long gasLimit, RskAddress toAddress,
                                                   long value, byte[] data, byte chainId) {
        return Transaction
                .builder()
                .nonce(longToBytesNoLeadZeroes(nonce))
                .gasPrice(longToBytesNoLeadZeroes(gasPrice))
                .gasLimit(longToBytesNoLeadZeroes(gasLimit))
                .destination(toAddress.equals(RskAddress.nullAddress()) ? null : toAddress.getBytes())
                .value(longToBytesNoLeadZeroes(value))
                .data(data)
                .chainId(chainId)
                .build();
    }


    public static Transaction createCallTransaction(long nonce, long gasPrice, long gasLimit, RskAddress toAddress,
                                                    long value, Function callFunc, byte chainId, Object... funcArgs) {

        byte[] callData = callFunc.encode(funcArgs);
        return createRawTransaction(nonce, gasPrice, gasLimit, toAddress, value, callData, chainId);
    }

    /**
     * Generic ABI type
     */
    public abstract static class Type {
        protected String name;

        public Type(String name) {
            this.name = name;
        }

        /**
         * The type name as it was specified in the interface description
         */
        public String getName() {
            return name;
        }

        /**
         * The canonical type name (used for the method signature creation)
         * E.g. 'int' - canonical 'int256'
         */
        public String getCanonicalName() {
            return getName();
        }

        @JsonCreator
        public static Type getType(String typeName) {
            if ("bool".equals(typeName)) {
                return new BoolType();
            }

            if (typeName.startsWith("int") || typeName.startsWith("uint")) {
                return new IntType(typeName);
            }

            throw new RuntimeException("Unknown type: " + typeName);
        }

        /**
         * Encodes the value according to specific type rules
         *
         * @param value
         */
        public abstract byte[] encode(Object value);

        public abstract Object decode(BytesSlice encoded, int offset);

        public Object decode(BytesSlice encoded) {
            return decode(encoded, 0);
        }

        /**
         * @return fixed size in bytes. For the dynamic types returns IntType.getFixedSize()
         * which is effectively the int offset to dynamic data
         */
        public int getFixedSize() {
            return 32;
        }

        public boolean isDynamicType() {
            return false;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    public static class IntType extends Type {
        public IntType(String name) {
            super(name);
        }

        @Override
        public String getCanonicalName() {
            if ("int".equals(getName())) {
                return "int256";
            }

            if ("uint".equals(getName())) {
                return "uint256";
            }

            return super.getCanonicalName();
        }

        @Override
        public byte[] encode(Object value) {
            BigInteger bigInt;

            if (value instanceof String) {
                String s = ((String) value).toLowerCase().trim();
                int radix = 10;
                if (s.startsWith("0x")) {
                    s = s.substring(2);
                    radix = 16;
                } else if (s.contains("a") || s.contains("b") || s.contains("c") ||
                        s.contains("d") || s.contains("e") || s.contains("f")) {
                    radix = 16;
                }
                bigInt = new BigInteger(s, radix);
            } else if (value instanceof BigInteger) {
                bigInt = (BigInteger) value;
            } else if (value instanceof Number) {
                bigInt = new BigInteger(value.toString());
            } else {
                throw new RuntimeException("Invalid value for type '" + this + "': " + value + " (" + value.getClass() + ")");
            }
            return encodeInt(bigInt);
        }

        @Override
        public Object decode(BytesSlice encoded, int offset) {
            return decodeInt(encoded, offset);
        }

        public static BigInteger decodeInt(BytesSlice encoded, int offset) {
            return new BigInteger(encoded.copyArrayOfRange(offset, offset + 32));
        }

        public static byte[] encodeInt(int i) {
            return encodeInt(new BigInteger("" + i));
        }

        public static byte[] encodeInt(BigInteger bigInt) {
            byte[] ret = new byte[32];
            Arrays.fill(ret, bigInt.signum() < 0 ? (byte) 0xFF : 0);
            byte[] bytes = bigInt.toByteArray();
            System.arraycopy(bytes, 0, ret, 32 - bytes.length, bytes.length);
            return ret;
        }
    }

    public static class BoolType extends IntType {
        public BoolType() {
            super("bool");
        }

        @Override
        public byte[] encode(Object value) {
            if (!(value instanceof Boolean)) {
                throw new RuntimeException("Wrong value for bool type: " + value);
            }
            return super.encode(value == Boolean.TRUE ? 1 : 0);
        }

        @Override
        public Object decode(BytesSlice encoded, int offset) {
            return Boolean.valueOf(((Number) super.decode(encoded, offset)).intValue() != 0);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Param {
        public Boolean indexed;
        public String name;
        public SolidityType type;

        public Param() {
        }

        public Param(Boolean indexed, String name, SolidityType type) {
            this.indexed = indexed;
            this.name = name;
            this.type = type;
        }

        @JsonGetter("type")
        public String getType() {
            return type.getName();
        }
    }

    public enum FunctionType {
        constructor,
        function,
        event,
        fallback
    }

    public static class Function {
        public boolean anonymous;
        public boolean constant;
        public boolean payable;
        public String name = "";
        public Param[] inputs = new Param[0];
        public Param[] outputs = new Param[0];
        public FunctionType type;

        private Function() {
        }

        public byte[] encode(Object... args) {
            return ByteUtil.merge(encodeSignature(), encodeArguments(args));
        }

        public byte[] encodeArguments(Param[] params, Object... args) {
            if (args.length > params.length) {
                throw new RuntimeException("Too many arguments: " + args.length + " > " + params.length);
            }

            int staticSize = 0;
            int dynamicCnt = 0;
            // calculating static size and number of dynamic params
            for (int i = 0; i < args.length; i++) {
                Param param = params[i];
                if (param.type.isDynamicType()) {
                    dynamicCnt++;
                }
                staticSize += param.type.getFixedSize();
            }

            byte[][] bb = new byte[args.length + dynamicCnt][];

            int curDynamicPtr = staticSize;
            int curDynamicCnt = 0;
            for (int i = 0; i < args.length; i++) {
                Param param = params[i];
                if (param.type.isDynamicType()) {
                    byte[] dynBB = param.type.encode(args[i]);
                    bb[i] = IntType.encodeInt(curDynamicPtr);
                    bb[args.length + curDynamicCnt] = dynBB;
                    curDynamicCnt++;
                    curDynamicPtr += dynBB.length;
                } else {
                    bb[i] = param.type.encode(args[i]);
                }
            }
            return ByteUtil.merge(bb);
        }

        public byte[] encodeArguments(Object... args) {
            return encodeData(inputs, args);
        }

        //args order should be the same as function params order
        public byte[][] encodeEventTopics(Object... args) {
            checkFunctionType(FunctionType.event);

            Param[] topicInputs = Arrays.stream(inputs).filter(i -> i.indexed).toArray(Param[]::new);
            int topicsCount = topicInputs.length;
            checkArgumentsCount(topicsCount, args.length);

            topicsCount++;  //Plus one for the event signature
            byte[][] topics = new byte[topicsCount][];
            int topicIndex = 1;
            topics[0] = this.encodeSignatureLong();
            for (int i = 0; i < args.length; i++) {
                Param param = topicInputs[i];
                if (param.type.isDynamicType()) {
                    //Dynamic data types nor array (dynamic or static) are currently not supported as topics
                    topics[topicIndex] = HashUtil.keccak256(param.type.encode(args[i]));
                } else {
                    topics[topicIndex] = param.type.encode(args[i]);
                }
                topicIndex++;
            }

            return topics;
        }

        //args order should be the same as function params order
        public byte[] encodeEventData(Object... args) {
            checkFunctionType(FunctionType.event);

            Param[] dataInputs = Arrays.stream(inputs).filter(i -> !i.indexed).toArray(Param[]::new);
            checkArgumentsCount(dataInputs.length, args.length);

            return encodeData(dataInputs, args);
        }

        public Object[] decodeEventData(byte[] encodedData) {
            checkFunctionType(FunctionType.event);
            Param[] dataInputs = Arrays.stream(inputs).filter(i -> !i.indexed).toArray(Param[]::new);

            return decode(Bytes.of(encodedData), dataInputs);
        }

        private void checkFunctionType(FunctionType expected) {
            if (this.type != expected) {
                throw new RuntimeException(String.format("Wrong function type. Expected %s, Received: %s", expected, this.type));
            }
        }

        private void checkArgumentsCount(int expected, int received) {
            if (expected != received) {
                throw new RuntimeException(String.format("Wrong amount of arguments. Expected %d, Received %d", expected, received));
            }
        }

        //args of type byte32 should be passed as Hex string for encoding to work
        private byte[] encodeData(Param[] params, Object... args) {
            if (args.length > params.length) {
                throw new CallTransactionException("Too many arguments: " + args.length + " > " + params.length);
            }

            int staticSize = 0;
            int dynamicCnt = 0;
            // calculating static size and number of dynamic params
            for (int i = 0; i < args.length; i++) {
                Param param = params[i];
                if (param.type.isDynamicType()) {
                    dynamicCnt++;
                }
                staticSize += param.type.getFixedSize();
            }

            byte[][] bb = new byte[args.length + dynamicCnt][];

            int curDynamicPtr = staticSize;
            int curDynamicCnt = 0;
            for (int i = 0; i < args.length; i++) {
                Param param = params[i];
                if (param.type.isDynamicType()) {
                    byte[] dynBB = param.type.encode(args[i]);
                    bb[i] = SolidityType.IntType.encodeInt(curDynamicPtr);
                    bb[args.length + curDynamicCnt] = dynBB;
                    curDynamicCnt++;
                    curDynamicPtr += dynBB.length;
                } else {
                    bb[i] = param.type.encode(args[i]);
                }
            }
            return ByteUtil.merge(bb);
        }

        public byte[] encodeOutputs(Object... args) {
            return encodeArguments(outputs, args);
        }

        private Object[] decode(BytesSlice encoded, Param[] params) {
            Object[] ret = new Object[params.length];

            int off = 0;
            for (int i = 0; i < params.length; i++) {
                if (params[i].type.isDynamicType()) {
                    ret[i] = params[i].type.decode(encoded, IntType.decodeInt(encoded, off).intValue());
                } else {
                    ret[i] = params[i].type.decode(encoded, off);
                }
                off += params[i].type.getFixedSize();
            }
            return ret;
        }

        public Object[] decode(byte[] encoded) {
            return decode(Bytes.of(encoded).slice(4, encoded.length), inputs);
        }

        public Object[] decodeResult(byte[] encodedRet) {
            return decode(Bytes.of(encodedRet), outputs);
        }

        public String formatSignature() {
            StringBuilder paramsTypes = new StringBuilder();
            for (Param param : inputs) {
                paramsTypes.append(param.type.getCanonicalName()).append(",");
            }

            return format("%s(%s)", name, stripEnd(paramsTypes.toString(), ","));
        }

        public byte[] encodeSignatureLong() {
            String signature = formatSignature();
            return HashUtil.keccak256(signature.getBytes(StandardCharsets.UTF_8));
        }

        public byte[] encodeSignature() {
            return Arrays.copyOfRange(encodeSignatureLong(), 0, 4);
        }

        @Override
        public String toString() {
            return formatSignature();
        }

        public static Function fromJsonInterface(String json) {
            try {
                return DEFAULT_MAPPER.readValue(json, Function.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public static Function fromSignature(String funcName, String... paramTypes) {
            return fromSignature(funcName, paramTypes, new String[0]);
        }

        public static Function fromSignature(String funcName, String[] paramTypes, String[] resultTypes) {
            Function ret = new Function();
            ret.name = funcName;
            ret.constant = false;
            ret.type = FunctionType.function;
            ret.inputs = new Param[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                ret.inputs[i] = new Param();
                ret.inputs[i].name = "param" + i;
                ret.inputs[i].type = SolidityType.getType(paramTypes[i]);
            }
            ret.outputs = new Param[resultTypes.length];
            for (int i = 0; i < resultTypes.length; i++) {
                ret.outputs[i] = new Param();
                ret.outputs[i].name = "res" + i;
                ret.outputs[i].type = SolidityType.getType(resultTypes[i]);
            }
            return ret;
        }

        public static Function fromEventSignature(String eventName, Param[] params) {
            validateEventParams(params);

            Function event = new Function();
            event.name = eventName;
            event.constant = false;
            event.type = FunctionType.event;
            event.inputs = params;
            return event;
        }

        private static void validateEventParams(Param[] params) {
            int topicsCount = 0;
            for (Param param : params) {
                if (Boolean.TRUE.equals(param.indexed)) {
                    topicsCount++;
                    if (param.type.isDynamicType() || param.type.getName().contains("[")) {
                        throw new RuntimeException("Dynamic or array type topics are not supported");
                    }
                }
            }

            if (topicsCount > 3) {
                throw new RuntimeException("Too many indexed params: " + topicsCount + ". Max 3 indexed params allowed.");
            }
        }
    }

    public static class Contract {

        public Contract(String jsonInterface) {
            try {
                functions = new ObjectMapper().readValue(jsonInterface, Function[].class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Function getByName(String name) {
            for (Function function : functions) {
                if (name.equals(function.name)) {
                    return function;
                }
            }
            return null;
        }

        public Function getConstructor() {
            for (Function function : functions) {
                if (function.type == FunctionType.constructor) {
                    return function;
                }
            }
            return null;
        }

        private Function getBySignatureHash(byte[] hash) {
            if (hash.length == 4) {
                for (Function function : functions) {
                    if (FastByteComparisons.equalBytes(function.encodeSignature(), hash)) {
                        return function;
                    }
                }
            } else if (hash.length == 32) {
                for (Function function : functions) {
                    if (FastByteComparisons.equalBytes(function.encodeSignatureLong(), hash)) {
                        return function;
                    }
                }
            } else {
                throw new CallTransactionException("Function signature hash should be 4 or 32 bytes length");
            }
            return null;
        }

        /**
         * Parses function and its arguments from transaction invocation binary data
         */
        public Invocation parseInvocation(byte[] data) {
            if (data.length < 4) {
                throw new CallTransactionException("Invalid data length: " + data.length);
            }
            Function function = getBySignatureHash(Arrays.copyOfRange(data, 0, 4));
            if (function == null) {
                throw new CallTransactionException("Can't find function/event by it signature");
            }
            Object[] args = function.decode(data);
            return new Invocation(this, function, args);
        }

        public Function[] functions;
    }


    /**
     * Represents either function invocation with its arguments
     * or Event instance with its data members
     */
    public static class Invocation {
        public final Contract contract;
        public final Function function;
        public final Object[] args;

        public Invocation(Contract contract, Function function, Object[] args) {
            this.contract = contract;
            this.function = function;
            this.args = args;
        }

        @Override
        public String toString() {
            return "[" + "contract=" + contract +
                    (function.type == FunctionType.event ? ", event=" : ", function=")
                    + function + ", args=" + Arrays.toString(args) + ']';
        }
    }

    public static class CallTransactionException extends RuntimeException {
        public CallTransactionException(String msg) {
            super(msg);
        }
    }
}

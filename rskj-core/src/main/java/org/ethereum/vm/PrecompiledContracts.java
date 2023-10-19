/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package org.ethereum.vm;

import co.rsk.config.RemascConfig;
import co.rsk.config.RemascConfigFactory;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.pcc.altBN128.BN128Addition;
import co.rsk.pcc.altBN128.BN128Multiplication;
import co.rsk.pcc.altBN128.BN128Pairing;
import co.rsk.pcc.altBN128.impls.AbstractAltBN128;
import co.rsk.pcc.blockheader.BlockHeaderContract;
import co.rsk.pcc.bto.HDWalletUtils;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.remasc.RemascContract;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.crypto.cryptohash.Blake2b;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.BIUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.Program;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.ethereum.util.ByteUtil.*;

/**
 * @author Roman Mandeleil
 * @since 09.01.2015
 */
public class PrecompiledContracts {

    public static final String ECRECOVER_ADDR_STR = "0000000000000000000000000000000000000001";
    public static final String SHA256_ADDR_STR = "0000000000000000000000000000000000000002";
    public static final String RIPEMPD160_ADDR_STR = "0000000000000000000000000000000000000003";
    public static final String IDENTITY_ADDR_STR = "0000000000000000000000000000000000000004";
    public static final String BIG_INT_MODEXP_ADDR_STR = "0000000000000000000000000000000000000005";
    public static final String ALT_BN_128_ADD_ADDR_STR = "0000000000000000000000000000000000000006";
    public static final String ALT_BN_128_MUL_ADDR_STR = "0000000000000000000000000000000000000007";
    public static final String ALT_BN_128_PAIRING_ADDR_STR = "0000000000000000000000000000000000000008";
    public static final String BLAKE2F_ADDR_STR = "0000000000000000000000000000000000000009";
    public static final String BRIDGE_ADDR_STR = "0000000000000000000000000000000001000006";
    public static final String REMASC_ADDR_STR = "0000000000000000000000000000000001000008";
    public static final String HD_WALLET_UTILS_ADDR_STR = "0000000000000000000000000000000001000009";
    public static final String BLOCK_HEADER_ADDR_STR = "0000000000000000000000000000000001000010";
    public static final String ENVIRONMENT_ADDR_STR = "0000000000000000000000000000000001000011";

    public static final DataWord ECRECOVER_ADDR_DW = DataWord.valueFromHex(ECRECOVER_ADDR_STR);
    public static final DataWord SHA256_ADDR_DW = DataWord.valueFromHex(SHA256_ADDR_STR);
    public static final DataWord RIPEMPD160_ADDR_DW = DataWord.valueFromHex(RIPEMPD160_ADDR_STR);
    public static final DataWord IDENTITY_ADDR_DW = DataWord.valueFromHex(IDENTITY_ADDR_STR);
    public static final DataWord BIG_INT_MODEXP_ADDR_DW = DataWord.valueFromHex(BIG_INT_MODEXP_ADDR_STR);
    public static final DataWord ALT_BN_128_ADD_DW = DataWord.valueFromHex(ALT_BN_128_ADD_ADDR_STR);
    public static final DataWord ALT_BN_128_MUL_DW = DataWord.valueFromHex(ALT_BN_128_MUL_ADDR_STR);
    public static final DataWord ALT_BN_128_PAIRING_DW = DataWord.valueFromHex(ALT_BN_128_PAIRING_ADDR_STR);
    public static final DataWord BLAKE2F_ADDR_DW = DataWord.valueFromHex(BLAKE2F_ADDR_STR);
    public static final DataWord BRIDGE_ADDR_DW = DataWord.valueFromHex(BRIDGE_ADDR_STR);
    public static final DataWord REMASC_ADDR_DW = DataWord.valueFromHex(REMASC_ADDR_STR);
    public static final DataWord HD_WALLET_UTILS_ADDR_DW = DataWord.valueFromHex(HD_WALLET_UTILS_ADDR_STR);
    public static final DataWord BLOCK_HEADER_ADDR_DW = DataWord.valueFromHex(BLOCK_HEADER_ADDR_STR);
    public static final DataWord ENVIRONMENT_ADDR_DW = DataWord.valueFromHex(ENVIRONMENT_ADDR_STR);

    public static final RskAddress ECRECOVER_ADDR = new RskAddress(ECRECOVER_ADDR_DW);
    public static final RskAddress SHA256_ADDR = new RskAddress(SHA256_ADDR_DW);
    public static final RskAddress RIPEMPD160_ADDR = new RskAddress(RIPEMPD160_ADDR_DW);
    public static final RskAddress IDENTITY_ADDR = new RskAddress(IDENTITY_ADDR_DW);
    public static final RskAddress BIG_INT_MODEXP_ADDR = new RskAddress(BIG_INT_MODEXP_ADDR_DW);
    public static final RskAddress ALT_BN_128_ADD_ADDR = new RskAddress(ALT_BN_128_ADD_DW);
    public static final RskAddress ALT_BN_128_MUL_ADDR = new RskAddress(ALT_BN_128_MUL_DW);
    public static final RskAddress ALT_BN_128_PAIRING_ADDR = new RskAddress(ALT_BN_128_PAIRING_DW);
    public static final RskAddress BLAKE2F_ADDR = new RskAddress(BLAKE2F_ADDR_DW);
    public static final RskAddress BRIDGE_ADDR = new RskAddress(BRIDGE_ADDR_DW);
    public static final RskAddress REMASC_ADDR = new RskAddress(REMASC_ADDR_DW);
    public static final RskAddress HD_WALLET_UTILS_ADDR = new RskAddress(HD_WALLET_UTILS_ADDR_STR);
    public static final RskAddress BLOCK_HEADER_ADDR = new RskAddress(BLOCK_HEADER_ADDR_STR);
    public static final RskAddress ENVIRONMENT_ADDR = new RskAddress(ENVIRONMENT_ADDR_STR);

    public static final List<RskAddress> GENESIS_ADDRESSES = Collections.unmodifiableList(Arrays.asList(
            ECRECOVER_ADDR,
            SHA256_ADDR,
            RIPEMPD160_ADDR,
            IDENTITY_ADDR,
            BIG_INT_MODEXP_ADDR,
            BRIDGE_ADDR,
            REMASC_ADDR,
            ENVIRONMENT_ADDR
    ));

    // this maps needs to be updated by hand any time a new pcc is added
    public static final Map<RskAddress, ConsensusRule> CONSENSUS_ENABLED_ADDRESSES = Collections.unmodifiableMap(
            Stream.of(
                    new AbstractMap.SimpleEntry<>(HD_WALLET_UTILS_ADDR, ConsensusRule.RSKIP106),
                    new AbstractMap.SimpleEntry<>(BLOCK_HEADER_ADDR, ConsensusRule.RSKIP119),
                    new AbstractMap.SimpleEntry<>(ALT_BN_128_ADD_ADDR, ConsensusRule.RSKIP137),
                    new AbstractMap.SimpleEntry<>(ALT_BN_128_MUL_ADDR, ConsensusRule.RSKIP137),
                    new AbstractMap.SimpleEntry<>(ALT_BN_128_PAIRING_ADDR, ConsensusRule.RSKIP137),
                    new AbstractMap.SimpleEntry<>(BLAKE2F_ADDR, ConsensusRule.RSKIP153)
            ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
    );

    private static ECRecover ecRecover = new ECRecover();
    private static Sha256 sha256 = new Sha256();
    private static Ripempd160 ripempd160 = new Ripempd160();
    private static Identity identity = new Identity();
    private static BigIntegerModexp bigIntegerModexp = new BigIntegerModexp();
    private static Environment environment = new Environment();
    private final RskSystemProperties config;
    private final BridgeSupportFactory bridgeSupportFactory;
    private final SignatureCache signatureCache;
    private final RemascConfig remascConfig;

    public PrecompiledContracts(RskSystemProperties config,
                                BridgeSupportFactory bridgeSupportFactory,
                                SignatureCache signatureCache) {
        this.config = config;
        this.bridgeSupportFactory = bridgeSupportFactory;
        this.signatureCache = signatureCache;
        this.remascConfig = new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig(config.netName());
    }


    public PrecompiledContract getContractForAddress(ActivationConfig.ForBlock activations, DataWord address) {

        if (address == null) {
            return identity;
        }
        if (address.equals(ECRECOVER_ADDR_DW)) {
            return ecRecover;
        }
        if (address.equals(SHA256_ADDR_DW)) {
            return sha256;
        }
        if (address.equals(RIPEMPD160_ADDR_DW)) {
            return ripempd160;
        }
        if (address.equals(IDENTITY_ADDR_DW)) {
            return identity;
        }
        if (address.equals(ENVIRONMENT_ADDR_DW)) {
            return environment;
        }
        if (address.equals(BRIDGE_ADDR_DW)) {
            return new Bridge(
                    BRIDGE_ADDR,
                    config.getNetworkConstants(),
                    config.getActivationConfig(),
                    bridgeSupportFactory, signatureCache);
        }
        if (address.equals(BIG_INT_MODEXP_ADDR_DW)) {
            return bigIntegerModexp;
        }
        if (address.equals(REMASC_ADDR_DW)) {
            return new RemascContract(REMASC_ADDR, remascConfig, config.getNetworkConstants(), config.getActivationConfig());
        }

        // TODO(mc) reuse CONSENSUS_ENABLED_ADDRESSES
        if (activations.isActive(ConsensusRule.RSKIP119) && address.equals(BLOCK_HEADER_ADDR_DW)) {
            return new BlockHeaderContract(config.getActivationConfig(), BLOCK_HEADER_ADDR);
        }

        if (activations.isActive(ConsensusRule.RSKIP106) && address.equals(HD_WALLET_UTILS_ADDR_DW)) {
            return new HDWalletUtils(config.getActivationConfig(), HD_WALLET_UTILS_ADDR);
        }

        if (activations.isActive(ConsensusRule.RSKIP137) && address.equals(ALT_BN_128_ADD_DW)) {
            return new BN128Addition(activations, AbstractAltBN128.create());
        }

        if (activations.isActive(ConsensusRule.RSKIP137) && address.equals(ALT_BN_128_MUL_DW)) {
            return new BN128Multiplication(activations, AbstractAltBN128.create());
        }

        if (activations.isActive(ConsensusRule.RSKIP137) && address.equals(ALT_BN_128_PAIRING_DW)) {
            return new BN128Pairing(activations, AbstractAltBN128.create());
        }

        if (activations.isActive(ConsensusRule.RSKIP153) && address.equals(BLAKE2F_ADDR_DW)) {
            return new Blake2F();
        }

        return null;
    }

    public abstract static class PrecompiledContract {
        public RskAddress contractAddress;

        public abstract long getGasForData(byte[] data);

        public void init(Transaction tx, Block executionBlock, Repository repository, BlockStore blockStore, ReceiptStore receiptStore, List<LogInfo> logs) {
        }

        public void init(Transaction tx, Block executionBlock, Repository repository, BlockStore blockStore, ReceiptStore receiptStore, List<LogInfo> logs, Environment.CallStackDepth callStackDepth) {
            if(this instanceof Environment) {
                this.init(callStackDepth);
            } else {
                this.init(tx, executionBlock, repository, blockStore, receiptStore, logs);
            }
        }

        public void init(Environment.CallStackDepth callStackDepth) {
        }

        public List<ProgramSubtrace> getSubtraces() {
            return Collections.emptyList();
        }

        public abstract byte[] execute(byte[] data) throws VMException;
    }

    public static class Identity extends PrecompiledContract {

        public Identity() {
        }

        @Override
        public long getGasForData(byte[] data) {

            // gas charge for the execution:
            // minimum 15 and additional 3 for each 32 bytes word (round  up)
            if (data == null) {
                return 15;
            }
            long variableCost = GasCost.multiply(GasCost.add(data.length, 31) / 32, 3);
            return GasCost.add(15, variableCost);
        }

        @Override
        public byte[] execute(byte[] data) {
            return data;
        }
    }

    public static class Environment extends PrecompiledContract {
        public interface CallStackDepth {
            int getCurrentCallDepth();
        }

        private CallStackDepth callStackDepth;

        public Environment() {
        }

        @Override
        public void init(CallStackDepth callStackDepth) {
            super.init(callStackDepth);
            this.callStackDepth = callStackDepth;
        }

        @Override
        public long getGasForData(byte[] data) {
            return 0;
        }

        @Override
        public byte[] execute(byte[] data) {
            return ByteUtil.intToBytes(getCallStackDepth());
        }

        private int getCallStackDepth() {
            return callStackDepth == null ? 0 : callStackDepth.getCurrentCallDepth();
        }
    }

    public static class Sha256 extends PrecompiledContract {


        @Override
        public long getGasForData(byte[] data) {

            // gas charge for the execution:
            // minimum 60 and additional 12 for each 32 bytes word (round  up)
            if (data == null) {
                return 60;
            }
            long variableCost = GasCost.multiply(GasCost.add(data.length, 31) / 32, 12);
            return GasCost.add(60, variableCost);
        }

        @Override
        public byte[] execute(byte[] data) {

            if (data == null) {
                return HashUtil.sha256(ByteUtil.EMPTY_BYTE_ARRAY);
            }
            return HashUtil.sha256(data);
        }
    }


    public static class Ripempd160 extends PrecompiledContract {


        @Override
        public long getGasForData(byte[] data) {

            // TODO Replace magic numbers with constants
            // gas charge for the execution:
            // minimum 600 and additional 120 for each 32 bytes word (round  up)
            if (data == null) {
                return 600;
            }
            long variableCost = GasCost.multiply(GasCost.add(data.length, 31) / 32, 120);
            return GasCost.add(600, variableCost);
        }

        @Override
        public byte[] execute(byte[] data) {

            byte[] result = null;
            if (data == null) {
                result = HashUtil.ripemd160(ByteUtil.EMPTY_BYTE_ARRAY);
            } else {
                result = HashUtil.ripemd160(data);
            }

            return DataWord.valueOf(result).getData();
        }
    }


    public static class ECRecover extends PrecompiledContract {

        @Override
        public long getGasForData(byte[] data) {
            return 3000;
        }

        @Override
        public byte[] execute(byte[] data) throws VMException {

            byte[] h = new byte[32];
            byte[] v = new byte[32];
            byte[] r = new byte[32];
            byte[] s = new byte[32];

            DataWord out = null;

            try {
                System.arraycopy(data, 0, h, 0, 32);
                System.arraycopy(data, 32, v, 0, 32);
                System.arraycopy(data, 64, r, 0, 32);

                int sLength = data.length < 128 ? data.length - 96 : 32;
                System.arraycopy(data, 96, s, 0, sLength);

                if (isValid(r, s, v)) {
                    ECDSASignature signature = ECDSASignature.fromComponents(r, s, v[31]);

                    ECKey key = Secp256k1.getInstance().signatureToKey(h, signature);
                    out = DataWord.valueOf(key.getAddress());
                }
            } catch (Exception any) {
            }

            if (out == null) {
                return new byte[0];
            } else {
                return out.getData();
            }
        }

        private boolean isValid(byte[] rBytes, byte[] sBytes, byte[] vBytes) {

            byte v = vBytes[vBytes.length - 1];
            BigInteger r = new BigInteger(1, rBytes);
            BigInteger s = new BigInteger(1, sBytes);

            return ECDSASignature.validateComponents(r, s, v);
        }
    }

    /**
     * Computes modular exponentiation on big numbers
     * <p>
     * format of data[] array:
     * [length_of_BASE] [length_of_EXPONENT] [length_of_MODULUS] [BASE] [EXPONENT] [MODULUS]
     * where every length is a 32-byte left-padded integer representing the number of bytes.
     * Call data is assumed to be infinitely right-padded with zero bytes.
     * <p>
     * Returns an output as a byte array with the same length as the modulus
     */
    public static class BigIntegerModexp extends PrecompiledContract {

        private static final int BASE = 0;
        private static final int EXPONENT = 1;
        private static final int MODULUS = 2;

        private static final BigInteger GQUAD_DIVISOR = BigInteger.valueOf(20);

        private static final int ARGS_OFFSET = 32 * 3; // addresses length part

        @Override
        public long getGasForData(byte[] data) {
            byte[] safeData = data == null ? EMPTY_BYTE_ARRAY : data;

            int baseLen = parseLen(safeData, BASE);
            int expLen = parseLen(safeData, EXPONENT);
            int modLen = parseLen(safeData, MODULUS);

            long multComplexity = GasCost.toGas(getMultComplexity(Math.max(baseLen, modLen)));

            byte[] expHighBytes;
            try {
                int offset = Math.addExact(ARGS_OFFSET, baseLen);
                expHighBytes = parseBytes(safeData, offset, Math.min(expLen, 32));
            } catch (ArithmeticException e) {
                expHighBytes = ByteUtil.EMPTY_BYTE_ARRAY;
            }

            long adjExpLen = getAdjustedExponentLength(expHighBytes, expLen);

            BigInteger gas = BigInteger.valueOf(multComplexity).multiply(
                    BigInteger.valueOf(Math.max(adjExpLen, 1))).divide(GQUAD_DIVISOR);

            return GasCost.toGas(gas);
        }

        @Override
        public byte[] execute(byte[] data) {

            if (data == null) {
                return EMPTY_BYTE_ARRAY;
            }

            try {
                int baseLen = parseLen(data, BASE);
                int expLen = parseLen(data, EXPONENT);
                int modLen = parseLen(data, MODULUS);

                int expOffset = Math.addExact(ARGS_OFFSET, baseLen);
                int modOffset = Math.addExact(expOffset, expLen);

                // whenever an offset gets too big we will get BigInteger.ZERO back
                BigInteger base = parseArg(data, ARGS_OFFSET, baseLen);
                BigInteger exp = parseArg(data, expOffset, expLen);
                BigInteger mod = parseArg(data, modOffset, modLen);

                if (mod.equals(BigInteger.ZERO)) {
                    // Modulo 0 is undefined, return zero
                    return ByteUtil.leftPadBytes(ByteUtil.EMPTY_BYTE_ARRAY, modLen);
                }

                byte[] res = stripLeadingZeroes(base.modPow(exp, mod).toByteArray());
                return ByteUtil.leftPadBytes(res, modLen);
            } catch (ArithmeticException e) {
                return ByteUtil.EMPTY_BYTE_ARRAY;
            }
        }

        private long getMultComplexity(long x) {

            long x2 = x * x;

            if (x <= 64) {
                return x2;
            }
            if (x <= 1024) {
                return x2 / 4 + 96 * x - 3072;
            }

            return x2 / 16 + 480 * x - 199680;
        }

        private long getAdjustedExponentLength(byte[] expHighBytes, long expLen) {

            int leadingZeros = numberOfLeadingZeros(expHighBytes);
            int highestBit = 8 * expHighBytes.length - leadingZeros;

            // set index basement to zero
            if (highestBit > 0) {
                highestBit--;
            }

            if (expLen <= 32) {
                return highestBit;
            } else {
                return 8 * (expLen - 32) + highestBit;
            }
        }

        private int parseLen(byte[] data, int idx) {
            byte[] bytes = parseBytes(data, 32 * idx, 32);
            return DataWord.valueOf(bytes).intValueSafe();
        }

        private BigInteger parseArg(byte[] data, int offset, int len) {
            byte[] bytes = parseBytes(data, offset, len);
            return BIUtil.toBI(bytes);
        }

    }


    public static class Blake2F extends PrecompiledContract {

        private static final int BLAKE2F_INPUT_LEN = 213;
        private static final byte BLAKE2F_FINAL_BLOCK_BYTES = 1;
        private static final byte BLAKE2F_NON_FINAL_BLOCK_BYTES = 0;

        public static final String BLAKE2F_ERROR_INPUT_LENGHT = "input length for BLAKE2 F precompile should be exactly 213 bytes";
        public static final String BLAKE2F_ERROR_FINAL_BLOCK_BYTES = "incorrect final block indicator flag";

        @Override
        public long getGasForData(byte[] data) {
            if (data.length != BLAKE2F_INPUT_LEN) {
                // Input is malformed, we can't read the number of rounds.
                // Precompile can't be executed so we set its price to 0.
                return 0;
            }

            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            return bb.getInt() & 0x00000000ffffffffL;
        }

        @Override
        public byte[] execute(byte[] data) throws VMException {
            if (data.length != BLAKE2F_INPUT_LEN) {
                throw new VMException(BLAKE2F_ERROR_INPUT_LENGHT);
            }
            if (data[212] != BLAKE2F_NON_FINAL_BLOCK_BYTES && data[212] != BLAKE2F_FINAL_BLOCK_BYTES) {
                throw new VMException(BLAKE2F_ERROR_FINAL_BLOCK_BYTES);
            }

            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            long rounds = bb.getInt() & 0x00000000ffffffffL;

            long[] h = new long[8];
            bb.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 8; i++) {
                h[i] = bb.getLong();
            }

            long[] m = new long[16];
            for (int i = 0; i < 16; i++) {
                m[i] = bb.getLong();
            }

            long[] t = new long[2];
            t[0] = bb.getLong();
            t[1] = bb.getLong();

            boolean f = (data[212] == BLAKE2F_FINAL_BLOCK_BYTES);

            Blake2b.functionF(h, m, t, f, rounds);
            ByteBuffer output = ByteBuffer.allocate(64);
            output.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 8; i++) {
                output.putLong(h[i]);
            }
            return output.array();
        }
    }

}

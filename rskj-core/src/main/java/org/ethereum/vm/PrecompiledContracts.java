package org.ethereum.vm;
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


import co.rsk.config.RemascConfigFactory;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.peg.Bridge;
import co.rsk.peg.SamplePrecompiledContract;
import co.rsk.remasc.RemascContract;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.BIUtil;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.util.ByteUtil.*;



/**
 * @author Roman Mandeleil
 * @since 09.01.2015
 */
public class PrecompiledContracts {

    public static final String ECRECOVER_ADDR = "0000000000000000000000000000000000000001";
    public static final String SHA256_ADDR = "0000000000000000000000000000000000000002";
    public static final String RIPEMPD160_ADDR = "0000000000000000000000000000000000000003";
    public static final String IDENTITY_ADDR_STR = "0000000000000000000000000000000000000004";
    public static final String BIG_INT_MODEXP_ADDR = "0000000000000000000000000000000000000005";
    public static final String SAMPLE_ADDR_STR = "0000000000000000000000000000000001000005";
    public static final String BRIDGE_ADDR_STR = "0000000000000000000000000000000001000006";
    public static final String REMASC_ADDR_STR = "0000000000000000000000000000000001000008";

    public static final RskAddress BRIDGE_ADDR = new RskAddress(BRIDGE_ADDR_STR);
    public static final RskAddress IDENTITY_ADDR = new RskAddress(IDENTITY_ADDR_STR);
    public static final RskAddress REMASC_ADDR = new RskAddress(REMASC_ADDR_STR);
    public static final RskAddress SAMPLE_ADDR = new RskAddress(SAMPLE_ADDR_STR);

    public static final DataWord BRIDGE_ADDR_DW = new DataWord(BRIDGE_ADDR.getBytes());
    public static final DataWord IDENTITY_ADDR_DW = new DataWord(IDENTITY_ADDR.getBytes());
    public static final DataWord REMASC_ADDR_DW  = new DataWord(REMASC_ADDR.getBytes());
    public static final DataWord SAMPLE_ADDR_DW = new DataWord(SAMPLE_ADDR.getBytes());
    public static final DataWord ECRECOVER_ADDR_DW = new DataWord(ECRECOVER_ADDR);
    public static final DataWord RIPEMPD160_ADDR_DW = new DataWord(RIPEMPD160_ADDR);
    public static final DataWord BIG_INT_MODEXP_ADDR_DW = new DataWord(BIG_INT_MODEXP_ADDR);
    public static final DataWord SHA256_ADDR_DW = new DataWord(SHA256_ADDR);

    private static ECRecover ecRecover = new ECRecover();
    private static Sha256 sha256 = new Sha256();
    private static Ripempd160 ripempd160 = new Ripempd160();
    private static Identity identity = new Identity();
    private static SamplePrecompiledContract sample = new SamplePrecompiledContract(SAMPLE_ADDR);
    private static BigIntegerModexp bigIntegerModexp = new BigIntegerModexp();

    private final RskSystemProperties config;

    public PrecompiledContracts(RskSystemProperties config) {
        this.config = config;
    }


    public PrecompiledContract getContractForAddress(BlockchainConfig blockchainConfig, DataWord address) {

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
        // RSKIP-93 removes this contract completely
        if (address.equals(SAMPLE_ADDR_DW) && !blockchainConfig.isRskip93()) {
            return sample;
        }
        if (address.equals(BRIDGE_ADDR_DW)) {
            return new Bridge(config, BRIDGE_ADDR);
        }
        if (address.equals(BIG_INT_MODEXP_ADDR_DW)) {
            return bigIntegerModexp;
        }
        if (address.equals(REMASC_ADDR_DW)) {
            return new RemascContract(config, new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig(config.netName()), REMASC_ADDR);
        }

        return null;
    }

    public abstract static class PrecompiledContract {
        public RskAddress contractAddress;

        public abstract long getGasForData(byte[] data);

        public void init(Transaction tx, Block executionBlock, Repository repository, BlockStore blockStore, ReceiptStore receiptStore, List<LogInfo> logs) {}

        public abstract byte[] execute(byte[] data);
    }

    public static class Identity extends PrecompiledContract {

        public Identity() {
        }

        @Override
        public long getGasForData(byte[] data) {

            // gas charge for the execution:
            // minimum 1 and additional 1 for each 32 bytes word (round  up)
            if (data == null) {
                return 15;
            }
            return 15l + (data.length + 31) / 32 * 3;
        }

        @Override
        public byte[] execute(byte[] data) {
            return data;
        }
    }

    public static class Sha256 extends PrecompiledContract {


        @Override
        public long getGasForData(byte[] data) {

            // gas charge for the execution:
            // minimum 50 and additional 50 for each 32 bytes word (round  up)
            if (data == null) {
                return 60;
            }
            return 60l + (data.length + 31) / 32 * 12;
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
            // minimum 50 and additional 50 for each 32 bytes word (round  up)
            if (data == null) {
                return 600;
            }
            return 600l + (data.length + 31) / 32 * 120;
        }

        @Override
        public byte[] execute(byte[] data) {

            byte[] result = null;
            if (data == null) {
                result = HashUtil.ripemd160(ByteUtil.EMPTY_BYTE_ARRAY);
            }
            else {
                result = HashUtil.ripemd160(data);
            }

            return new DataWord(result).getData();
        }
    }


    public static class ECRecover extends PrecompiledContract {

        @Override
        public long getGasForData(byte[] data) {
            return 3000;
        }

        @Override
        public byte[] execute(byte[] data) {

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
                    ECKey.ECDSASignature signature = ECKey.ECDSASignature.fromComponents(r, s, v[31]);

                    ECKey key = ECKey.signatureToKey(h, signature);
                    out = new DataWord(key.getAddress());
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

            return ECKey.ECDSASignature.validateComponents(r, s, v);
        }
    }

    /**
     * Computes modular exponentiation on big numbers
     *
     * format of data[] array:
     * [length_of_BASE] [length_of_EXPONENT] [length_of_MODULUS] [BASE] [EXPONENT] [MODULUS]
     * where every length is a 32-byte left-padded integer representing the number of bytes.
     * Call data is assumed to be infinitely right-padded with zero bytes.
     *
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
            byte[] safeData = data==null?EMPTY_BYTE_ARRAY:data;

            int baseLen = parseLen(safeData, BASE);
            int expLen = parseLen(safeData, EXPONENT);
            int modLen = parseLen(safeData, MODULUS);

            long multComplexity = getMultComplexity(Math.max(baseLen, modLen));

            byte[] expHighBytes;
            try {
                int offset = Math.addExact(ARGS_OFFSET, baseLen);
                expHighBytes = parseBytes(safeData, offset, Math.min(expLen, 32));
            }
            catch (ArithmeticException e) {
                expHighBytes = ByteUtil.EMPTY_BYTE_ARRAY;
            }

            long adjExpLen = getAdjustedExponentLength(expHighBytes, expLen);

            // use big numbers to stay safe in case of overflow
            BigInteger gas = BigInteger.valueOf(multComplexity)
                    .multiply(BigInteger.valueOf(Math.max(adjExpLen, 1)))
                    .divide(GQUAD_DIVISOR);


            return gas.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
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
            return new DataWord(bytes).intValueSafe();
        }

        private BigInteger parseArg(byte[] data, int offset, int len) {
            byte[] bytes = parseBytes(data, offset, len);
            return BIUtil.toBI(bytes);
        }

    }

}

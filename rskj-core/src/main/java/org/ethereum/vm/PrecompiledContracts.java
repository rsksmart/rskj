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
import co.rsk.peg.Bridge;
import co.rsk.peg.SamplePrecompiledContract;
import co.rsk.remasc.RemascContract;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.util.ModexpUtil;

import java.math.BigInteger;
import java.util.List;

/**
 * @author Roman Mandeleil
 * @since 09.01.2015
 */
public class PrecompiledContracts {

    public static final String ECRECOVER_ADDR = "0000000000000000000000000000000000000000000000000000000000000001";
    public static final String SHA256_ADDR = "0000000000000000000000000000000000000000000000000000000000000002";
    public static final String RIPEMPD160_ADDR = "0000000000000000000000000000000000000000000000000000000000000003";
    public static final String IDENTITY_ADDR = "0000000000000000000000000000000000000000000000000000000000000004";
    public static final String SAMPLE_ADDR = "0000000000000000000000000000000000000000000000000000000001000005";
    public static final String BRIDGE_ADDR = "0000000000000000000000000000000001000006";
    public static final String MODEXP_ADDR = "0000000000000000000000000000000000000000000000000000000001000007";
    public static final String REMASC_ADDR = "0000000000000000000000000000000001000008";

    private static final String RSK_NATIVECONTRACT_REQUIREDPREFIX = "000000000000000000000000";
    private static ECRecover ecRecover = new ECRecover();
    private static Sha256 sha256 = new Sha256();
    private static Ripempd160 ripempd160 = new Ripempd160();
    private static Identity identity = new Identity();
    private static SamplePrecompiledContract sample = new SamplePrecompiledContract(SAMPLE_ADDR);
    private static Modexp modexp = new Modexp();

    public static PrecompiledContract getContractForAddress(DataWord address) {

        if (address == null) {
            return identity;
        }
        if (address.isHex(ECRECOVER_ADDR)) {
            return ecRecover;
        }
        if (address.isHex(SHA256_ADDR)) {
            return sha256;
        }
        if (address.isHex(RIPEMPD160_ADDR)) {
            return ripempd160;
        }
        if (address.isHex(IDENTITY_ADDR)) {
            return identity;
        }
        if (address.isHex(SAMPLE_ADDR)) {
            return sample;
        }
        if (address.isHex(BRIDGE_ADDR) || address.isHex(RSK_NATIVECONTRACT_REQUIREDPREFIX + BRIDGE_ADDR)) {
            return new Bridge(BRIDGE_ADDR);
        }
        if (address.isHex(MODEXP_ADDR)) {
            return modexp;
        }
        if (address.isHex(REMASC_ADDR) || address.isHex(RSK_NATIVECONTRACT_REQUIREDPREFIX + REMASC_ADDR))
            return new RemascContract(REMASC_ADDR, new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig(RskSystemProperties.RSKCONFIG.netName()));

        return null;
    }

    public abstract static class PrecompiledContract {
        public String contractAddress;

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
            return 15 + (data.length + 31) / 32 * 3;
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
            return 60 + (data.length + 31) / 32 * 12;
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
            return 600 + (data.length + 31) / 32 * 120;
        }

        @Override
        public byte[] execute(byte[] data) {

            byte[] result = null;
            if (data == null) {
                result = HashUtil.ripemd160(ByteUtil.EMPTY_BYTE_ARRAY);
            }
            else result = HashUtil.ripemd160(data);

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

                    ECKey key = ECKey.signatureToKey(h, signature.toBase64());
                    out = new DataWord(key.getAddress());
                }
            } catch (Throwable any) {
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


    public static class Modexp extends PrecompiledContract {

        public Modexp() {
            super();
        }

        @Override
        public byte[] execute(byte[] data) throws RuntimeException {
            ModexpUtil util = new ModexpUtil(data);

            BigInteger b = util.base();
            BigInteger e = util.exp();
            BigInteger m = util.mod();

            BigInteger res = b.modPow(e, m);

            return util.encodeResult(res);
        }

        @Override
        public long getGasForData(byte[] data) {
            return data != null ? 4 * data.length : 400;
        }

    }
}

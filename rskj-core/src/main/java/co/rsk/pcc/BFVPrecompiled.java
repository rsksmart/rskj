package co.rsk.pcc;

import co.rsk.util.HexUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.FhContext;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.rsksmart.BFV;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// todo(fedejinich) refactor duplicated code in add and sub
public class BFVPrecompiled extends PrecompiledContracts.PrecompiledContract {

    private final Op op;
    private final BFV bfv;

    public BFVPrecompiled(Op operation) {
        this.op = operation;
        this.bfv = new BFV();
    }

    @Override
    public long getGasForData(byte[] data) {
        return this.op.gasForData(data);
    }

    @Override
    public byte[] execute(byte[] data) throws VMException {
        byte[] result;
        try {
            result = this.op.executeOperation(data, bfv);
        } catch (Exception e) {
            throw new VMException(e.getMessage());
        }

        return result;
    }

//    public void addFhStore(FhContext fhStore) {
//        this.fhStore = fhStore;
//    }

    public enum Op {
        ADD // addition
        {
            @Override
            public long gasForData(byte[] data) {
                return 1;
            }

            @Override
            public byte[] executeOperation(byte[] data, BFV bfv) {
                byte[] op1Hash = new byte[DataWord.BYTES];
                byte[] op2Hash = new byte[DataWord.BYTES];

                System.arraycopy(data, 0, op1Hash, 0, 32);
                System.arraycopy(data, 32, op2Hash, 0, 32);

                byte[] op1 = FhContext.getInstance().getEncryptedData(op1Hash).getData();
                byte[] op2 = FhContext.getInstance().getEncryptedData(op2Hash).getData();

//                byte[] result;
//                int noiseBudget;
//                if(FhContext.getInstance().enableBenchmark()) {
//                    result = bfv.add(op1, op1.length, op2, op2.length);
////                    // todo(fedejinich) this should be at callToPrecompiledAddress
////                    VotingMocks vm = Op.getMocks();
////                    noiseBudget = bfv.noiseBudget(result, result.length, vm.getBfvSK(), vm.getBfvSK().length);
////                    FhContext.getInstance().addNoiseBudgetBenchmark(noiseBudget);
//                } else {
//                    result = bfv.add(op1, op1.length, op2, op2.length);
//                }

                byte[] result = bfv.add(op1, op1.length, op2, op2.length);
                byte[] hash = Keccak256Helper.keccak256(result);

                FhContext.getInstance().putEncryptedData(hash, result);

                return hash;
            }
        },
        SUB // subtraction
        {
            @Override
            public long gasForData(byte[] data) {
                return 1;
            }

            @Override
            public byte[] executeOperation(byte[] data, BFV bfv) {
                ByteBuffer buffer = ByteBuffer.allocate(data.length);
                buffer.put(data);
                buffer.position(0);

                int op1Len = buffer.getInt();
                int op2Len = buffer.getInt();

                byte[] op1 = new byte[op1Len];
                byte[] op2 = new byte[op2Len];

                buffer.get(op1);
                buffer.get(op2);

                return bfv.sub(op1, op1Len, op2, op2Len);
            }
        },
        MUL // multiplication (with relinearization)
        {
            @Override
            public long gasForData(byte[] data) {
                return 1;
            }

            @Override
            public byte[] executeOperation(byte[] data, BFV bfv) {
                ByteBuffer buffer = ByteBuffer.allocate(data.length);
                buffer.put(data);
                buffer.position(0);

                int op1Len = buffer.getInt();
                int op2Len = buffer.getInt();
                int rkLen = buffer.getInt();

                byte[] op1 = new byte[op1Len];
                byte[] op2 = new byte[op2Len];
                byte[] rk = new byte[rkLen];

                buffer.get(op1);
                buffer.get(op2);
                buffer.get(rk);

                return bfv.mul(op1, op1Len, op2, op2Len, rk, rkLen);
            }
        },
        // todo(fedejinich) remove this precompiled, we don't need it
        TRAN // transcipher from PASTA
        {
            @Override
            public long gasForData(byte[] data) {
                return 1;
            }

            @Override
            public byte[] executeOperation(byte[] data, BFV bfv) {
                ByteBuffer parsedDataBuff = parseData(data);

                byte[] parsedData = parsedDataBuff.array();

                VotingMocks mocks = getMocks();
                byte[] pastaSK = mocks.getPastaSK(); // todo(fedejinich) pasta sk shouldn't be mocked
                byte[] bfvSK = mocks.getBfvSK();
                byte[] rks = mocks.getRk();

                byte[] result = bfv.transcipher(parsedData, parsedData.length, pastaSK, pastaSK.length,
                rks, rks.length, bfvSK, bfvSK.length);

                byte[] hash = Keccak256Helper.keccak256(result);

                FhContext.getInstance().putEncryptedData(hash, result);

                return hash;
            }

            private ByteBuffer parseData(byte[] data) {
                // from hex to byte[]
                ByteBuffer dataBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
                dataBuffer.position(0);

                BigInteger dataLen = parseDataLen(dataBuffer, 0);

                // data
                byte[] dataBytesAux = new byte[dataLen.intValue()];
                dataBuffer.get(dataBytesAux);

                ByteBuffer dataBytes = ByteBuffer.wrap(dataBytesAux).order(ByteOrder.BIG_ENDIAN);

                // todo(fedejinich) improve this, I should convert abi.encodedPacked data (32 bytes each) into long[] into byte[]
                long[] auxLong = new long[dataLen.intValue()/ DataWord.BYTES];//Long.BYTES];
                for (int i = 0; i < auxLong.length; i++) {
                    byte[] a = new byte[DataWord.BYTES];
                    dataBytes.get(a);
                    auxLong[i] = DataWord.valueOf(a).longValue();
                }

                ByteBuffer dataFinalBuff = ByteBuffer
                        .allocate(auxLong.length * Long.BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN);

                for(long l : auxLong) {
                    dataFinalBuff.putLong(l);
                }
                return dataFinalBuff;
            }


        },
        DECRYPT {
            @Override
            public long gasForData(byte[] data) {
                return 1;
            }

            @Override
            public byte[] executeOperation(byte[] data, BFV bfv) {
                VotingMocks votingMocks = getMocks();
                byte[] bfvSK = votingMocks.getBfvSK();

                byte[] encrypted = FhContext.getInstance().getEncryptedData(data).getData();

                byte[] result = bfv.decrypt(encrypted, encrypted.length, bfvSK, bfvSK.length);

                byte[] dec = shrink(result, 4 * Long.BYTES);

                Op.reverse(dec); // as little endian

                return dec;
            }

        },
        ENCRYPTED_PARAMS {
            @Override
            public long gasForData(byte[] data) {
                return 1;
            }

            @Override
            public byte[] executeOperation(byte[] data, BFV bfv) {
                // todo(fedejinich) find a better way to do this
                String param = new String(HexUtils.stringHexToByteArray(HexUtils.toJsonHex(data))).trim();
                byte[] hash = FhContext.getInstance().getEncryptedParam(param).getData();

                return hash;
            }

        };
        private final boolean enableBenchmark = true;
        public static void reverse(byte[] array) {
            if (array == null) {
                return;
            }
            int i = 0;
            int j = array.length - 1;
            byte tmp;
            while (j > i) {
                tmp = array[j];
                array[j] = array[i];
                array[i] = tmp;
                j--;
                i++;
            }
        }
        private static byte[] shrink(byte[] original, int newSize) {
            // Ensure the new size is valid.
            if (newSize < 0 || newSize > original.length) {
                throw new IllegalArgumentException("Invalid new size: " + newSize);
            }

            byte[] shrunk = new byte[newSize];
            System.arraycopy(original, 0, shrunk, 0, newSize);
            return shrunk;
        }
        private static long[] parseDecrypt(byte[] result, int len) {
            long[] res = new long[len];
            ByteBuffer data = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
            for(int i = 0; i < len; i++) {
               long e = data.getLong();
               res[i] = e;
            }

            return res;
        }

        private static BigInteger parseDataLen(ByteBuffer dataBuffer, int offset) {
            byte[] dataLenBytes = new byte[DataWord.BYTES];
            dataBuffer.get(dataLenBytes, offset, DataWord.BYTES);
            BigInteger dataLen = new BigInteger(dataLenBytes);

            return dataLen;
        }

        public static boolean isEmpty(byte[] data) {
            for (byte d : data) {
                if (d != 0) {
                    return false;
                }
            }
            return true;
        }

        public static VotingMocks getMocks() {
            // todo(fedejinich) this should be adapted for each use :)
            try {
//                    TranscipherMocks tMock = new ObjectMapper().readValue(new File(
//                                    "/Users/fedejinich/Projects/rskj/rskj-core/src/test/java/org/ethereum/vm/bfv/test_simple_hhe.json"),
//                    TranscipherMocks.class);
                VotingMocks vm = new ObjectMapper().readValue(new File(
                                "/Users/fedejinich/Projects/rskj/rskj-core/src/test/java/org/ethereum/vm/bfv/votes.json"),
                        VotingMocks.class);
                return vm;
            } catch (IOException e) {
                throw new RuntimeException("Error reading pasta SK" + e.getMessage());
            }
        }

        public abstract long gasForData(byte[] data);
        public abstract byte[] executeOperation(byte[] data, BFV bfv);
    }
}


// todo(fedejinich) benchmark EVM execution time
//   instrument evm for this
// todo(fedejinich) benchmark transciphering time
//   instrument rskj for this
// todo(fedejinich) print charts with server side benchmark


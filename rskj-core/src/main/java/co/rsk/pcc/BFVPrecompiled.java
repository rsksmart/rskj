package co.rsk.pcc;

import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.rsksmart.BFV;

import java.nio.ByteBuffer;

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

    public enum Op {
        ADD // addition
        {
            @Override
            public long gasForData(byte[] data) {
                throw new NotImplementedException("bfv add gas should be implemented");
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

                return bfv.add(op1, op1Len, op2, op2Len);
            }
        },
        SUB // subtraction
        {
            @Override
            public long gasForData(byte[] data) {
                throw new NotImplementedException("bfv sub gas should be implemented");
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
                throw new NotImplementedException("bfv mul gas should be implemented");
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
        TRAN // transcipher from PASTA
        {
            @Override
            public long gasForData(byte[] data) {
                throw new NotImplementedException("bfv tran gas should be implemented");
            }

            @Override
            public byte[] executeOperation(byte[] data, BFV bfv) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                buffer.position(0);

                int encryptedMessageLen = buffer.getInt();
                int pastaSKLen = buffer.getInt();
                int rkLen = buffer.getInt();
                int bfvSkLen = buffer.getInt();

                byte[] encryptedMessage = new byte[encryptedMessageLen];
                byte[] pastaSK = new byte[pastaSKLen];
                byte[] rk = new byte[rkLen];
                byte[] bfvSk = new byte[bfvSkLen];

                buffer.get(encryptedMessage);
                buffer.get(pastaSK);
                buffer.get(rk);
                buffer.get(bfvSk);

                return bfv.transcipher(encryptedMessage, encryptedMessageLen,
                        pastaSK, pastaSKLen, rk, rkLen, bfvSk, bfvSkLen);
            }
        };

        public abstract long gasForData(byte[] data);
        public abstract byte[] executeOperation(byte[] data, BFV bfv);
    }
}

package co.rsk.pcc;

import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;

public class BFVPrecompiled extends PrecompiledContracts.PrecompiledContract {

    private final Op op;

    public BFVPrecompiled(Op operation) {
        this.op = operation;
        // this.bfv = new BFV();
    }

    @Override
    public long getGasForData(byte[] data) {
        return this.op.gasForData(data);
    }

    @Override
    public byte[] execute(byte[] data) throws VMException {
        return this.op.executeOperation(data);
    }

    public enum Op {
        ADD // addition
        {
            @Override
            public long gasForData(byte[] data) {
                throw new NotImplementedException("bfv add gas should be implemented");
            }

            @Override
            public byte[] executeOperation(byte[] data) {
                throw new NotImplementedException("bfv add should be implemented");
            }
        },
        SUB // subtraction
        {
            @Override
            public long gasForData(byte[] data) {
                throw new NotImplementedException("bfv sub gas should be implemented");
            }

            @Override
            public byte[] executeOperation(byte[] data) {
                throw new NotImplementedException("bfv sub should be implemented");
            }
        },
        MUL // multiplication (with relinearization)
        {
            @Override
            public long gasForData(byte[] data) {
                throw new NotImplementedException("bfv mul gas should be implemented");
            }

            @Override
            public byte[] executeOperation(byte[] data) {
                throw new NotImplementedException("bfv mul should be implemented");
            }
        },
        TRAN // transcipher from PASTA
        {
            @Override
            public long gasForData(byte[] data) {
                throw new NotImplementedException("bfv tran gas should be implemented");
            }

            @Override
            public byte[] executeOperation(byte[] data) {
                throw new NotImplementedException("bfv tran should be implemented");
            }
        };

        public abstract long gasForData(byte[] data);
        public abstract byte[] executeOperation(byte[] data);
    }
}

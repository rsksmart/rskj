package co.rsk.pcc;

import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;

public class BFV extends PrecompiledContracts.PrecompiledContract {
    public enum Op {
        ADD, SUB, MUL
    }
    private final Op op;

    public BFV(Op operation) {
        this.op = operation;
    }

    @Override
    public long getGasForData(byte[] data) {
        switch (this.op) {
            case ADD: {
                return bfvAddGas(data);
            }
            case SUB: {
                return bfvSubGas(data);
            }
            case MUL: {
                return bfvMulGas(data);
            }
            default:
                // todo(fedejinich) should i throw an exception?
                return 0;
        }
    }

    private long bfvAddGas(byte[] data) {
       throw new NotImplementedException("bfv add gas should be implemented");
    }
    private long bfvSubGas(byte[] data) {
        throw new NotImplementedException("bfv sub gas should be implemented");
    }
    private long bfvMulGas(byte[] data) {
        throw new NotImplementedException("bfv mul gas should be implemented");
    }

    @Override
    public byte[] execute(byte[] data) throws VMException {
        switch (this.op) {
            case ADD: {
                return bfvAdd(data);
            }
            case SUB: {
                return bfvSub(data);
            }
            case MUL: {
               return bfvMul(data);
            }
            default:
                throw new VMException("no bfv operation provided");
        }
    }

    private byte[] bfvAdd(byte[] data) {
       throw new NotImplementedException("bfv add should be implemented");
    }

    private byte[] bfvSub(byte[] data) {
        throw new NotImplementedException("bfv sub should be implemented");
    }

    private byte[] bfvMul(byte[] data) {
        throw new NotImplementedException("bfv mul should be implemented");
    }
}

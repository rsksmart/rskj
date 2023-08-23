package co.rsk.pcc;

// todo(fedejinich) remove this class
public class TranscipherMocks {
    private byte[] op1Pasta;
    private byte[] op1Real;
    private byte[] op2Pasta;
    private byte[] op2Real;
    private byte[] pastaSK;
    private byte[] rk;
    private byte[] bfvSK;

    public byte[] getOp1Pasta() {
        return op1Pasta;
    }

    public void setOp1Pasta(byte[] op1Pasta) {
        this.op1Pasta = op1Pasta;
    }

    public byte[] getOp1Real() {
        return op1Real;
    }

    public void setOp1Real(byte[] op1Real) {
        this.op1Real = op1Real;
    }

    public byte[] getOp2Pasta() {
        return op2Pasta;
    }

    public void setOp2Pasta(byte[] op2Pasta) {
        this.op2Pasta = op2Pasta;
    }

    public byte[] getOp2Real() {
        return op2Real;
    }

    public void setOp2Real(byte[] op2Real) {
        this.op2Real = op2Real;
    }

    public byte[] getPastaSK() {
        return pastaSK;
    }

    public void setPastaSK(byte[] pastaSK) {
        this.pastaSK = pastaSK;
    }

    public byte[] getRk() {
        return rk;
    }

    public void setRk(byte[] rk) {
        this.rk = rk;
    }

    public byte[] getBfvSK() {
        return bfvSK;
    }

    public void setBfvSK(byte[] bfvSK) {
        this.bfvSK = bfvSK;
    }
}

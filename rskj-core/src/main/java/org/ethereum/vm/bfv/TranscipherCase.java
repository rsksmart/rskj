package org.ethereum.vm.bfv;

public class TranscipherCase {
    private byte[] encryptedMessage;
    private byte[] pastaSK;
    private byte[] expectedResult;
    private byte[] relinearizationKey;
    private byte[] bfvSK;
    private byte[] message;

    public byte[] getRelinearizationKey() {
        return relinearizationKey;
    }

    public void setRelinearizationKey(byte[] relinearizationKey) {
        this.relinearizationKey = relinearizationKey;
    }

    public byte[] getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(byte[] expectedResult) {
        this.expectedResult = expectedResult;
    }

    public byte[] getPastaSK() {
        return pastaSK;
    }

    public void setPastaSK(byte[] pastaSK) {
        this.pastaSK = pastaSK;
    }

    public byte[] getEncryptedMessage() {
        return encryptedMessage;
    }

    public void setEncryptedMessage(byte[] encryptedMessage) {
        this.encryptedMessage = encryptedMessage;
    }

    public byte[] getBfvSK() {
        return bfvSK;
    }

    public void setBfvSK(byte[] bfvSK) {
        this.bfvSK = bfvSK;
    }

    public byte[] getMessage() {
        return message;
    }

    public void setMessage(byte[] message) {
        this.message = message;
    }
}

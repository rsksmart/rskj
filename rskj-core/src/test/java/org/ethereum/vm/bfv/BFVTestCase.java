package org.ethereum.vm.bfv;

public class BFVTestCase {
    private String testName;
    private int operation;
    private byte[] el1;
    private byte[] el2;
    private byte[] expectedResult;
    private byte[] secretKey;
    private byte[] relinearizationKey;

    public int getOperation() {
        return operation;
    }

    public void setOperation(int operation) {
        this.operation = operation;
    }

    public byte[] getEl1() {
        return el1;
    }

    public void setEl1(byte[] el1) {
        this.el1 = el1;
    }

    public byte[] getEl2() {
        return el2;
    }

    public void setEl2(byte[] el2) {
        this.el2 = el2;
    }

    public byte[] getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(byte[] expectedResult) {
        this.expectedResult = expectedResult;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public byte[] getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    public byte[] getRelinearizationKey() {
        return relinearizationKey;
    }

    public void setRelinearizationKey(byte[] relinearizationKey) {
        this.relinearizationKey = relinearizationKey;
    }
}


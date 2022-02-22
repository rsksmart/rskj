package co.rsk.net.handler.quota;

public class TxQuota {

    private long timestamp;
    private double availableVirtualGas;

    public static TxQuota createNew(long maxGasPerSecond) {
        long timestamp = System.currentTimeMillis();
        return new TxQuota(timestamp, maxGasPerSecond);
    }

    private TxQuota(long timestamp, long availableVirtualGas) {
        this.timestamp = timestamp;
        this.availableVirtualGas = availableVirtualGas;
    }

    public boolean acceptVirtualGasConsumption(double virtualGasToConsume) {
        if (this.availableVirtualGas < virtualGasToConsume) {
            return false;
        }

        this.availableVirtualGas -= virtualGasToConsume;
        return true;
    }

    public void refresh(long maxGasPerSecond, long maxQuota) {
        long currentTimestamp = System.currentTimeMillis();

        double timeDiffSeconds = (currentTimestamp - this.timestamp) / 1000d;
        double addToQuota = timeDiffSeconds * maxGasPerSecond;

        this.timestamp = currentTimestamp;
        this.availableVirtualGas = Math.min(maxQuota, this.availableVirtualGas + addToQuota);
    }
}

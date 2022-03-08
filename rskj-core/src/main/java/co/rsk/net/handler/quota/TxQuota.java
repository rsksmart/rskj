package co.rsk.net.handler.quota;

import co.rsk.util.TimeProvider;

public class TxQuota {

    private final TimeProvider timeProvider;
    private long timestamp;
    private double availableVirtualGas;

    public static TxQuota createNew(long maxGasPerSecond, TimeProvider timeProvider) {
        return new TxQuota(maxGasPerSecond, timeProvider);
    }

    private TxQuota(long availableVirtualGas, TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        this.timestamp = this.timeProvider.currentTimeMillis();
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
        long currentTimestamp = this.timeProvider.currentTimeMillis();

        double timeDiffSeconds = (currentTimestamp - this.timestamp) / 1000d;
        double addToQuota = timeDiffSeconds * maxGasPerSecond;
        this.timestamp = currentTimestamp;
        this.availableVirtualGas = Math.min(maxQuota, this.availableVirtualGas + addToQuota);
    }
}

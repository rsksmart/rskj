package co.rsk.mine.minGasPrice;

public abstract class ExchangeRateProvider {
    private final ExchangeRateProviderFactory.XRSourceType type;

    public ExchangeRateProvider(ExchangeRateProviderFactory.XRSourceType type) {
        this.type = type;
    }

    public ExchangeRateProviderFactory.XRSourceType getType() {
        return type;
    }

    public abstract long getPrice(MinGasPriceProvider.ProviderContext context);

}

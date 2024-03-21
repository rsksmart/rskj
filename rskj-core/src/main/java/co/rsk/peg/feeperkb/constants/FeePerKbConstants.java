package co.rsk.peg.feeperkb.constants;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.AddressBasedAuthorizer;

public class FeePerKbConstants {

    protected Coin genesisFeePerKb;

    protected Coin maxFeePerKb;

    protected AddressBasedAuthorizer feePerKbChangeAuthorizer;

    public Coin getGenesisFeePerKb() { return genesisFeePerKb; }

    public Coin getMaxFeePerKb() { return maxFeePerKb; }

    public AddressBasedAuthorizer getFeePerKbChangeAuthorizer() { return feePerKbChangeAuthorizer; }
}

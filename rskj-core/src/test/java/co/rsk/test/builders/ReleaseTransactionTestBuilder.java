package co.rsk.test.builders;

import static co.rsk.peg.federation.FederationFormatVersion.STANDARD_MULTISIG_FEDERATION;
import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.ReleaseTransactionBuilder;
import co.rsk.peg.federation.Federation;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

public class ReleaseTransactionTestBuilder {
    private NetworkParameters networkParameters;
    private Wallet wallet;
    private int federationFormatVersion;
    private Address changeAddress;
    private Coin feePerKb;
    private ActivationConfig.ForBlock activations;

    private ReleaseTransactionTestBuilder() {
        this.networkParameters = mock(NetworkParameters.class);
        this.wallet = mock(Wallet.class);
        this.federationFormatVersion = STANDARD_MULTISIG_FEDERATION.getFormatVersion();
        this.changeAddress = mock(Address.class);
        this.feePerKb = mock(Coin.class);
        this.activations = mock(ActivationConfig.ForBlock.class);
    }

    public static ReleaseTransactionTestBuilder builder() {
        return new ReleaseTransactionTestBuilder();
    }

    public ReleaseTransactionTestBuilder withNetworkParameters(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
        return this;
    }

    public ReleaseTransactionTestBuilder withWallet(Wallet wallet) {
        this.wallet = wallet;
        return this;
    }

    public ReleaseTransactionTestBuilder withFederationFormatVersion(int federationFormatVersion) {
        this.federationFormatVersion = federationFormatVersion;
        return this;
    }

    public ReleaseTransactionTestBuilder withFederation(Federation federation) {
        this.federationFormatVersion = federation.getFormatVersion();
        this.changeAddress = federation.getAddress();
        return this;
    }

    public ReleaseTransactionTestBuilder withChangeAddress(Address changeAddress) {
        this.changeAddress = changeAddress;
        return this;
    }

    public ReleaseTransactionTestBuilder withFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
        return this;
    }

    public ReleaseTransactionTestBuilder withActivations(ActivationConfig.ForBlock activations) {
        this.activations = activations;
        return this;
    }

    public ReleaseTransactionBuilder build() {
        return new ReleaseTransactionBuilder(
            networkParameters,
            wallet,
            federationFormatVersion,
            changeAddress,
            feePerKb,
            activations
        );
    }
}

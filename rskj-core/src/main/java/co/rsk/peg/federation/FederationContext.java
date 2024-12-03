package co.rsk.peg.federation;

import co.rsk.bitcoinj.script.Script;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class FederationContext {
    private final Federation activeFederation;
    private final Federation retiringFederation;
    private final Script lastRetiredFederationP2SHScript;

    private FederationContext(FederationContextBuilder federationContextBuilder) {
        this.activeFederation = federationContextBuilder.activeFederation;
        this.retiringFederation = federationContextBuilder.retiringFederation;
        this.lastRetiredFederationP2SHScript = federationContextBuilder.lastRetiredFederationP2SHScript;
    }

    public Federation getActiveFederation() {
        return activeFederation;
    }

    public Optional<Federation> getRetiringFederation() {
        return Optional.ofNullable(retiringFederation);
    }

    public Optional<Script> getLastRetiredFederationP2SHScript() {
        return Optional.ofNullable(lastRetiredFederationP2SHScript);
    }

    public List<Federation> getLiveFederations() {
        return Stream.of(activeFederation, retiringFederation)
            .filter(Objects::nonNull)
            .toList();
    }

    public static FederationContextBuilder builder() {
        return new FederationContextBuilder();
    }

    public static class FederationContextBuilder {
        private Federation activeFederation;
        private Federation retiringFederation;
        private Script lastRetiredFederationP2SHScript;

        public FederationContextBuilder withActiveFederation(Federation activeFederation) {
            this.activeFederation = Objects.requireNonNull(activeFederation, "Active federation must not be null");
            return this;
        }

        public FederationContextBuilder withRetiringFederation(Federation retiringFederation) {
            this.retiringFederation = retiringFederation;
            return this;
        }

        public FederationContextBuilder withLastRetiredFederationP2SHScript(Script lastRetiredFederationP2SHScript) {
            this.lastRetiredFederationP2SHScript = lastRetiredFederationP2SHScript;
            return this;
        }

        public FederationContext build() {
            Objects.requireNonNull(activeFederation, "Active federation is required");
            return new FederationContext(this);
        }
    }
}

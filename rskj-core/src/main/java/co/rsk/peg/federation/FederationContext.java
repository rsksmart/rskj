package co.rsk.peg.federation;

import co.rsk.bitcoinj.script.Script;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class FederationContext {
    private final Federation activeFederation;
    private Federation retiringFederation;
    private Script lastRetiredFederationP2SHScript;

    public FederationContext(Federation activeFederation) {
        this.activeFederation = activeFederation;
    }

    public Federation getActiveFederation() {
        return activeFederation;
    }

    public Optional<Federation> getRetiringFederation() {
        return Optional.ofNullable(retiringFederation);
    }

    public void setRetiringFederation(Federation retiringFederation) {
        this.retiringFederation = retiringFederation;
    }

    public Optional<Script> getLastRetiredFederationP2SHScript() {
        return Optional.ofNullable(lastRetiredFederationP2SHScript);
    }

    public void setLastRetiredFederationP2SHScript(Script lastRetiredFederationP2SHScript) {
        this.lastRetiredFederationP2SHScript = lastRetiredFederationP2SHScript;
    }

    public List<Federation> getLiveFederations() {
        return Stream.of(activeFederation, retiringFederation)
            .filter(Objects::nonNull)
            .toList();
    }
}

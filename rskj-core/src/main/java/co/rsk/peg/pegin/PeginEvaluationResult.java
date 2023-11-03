package co.rsk.peg.pegin;

import co.rsk.peg.utils.RejectedPeginReason;

import java.util.Optional;

public class PeginEvaluationResult {
    private final PeginProcessAction peginProcessAction;
    private final Optional<RejectedPeginReason> rejectedPeginReason;

    public PeginEvaluationResult(PeginProcessAction peginProcessAction) {
        this.peginProcessAction = peginProcessAction;
        this.rejectedPeginReason = Optional.empty();
    }

    public PeginEvaluationResult(PeginProcessAction peginProcessAction, RejectedPeginReason rejectedPeginReason) {
        this.peginProcessAction = peginProcessAction;
        this.rejectedPeginReason = Optional.of(rejectedPeginReason);
    }

    public PeginProcessAction getPeginProcessAction() {
        return peginProcessAction;
    }

    public Optional<RejectedPeginReason> getRejectedPeginReason() {
        return rejectedPeginReason;
    }
}

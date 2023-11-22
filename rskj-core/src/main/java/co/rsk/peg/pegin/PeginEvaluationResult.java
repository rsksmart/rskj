package co.rsk.peg.pegin;

import java.util.Optional;

public class PeginEvaluationResult {
    private final PeginProcessAction peginProcessAction;
    private final RejectedPeginReason rejectedPeginReason;

    public PeginEvaluationResult(PeginProcessAction peginProcessAction) {
        this.peginProcessAction = peginProcessAction;
        this.rejectedPeginReason = null;
    }

    public PeginEvaluationResult(PeginProcessAction peginProcessAction, RejectedPeginReason rejectedPeginReason) {
        this.peginProcessAction = peginProcessAction;
        this.rejectedPeginReason = rejectedPeginReason;
    }

    public PeginProcessAction getPeginProcessAction() {
        return peginProcessAction;
    }

    public Optional<RejectedPeginReason> getRejectedPeginReason() {
        if (rejectedPeginReason != null) {
            return Optional.of(rejectedPeginReason);
        }

        return Optional.empty();
    }
}

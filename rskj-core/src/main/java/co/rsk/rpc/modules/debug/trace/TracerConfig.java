package co.rsk.rpc.modules.debug.trace;

public class TracerConfig {

    public TracerConfig(boolean onlyTopCall, boolean diffMode) {
        this.onlyTopCall = onlyTopCall;
        this.diffMode = diffMode;
    }
    public TracerConfig() {
        this(false, false);
    }
    private boolean onlyTopCall;
    private boolean diffMode;

    public boolean isOnlyTopCall() {
        return onlyTopCall;
    }

    public boolean isDiffMode() {
        return diffMode;
    }



}

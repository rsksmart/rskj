package co.rsk.datasources;

public class FailureTrack {
    public int failurePoint;
    protected int remainingSteps;
    protected boolean failed;
    protected boolean finishedSuccessfully; // returns if the given point was the last.

    static public boolean shouldFailNow(FailureTrack track) {
        if (track==null)
            return false;
        return track.shouldFailNow();
    }

    static public void finish(FailureTrack track) {
        if (track==null)
            return;
        track.finish();
    }

    public boolean shouldFailNow() {
        if (remainingSteps==0) {
            failed = true;
            return true; // remainingSteps must remain zero
        }
        remainingSteps--;
        return false;
    }

    public boolean getFinishedSuccessfully() {
        return finishedSuccessfully;
    }

    public void finish() {
        if (!failed)
            finishedSuccessfully = true;
    }

    public void start() {
        remainingSteps = failurePoint;
        failed = false;
    }
}

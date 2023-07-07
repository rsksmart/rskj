package co.rsk.jmh.sync;

import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@AuxCounters(AuxCounters.Type.OPERATIONS)
public class OpCounters {
    // These fields would be counted as metrics
    public int bytesRead = 0;

    @Setup(Level.Iteration)
    public void setupIteration() {
        //Every each iteration
        this.bytesRead = 0;
    }

}

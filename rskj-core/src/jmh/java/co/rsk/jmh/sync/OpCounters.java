package co.rsk.jmh.sync;

import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@AuxCounters(AuxCounters.Type.OPERATIONS)
public class OpCounters {
    // These fields would be counted as metrics
    public int bytesRead = 0;
    public int bytesSend = 0;
    public int nodes = 0;
    public int terminal = 0;
    public int account = 0;
    public int terminalAccount = 0;
    public int recovered = 0;

    @Setup(Level.Iteration)
    public void setupIteration() {
        //Every each iteration
        this.bytesRead = 0;
        this.bytesSend = 0;
        this.nodes = 0;
        this.terminal = 0;
        this.account = 0;
        this.terminalAccount = 0;
        this.recovered = 0;
    }

}

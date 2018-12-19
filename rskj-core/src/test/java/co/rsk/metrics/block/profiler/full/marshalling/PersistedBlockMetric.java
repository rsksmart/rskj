package co.rsk.metrics.block.profiler.full.marshalling;

public class PersistedBlockMetric extends PersistedMetric {

    private int rS;
    private int rE;

    public PersistedBlockMetric(){
        super();
    }

    public PersistedBlockMetric(int ramAtStart, int ramAtEnd){
        super();
        this.rS = ramAtStart;
        this.rE = ramAtEnd;
    }

    public int getrS() {
        return rS;
    }

    public void setrS(int ramAtStart) {
        this.rS = ramAtStart;
    }

    public int getrE() {
        return rE;
    }

    public void setrE(int ramAtEnd) {
        this.rE = ramAtEnd;
    }

}

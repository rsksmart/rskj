package co.rsk.pcc;

// todo(fedejinich) remove this class
public class VotingMocks {
    private long[][] votes;
    private byte[][] votesBfv;
    private long[][] votesPasta;
//    private List<String> votesBfvString;
    private byte[] pastaSK;
    private byte[] rk;
    private byte[] bfvSK;

    public long[][] getVotes() {
        return votes;
    }

    public void setVotes(long[][] votes) {
        this.votes = votes;
    }

    public byte[][] getVotesBfv() {
        return votesBfv;
    }

    public void setVotesBfv(byte[][] votesBfv) {
        this.votesBfv = votesBfv;
    }

    public long[][] getVotesPasta() {
        return votesPasta;
    }

    public void setVotesPasta(long[][] votesPasta) {
        this.votesPasta = votesPasta;
    }

//    public List<String> getVotesBfvString() {
//        return votesBfvString;
//    }
//
//    public void setVotesBfvString(List<String> votesBfvString) {
//        this.votesBfvString = votesBfvString;
//    }

    public byte[] getPastaSK() {
        return pastaSK;
    }

    public void setPastaSK(byte[] pastaSK) {
        this.pastaSK = pastaSK;
    }

    public byte[] getRk() {
        return rk;
    }

    public void setRk(byte[] rk) {
        this.rk = rk;
    }

    public byte[] getBfvSK() {
        return bfvSK;
    }

    public void setBfvSK(byte[] bfvSK) {
        this.bfvSK = bfvSK;
    }
}

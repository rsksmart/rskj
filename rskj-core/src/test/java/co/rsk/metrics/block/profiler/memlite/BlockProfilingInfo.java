package co.rsk.metrics.block.profiler.memlite;


public class BlockProfilingInfo {

    private long blockId;

    private long blockExecute; //BLOCK_EXECUTE,
    private long sigValidation; //SIG_VALIDATION,
    private long dataFlush; //DATA_FLUSH,
    private long vmExecute; //VM_EXECUTE,
    private long diskRead; //DISK_READ,
    private long genesisGeneration; //GENESIS_GENERATION,
    private long blockConnection; //BLOCK_CONNECTION,
    private long genesisBlockstoreFlush; //GENESIS_BLOCKSTORE_FLUSH,
    private long finalBlockchainFlush; //FINAL_BLOCKCHAIN_FLUSH,
    private long fillingExecutedBlock; //FILLING_EXECUTED_BLOCK,
    private long blockMining; //BLOCK_MINING,
    private long blockFinalStateValidation; //BLOCK_FINAL_STATE_VALIDATION,
    private long precompiledContractExecute; //PRECOMPILED_CONTRACT_EXECUTE
    private int trxs;

    public BlockProfilingInfo(){
        this(-1,-1);
    }

    public BlockProfilingInfo(long blockId, int trxs){
        this.blockId = blockId;
        this.trxs = trxs;
    }



    public long getBlockId() {
        return blockId;
    }

    public void setBlockId(long blockId) {
        this.blockId = blockId;
    }

    public int getTrxs() {
        return trxs;
    }

    public void setTrxs(int trxs) {
        this.trxs = trxs;
    }


    public long getBlockExecute() {
        return blockExecute;
    }

    public void addBlockExecute(long blockExecute) {
        this.blockExecute+=blockExecute;
    }

    public void setBlockExecute(long blockExecute) {
        this.blockExecute = blockExecute;
    }

    public long getSigValidation() {
        return sigValidation;
    }

    public void addSigValidation(long sigValidation) {
        this.sigValidation += sigValidation;
    }

    public void setSigValidation(long sigValidation) {
        this.sigValidation = sigValidation;
    }

    public long getDataFlush() {
        return dataFlush;
    }

    public void addDataFlush(long dataFlush) {
        this.dataFlush += dataFlush;
    }

    public void setDataFlush(long dataFlush) {
        this.dataFlush = dataFlush;
    }

    public long getVmExecute() {
        return vmExecute;
    }

    public void addVmExecute(long vmExecute) {
        this.vmExecute += vmExecute;
    }

    public void setVmExecute(long vmExecute) {
        this.vmExecute = vmExecute;
    }

    public long getDiskRead() {
        return diskRead;
    }

    public void addDiskRead(long diskRead) {
        this.diskRead += diskRead;
    }

    public void setDiskRead(long diskRead) {
        this.diskRead = diskRead;
    }

    public long getGenesisGeneration() {
        return genesisGeneration;
    }

    public void addGenesisGeneration(long genesisGeneration) {
        this.genesisGeneration += genesisGeneration;
    }

    public void setGenesisGeneration(long genesisGeneration) {
        this.genesisGeneration = genesisGeneration;
    }

    public long getBlockConnection() {
        return blockConnection;
    }


    public void addBlockConnection(long blockConnection) {
        this.blockConnection += blockConnection;
    }

    public void setBlockConnection(long blockConnection) {
        this.blockConnection = blockConnection;
    }

    public long getGenesisBlockstoreFlush() {
        return genesisBlockstoreFlush;
    }

    public void addGenesisBlockstoreFlush(long genesisBlockstoreFlush) {
        this.genesisBlockstoreFlush += genesisBlockstoreFlush;
    }


    public void setGenesisBlockstoreFlush(long genesisBlockstoreFlush) {
        this.genesisBlockstoreFlush = genesisBlockstoreFlush;
    }

    public long getFinalBlockchainFlush() {
        return finalBlockchainFlush;
    }

    public void addFinalBlockchainFlush(long finalBlockchainFlush) {
        this.finalBlockchainFlush += finalBlockchainFlush;
    }

    public void setFinalBlockchainFlush(long finalBlockchainFlush) {
        this.finalBlockchainFlush = finalBlockchainFlush;
    }

    public long getFillingExecutedBlock() {
        return fillingExecutedBlock;
    }

    public void addFillingExecutedBlock(long fillingExecutedBlock) {
        this.fillingExecutedBlock += fillingExecutedBlock;
    }

    public void setFillingExecutedBlock(long fillingExecutedBlock) {
        this.fillingExecutedBlock = fillingExecutedBlock;
    }

    public long getBlockMining() {
        return blockMining;
    }

    public void setBlockMining(long blockMining) {
        this.blockMining = blockMining;
    }

    public long getBlockFinalStateValidation() {
        return blockFinalStateValidation;
    }

    public void setBlockFinalStateValidation(long blockFinalStateValidation) {
        this.blockFinalStateValidation = blockFinalStateValidation;
    }

    public long getPrecompiledContractExecute() {
        return precompiledContractExecute;
    }

    public void setPrecompiledContractExecute(long precompiledContractExecute) {
        this.precompiledContractExecute = precompiledContractExecute;
    }
}

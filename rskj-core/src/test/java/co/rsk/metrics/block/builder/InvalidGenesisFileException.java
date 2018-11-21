package co.rsk.metrics.block.builder;

public class InvalidGenesisFileException extends Exception {

    public InvalidGenesisFileException(Exception e){
        super(e);
    }

    public InvalidGenesisFileException(String message){
        super(message);
    }
}

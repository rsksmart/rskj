package co.rsk.peg;

public class GetSenderBtcAddressSenderNotPresentException extends RegisterBtcTransferException{
    public GetSenderBtcAddressSenderNotPresentException(String message) {
        super(message);
    }
}
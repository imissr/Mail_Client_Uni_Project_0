package client;

public class IllegalResponseException extends RuntimeException{
    public IllegalResponseException(String errorMsg){
        super(errorMsg);
    }
}

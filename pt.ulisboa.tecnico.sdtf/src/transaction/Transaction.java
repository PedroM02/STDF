package transaction;

import java.io.Serializable;

public class Transaction implements Serializable {

    private final String from;
    private final String to;
    private final long value;
    private final long gasPrice;
    private final long gasLimit;
    private final long nonce;
    private final byte[] signature;

    public Transaction(String from, String to, long value,
                       long gasPrice, long gasLimit, long nonce,
                       byte[] signature) {
        this.from = from;
        this.to = to;
        this.value = value;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.nonce = nonce;
        this.signature = signature;
    }

    public String getFrom()      { return from; }
    public String getTo()        { return to; }
    public long getValue()       { return value; }
    public long getGasPrice()    { return gasPrice; }
    public long getGasLimit()    { return gasLimit; }
    public long getNonce()       { return nonce; }
    public byte[] getSignature() { return signature; }

    @Override
    public String toString() {
        return "Transaction{from=" + from + ", to=" + to +
               ", value=" + value + ", gasPrice=" + gasPrice + "}";
    }
}
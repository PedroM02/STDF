package blockchain;

import java.io.Serializable;

public class Account implements Serializable {

    private final String address;
    private long balance;
    private long nonce;

    public Account(String address, long balance) {
        this.address = address;
        this.balance = balance;
        this.nonce = 0;
    }

    public String getAddress() { return address; }
    public long getBalance()   { return balance; }
    public long getNonce()     { return nonce; }

    public void debit(long amount)  { this.balance -= amount; }
    public void credit(long amount) { this.balance += amount; }
    public void incrementNonce()    { this.nonce++; }

    @Override
    public String toString() {
        return "Account{address=" + address + ", balance=" + balance + ", nonce=" + nonce + "}";
    }
}
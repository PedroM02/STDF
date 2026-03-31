package blockchain;

import transaction.Transaction;
import java.util.HashMap;
import java.util.Map;

import crypto.SignatureUtils;

public class WorldState {

    private final Map<String, Account> accounts = new HashMap<>();

    public void addAccount(Account account) {
        accounts.put(account.getAddress(), account);
    }

    public Account getAccount(String address) {
        return accounts.get(address);
    }

    public boolean exists(String address) {
        return accounts.containsKey(address);
    }

    public TransactionResult execute(Transaction tx) {
        Account sender = accounts.get(tx.getFrom());
        Account receiver = accounts.get(tx.getTo());

        if (sender == null)
            return TransactionResult.fail("Sender does not exist");

        if (receiver == null)
            return TransactionResult.fail("Receiver does not exist");


        if (tx.getGasPrice() <= 0 || tx.getGasLimit() <= 0)
            return TransactionResult.fail("Gas price and limit must be > 0");

        if (tx.getNonce() != sender.getNonce())
            return TransactionResult.fail("Invalid nonce");

        long gasUsed = 21000L;
        long gasCost = tx.getGasPrice() * Math.min(tx.getGasLimit(), gasUsed);

        if (sender.getBalance() < tx.getValue() + gasCost)
            return TransactionResult.fail("Insufficient balance");

        sender.debit(tx.getValue() + gasCost);
        receiver.credit(tx.getValue());
        sender.incrementNonce();

        return TransactionResult.success(gasUsed);
    }

    @Override
    public String toString() {
        return "WorldState{accounts=" + accounts.values() + "}";
    }
}
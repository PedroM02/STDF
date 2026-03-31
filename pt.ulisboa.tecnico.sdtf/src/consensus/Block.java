package consensus;

import crypto.HashUtils;
import transaction.Transaction;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Block implements Serializable {

    private final String parentHash;
    private final List<Transaction> transactions;
    private final long view;
    private final int proposerId;

    public Block(String parentHash, List<Transaction> transactions,
                 long view, int proposerId) {
        this.parentHash = parentHash;
        // ordena por gasPrice decrescente ao construir o bloco
        this.transactions = transactions.stream()
                .sorted(Comparator.comparingLong(Transaction::getGasPrice).reversed())
                .toList();
        this.view = view;
        this.proposerId = proposerId;
    }

    public String getParentHash()            { return parentHash; }
    public List<Transaction> getTransactions() { return transactions; }
    public long getView()                    { return view; }
    public int getProposerId()               { return proposerId; }

    public String getHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(parentHash).append("|").append(view).append("|").append(proposerId);
        for (Transaction tx : transactions) {
            sb.append("|").append(tx.getFrom())
              .append(tx.getTo())
              .append(tx.getValue())
              .append(tx.getNonce());
        }
        try {
            byte[] hash = HashUtils.sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    @Override
    public String toString() {
        return "Block{txs=" + transactions.size() +
               ", view=" + view +
               ", hash=" + getHash().substring(0, 8) + "...}";
    }
}
package blockchain;

import consensus.Block;
import transaction.Transaction;
import crypto.HashUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class BlockPersistence {

    private final Path directory;

    public BlockPersistence(String directory) {
        this.directory = Path.of(directory);
        try {
            Files.createDirectories(this.directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create blocks directory", e);
        }
    }

    public void save(Block block, int index) {
        String filename = "block_" + index + ".json";
        Path path = directory.resolve(filename);
        String json = toJson(block, index);
        try {
            Files.writeString(path, json, StandardCharsets.UTF_8);
            System.out.println("  [Persistence] Saved " + filename);
        } catch (IOException e) {
            System.err.println("  [Persistence] Failed to save " + filename + ": " + e.getMessage());
        }
    }

    private String toJson(Block block, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"index\": ").append(index).append(",\n");
        sb.append("  \"block_hash\": \"").append(block.getHash()).append("\",\n");
        sb.append("  \"previous_block_hash\": \"").append(block.getParentHash()).append("\",\n");
        sb.append("  \"view\": ").append(block.getView()).append(",\n");
        sb.append("  \"proposer\": ").append(block.getProposerId()).append(",\n");
        sb.append("  \"transactions\": [\n");
        List<Transaction> txs = block.getTransactions();
        for (int i = 0; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            sb.append("    {\n");
            sb.append("      \"from\": \"").append(tx.getFrom()).append("\",\n");
            sb.append("      \"to\": \"").append(tx.getTo()).append("\",\n");
            sb.append("      \"value\": ").append(tx.getValue()).append(",\n");
            sb.append("      \"gasPrice\": ").append(tx.getGasPrice()).append(",\n");
            sb.append("      \"gasLimit\": ").append(tx.getGasLimit()).append(",\n");
            sb.append("      \"nonce\": ").append(tx.getNonce()).append("\n");
            sb.append("    }");
            if (i < txs.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }
}
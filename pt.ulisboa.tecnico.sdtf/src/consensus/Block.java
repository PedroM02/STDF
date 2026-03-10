package consensus;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class Block implements Serializable {

    private final String parentHash;
    private final String command;
    private final long view;
    private final int proposerId;

    public Block(String parentHash, String command, long view, int proposerId) {
        this.parentHash = parentHash;
        this.command = command;
        this.view = view;
        this.proposerId = proposerId;
    }

    public String getParentHash() { return parentHash; }
    public String getCommand() { return command; }
    public long getView() { return view; }
    public int getProposerId() { return proposerId; }

    public String getHash() {
        String data = parentHash + "|" + command + "|" + view + "|" + proposerId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // Fallback to simple hash
            return Integer.toHexString(data.hashCode());
        }
    }

    @Override
    public String toString() {
        return "Block{cmd=" + command + ", view=" + view + ", hash=" + getHash().substring(0, 8) + "...}";
    }
}

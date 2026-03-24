package consensus;

import blockchain.BlockchainService;
import blockchain.InMemoryLedger;
import common.Address;
import common.Membership;
import common.NodeConfig;
import common.ProcessId;
import crypto.ThresholdSignatureService;
import links.AuthenticatedPerfectLink;
import transport.UdpTransport;
import com.weavechain.sig.ThresholdSigEd25519Params;
import com.weavechain.sig.ThresholdSigEd25519;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Simulator {
    private static final String HOST = "127.0.0.1";
    private static final int BASE_PORT = 19000;
    private static final int MAX_PACKET_SIZE = 64 * 1024;
    private static final long RETRY_INTERVAL_MS = 200;
    private static final long VIEW_TIMEOUT_MS = 8_000;

    // Extra time to let the DH handshakes complete before starting consensus
    private static final long HANDSHAKE_WAIT_MS = 1_000;

    public static void run() {
        int n = 4;  //nº replicas
        int f = 1;
        int quorumSize = 2 * ((n - f) / 3) + 1;

        ThresholdSigEd25519Params thresholdParams = generateThresholdParams(quorumSize, n);

        List<NodeConfig> nodeConfigs = new ArrayList<>();
        List<ProcessId> processIds = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            ProcessId processId = new ProcessId("node-" + i);
            Address address = new Address(HOST, BASE_PORT + i);

            processIds.add(processId);
            nodeConfigs.add(new NodeConfig(i, processId, address));
        }

        Membership membership = new Membership(nodeConfigs);
        for (NodeConfig nodeConfig : nodeConfigs) {
            nodeConfig.setMembership(membership);
        }

        List<AuthenticatedPerfectLink> links = new ArrayList<>();
        List<HotStuffNode> nodes = new ArrayList<>();
        List<BlockchainService> ledgers = new ArrayList<>();
        CountDownLatch decisions = new CountDownLatch(n);

        System.out.println("HotStuff network simulation");
        System.out.println("Nodes: " + n);
        System.out.println("Leader for view 1: node-" + Math.floorMod(1, n));

        try {
            for (int i = 0; i < n; i++) {
                final int nodeId = i;
                ProcessId processId = processIds.get(i);
                Address address = membership.getAddress(processId);
                UdpTransport transport = new UdpTransport(address, null, MAX_PACKET_SIZE);

                // APL now uses DH key exchange + HMAC — no RSA keys needed here
                AuthenticatedPerfectLink link = new AuthenticatedPerfectLink(
                        processId,
                        membership,
                        transport,
                        RETRY_INTERVAL_MS
                );

                BlockchainService ledger = new InMemoryLedger();
                HotStuffNode node = new HotStuffNode(
                        i,
                        n,
                        membership,
                        link,
                        ledger,
                        decidedValue -> {
                            System.out.println("[Callback node-" + nodeId + "] decided " + decidedValue);
                            decisions.countDown();
                        },
                        VIEW_TIMEOUT_MS,
                        1L,
                        new ThresholdSignatureService(
                                quorumSize,
                                n,
                                thresholdParams.getPublicKey(),
                                thresholdParams.getPrivateShares().get(i).toByteArray()
                        )
                );

                links.add(link);
                nodes.add(node);
                ledgers.add(ledger);
            }

            // Trigger DH handshakes — each node broadcasts its DH public key to all peers
            for (AuthenticatedPerfectLink link : links) {
                link.startHandshake();
            }

            // Wait for handshakes to complete before starting consensus
            Thread.sleep(HANDSHAKE_WAIT_MS);

            for (HotStuffNode node : nodes) {
                node.start();
            }

            String value = "append:hello";
            int leaderId = Math.floorMod(1, n);
            System.out.println("Submitting value to leader node-" + leaderId + ": " + value);
            nodes.get(leaderId).submitValue(value);

            boolean completed = decisions.await(10, TimeUnit.SECONDS);
            if (!completed) {
                System.out.println("FAILED: timeout waiting for all nodes to decide");
            } else {
                System.out.println("All nodes decided");
                for (int i = 0; i < ledgers.size(); i++) {
                    System.out.println("  Ledger node-" + i + ": " + ledgers.get(i).readAll());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulation interrupted", e);
        } finally {
            for (HotStuffNode node : nodes) {
                node.stop();
            }
        }

        System.out.println("=== Simulation complete ===");
    }

    private static ThresholdSigEd25519Params generateThresholdParams(int threshold, int n) {
        try {
            return new ThresholdSigEd25519(threshold, n).generate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate threshold signature parameters", e);
        }
    }

    public static void main(String[] args) {
        run();
    }
}
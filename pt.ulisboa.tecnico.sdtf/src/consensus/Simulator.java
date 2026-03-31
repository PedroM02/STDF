package consensus;

import blockchain.BlockPersistence;
import blockchain.BlockchainService;
import blockchain.InMemoryLedger;
import blockchain.WorldState;
import blockchain.GenesisBlock;
import common.Address;
import common.Membership;
import common.NodeConfig;
import common.ProcessId;
import crypto.ThresholdSignatureService;
import links.AuthenticatedPerfectLink;
import transaction.Transaction;
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
        CountDownLatch decisions = new CountDownLatch(n - f);

        System.out.println("HotStuff network simulation");
        System.out.println("Nodes: " + n);
        System.out.println("Leader for view 1: node-" + Math.floorMod(1, n));

        try {
            for (int i = 0; i < n; i++) {
                final int nodeId = i;
                ProcessId processId = processIds.get(i);
                Address address = membership.getAddress(processId);
                UdpTransport transport = new UdpTransport(address, null, MAX_PACKET_SIZE);
                transport.start();

                // APL now uses DH key exchange + HMAC — no RSA keys needed here
                AuthenticatedPerfectLink link = new AuthenticatedPerfectLink(
                        processId,
                        membership,
                        transport,
                        RETRY_INTERVAL_MS
                );

                WorldState worldState = GenesisBlock.createWorldState();
                Block genesisBlock = GenesisBlock.create();
                BlockchainService ledger = new InMemoryLedger(worldState, genesisBlock, new BlockPersistence("blocks/node-" + i));
                HotStuffNode node = new HotStuffNode(
                        i,
                        n,
                        membership,
                        link,
                        ledger,
                        response -> {
                            System.out.println("[Callback node-" + nodeId + "] decided block=" + response.getRequestId() + " index=" + response.getIndex() + " success=" + response.isSuccess());
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

            // Trigger DH handshakes
            for (AuthenticatedPerfectLink link : links) {
                link.startHandshake();
            }

            // Espera até todos os handshakes estarem completos
            System.out.println("Waiting for DH handshakes...");
            long deadline = System.currentTimeMillis() + 10_000;
            boolean allReady = false;
            while (System.currentTimeMillis() < deadline) {
                for (int i = 0; i < links.size(); i++) {
                    AuthenticatedPerfectLink link = links.get(i);
                    long ready = membership.getProcessIds().stream()
                        .filter(pid -> !pid.equals(link.getSelf()))
                        .filter(link::isReady)
                        .count();
                    System.out.println("  node-" + i + " handshakes: " + ready + "/" + (n-1));
                }           
                allReady = links.stream().allMatch(link ->
                    membership.getProcessIds().stream()
                        .filter(pid -> !pid.equals(link.getSelf()))
                        .allMatch(link::isReady)
                );
                if (allReady) break;
                Thread.sleep(50);
            }
            if (!allReady) {
                System.out.println("FAILED: handshakes did not complete in time");
                return;
            }
System.out.println("All handshakes complete, starting consensus");

            Transaction tx = new Transaction(
                "alice",
                "bob",
                100L,
                1L,
                21000L,
                0L,
                null
            );

            // cliente envia a todos os nós
            System.out.println("Client broadcasting transaction to all nodes");
            for (HotStuffNode node : nodes) {
                node.submitValue(tx);
            }

            boolean completed = decisions.await(10, TimeUnit.SECONDS);
            if (!completed) {
                System.out.println("FAILED: timeout waiting for all nodes to decide");
            } else {
                System.out.println("All nodes decided");
                for (int i = 0; i < ledgers.size(); i++) {
                    System.out.println("  Ledger node-" + i + ": " + ledgers.get(i).size() + " blocks");
                    System.out.println("  WorldState node-" + i + ": " + ledgers.get(i).getWorldState());
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
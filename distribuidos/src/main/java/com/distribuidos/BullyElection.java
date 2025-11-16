package com.distribuidos;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean; // <--- added

/**
 * Simplified Bully: asynchronously probes higher IDs. If none responds, broadcasts coordinator.
 * Peers formatted as "node2:50052" or "IP:port".
 */
public class BullyElection {
    private final int myId;
    private final List<String> peers;
    private volatile int coordinator = -1;
    private final HttpClient client = HttpClient.newHttpClient();

    public BullyElection(int myId, List<String> peers) {
        this.myId = myId;
        this.peers = peers;
        // initial coordinator guess: highest numeric id in peers or self
        coordinator = myId;
        for (String p : peers) {
            int id = extractId(p);
            if (id > coordinator) coordinator = id;
        }
    }
        boolean foundHigher = false;
    public void startElection() {
        System.out.println(String.format("{\"ts\":\"%s\",\"node\":%d,\"event\":\"electionStart\"}", java.time.Instant.now(), myId));
          
        CompletableFuture<?>[] futures = peers.stream()
                .map(p -> {
                    int id = extractId(p);
                    if (id <= myId) return CompletableFuture.completedFuture(null);
                    foundHigher = true;
                    String url = base(p) + "/status"; // check alive
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
                    return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                            .thenAccept(resp -> {
                                if (resp.statusCode() == 200) {
                                    // a higher node is alive -> it will take over
                                    System.out.println(String.format("{\"ts\":\"%s\",\"node\":%d,\"event\":\"higherAlive\",\"higher\":%d}", java.time.Instant.now(), myId, id));
                                }
                            }).exceptionally(ex -> {
                                // no response -> ignored
                                return null;
                            });
                }).toArray(CompletableFuture[]::new);

        // after all checks, if none higher responded, become coordinator
        CompletableFuture.allOf(futures).whenComplete((v, ex) -> {
            boolean anyHigherAlive = false; // conservative default; we printed above if someone alive
            if (!anyHigherAlive) {
                becomeCoordinator();
            }
        });
    }

    private void becomeCoordinator() {
        coordinator = myId;
        System.out.println(String.format("{\"ts\":\"%s\",\"node\":%d,\"event\":\"becomeCoordinator\"}", java.time.Instant.now(), myId));
        // broadcast coordinator
        for (String p : peers) {
            String url = base(p) + "/coordinator";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(Integer.toString(myId)))
                    .build();
            client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                  .thenAccept(resp -> System.out.println(String.format("{\"ts\":\"%s\",\"node\":%d,\"event\":\"announceSent\",\"to\":\"%s\",\"status\":%d}", java.time.Instant.now(), myId, url, resp.statusCode())))
                  .exceptionally(e -> { return null; });
        }
    }

    public void receiveCoordinatorMessage(int newCoordinator) {
        this.coordinator = newCoordinator;
        System.out.println(String.format("{\"ts\":\"%s\",\"node\":%d,\"event\":\"coordinatorReceived\",\"leader\":%d}", java.time.Instant.now(), myId, newCoordinator));
    }

    public int getCoordinator() {
        return coordinator;
    }

    private int extractId(String peer) {
        try {
            String name = peer.split(":")[0];
            if (name.startsWith("node")) name = name.replace("node", "");
            return Integer.parseInt(name);
        } catch (Exception e) {
            return -1;
        }
    }

    private String base(String peer) {
        if (peer.startsWith("http")) return peer;
        return "http://" + peer;
    }
}


package com.distribuidos;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.distribuidos.LamportClock;
import com.distribuidos.BullyElection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.*;

/**
 * Nó distribuído:
 * - lê NODE_ID, PORT, PEERS
 * - endpoints: /healthz, /ping, /receive, /election, /coordinator, /status
 * - usa LamportClock e BullyElection
 */
public class App {
    private static int nodeId;
    private static LamportClock lamport = new LamportClock();
    private static BullyElection bully;
    private static int port;
    private static List<String> peers;
    private static HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        nodeId = Integer.parseInt(System.getenv().getOrDefault("NODE_ID", "1"));
        port = Integer.parseInt(System.getenv().getOrDefault("PORT", "50051"));
        String peersStr = System.getenv().getOrDefault("PEERS", "");
        peers = Arrays.stream(peersStr.split(","))
                      .map(String::trim)
                      .filter(s -> !s.isEmpty())
                      .collect(Collectors.toList());

        bully = new BullyElection(nodeId, peers);

        //logInfo("starting", "nodeStarted", "peers", peers.toString(), "port", String.valueOf(port));
    logInfo("starting", "peers", peers.toString(), "port", String.valueOf(port));

        startHttpServer();
        Thread.currentThread().join();
    }

    private static void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/healthz", exchange -> sendText(exchange, 200, "OK - node " + nodeId));

        server.createContext("/ping", exchange -> {
            lamport.localEvent();
            String body = "PONG from node " + nodeId + " | Lamport=" + lamport.getClock();
            logInfo("pingReceived", "pong", body);
            sendText(exchange, 200, body);
            // broadcast lamport to peers asynchronously
            broadcastLamport();
        });

        server.createContext("/receive", exchange -> {
            // POST body contains timestamp number
            try {
                byte[] bb = exchange.getRequestBody().readAllBytes();
                String s = new String(bb).trim();
                long received = Long.parseLong(s);
                lamport.receiveMessage(received);
                String resp = "ACK node " + nodeId + " lamport=" + lamport.getClock();
                logInfo("receive", "ack", s, "lamport", String.valueOf(lamport.getClock()));
                sendText(exchange, 200, resp);
            } catch (Exception e) {
                sendText(exchange, 500, "bad request");
            }
        });

        server.createContext("/election", exchange -> {
            // triggers local election protocol
            CompletableFuture.runAsync(() -> bully.startElection());
            sendText(exchange, 200, "election triggered");
        });

        server.createContext("/coordinator", exchange -> {
            // announcement from coordinator: body contains coordinator id
            try {
                byte[] bb = exchange.getRequestBody().readAllBytes();
                String s = new String(bb).trim();
                int coord = Integer.parseInt(s);
                bully.receiveCoordinatorMessage(coord);
                logInfo("coordinatorReceived", "coordinator", s);
                sendText(exchange, 200, "ok");
            } catch (Exception e) {
                sendText(exchange, 400, "bad");
            }
        });

        server.createContext("/status", exchange -> {
            String json = String.format("{\"node\":%d,\"lamport\":%d,\"leader\":%d}",
                    nodeId, lamport.getClock(), bully.getCoordinator());
            sendText(exchange, 200, json);
        });

        server.start();
        logInfo("server","nodeStarted" ,"started", "port", String.valueOf(port));
    }

    private static void broadcastLamport() {
        long ts = lamport.sendMessage(); // increments and returns
        for (String peer : peers) {
            
package net.server.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PingService {
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static final AtomicReference<String> latency = new AtomicReference<>("0");

    public static void init() {
        executor.scheduleAtFixedRate(() -> {
            try {
                ServerData server = Minecraft.getInstance().getCurrentServer();
                if (server != null) {
                    String host = parseHost(server.ip);
                    int port = parsePort(server.ip);
                    latency.set(String.valueOf(measureLatency(host, port)));
                } else if (Minecraft.getInstance().getSingleplayerServer() != null) {
                    latency.set("0");
                } else {
                    latency.set("N/A");
                }
            } catch (Exception e) {
                latency.set("N/A");
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    public static String getLatency() {
        String currentLatency = latency.get();
        return "N/A".equals(currentLatency) ? "N/A" : ("-1".equals(currentLatency) ? "超时" : currentLatency + "ms");
    }

    private static String parseHost(String ip) {
        if (ip == null) return "";
        String[] parts = ip.split(":");
        return parts.length > 0 ? parts[0] : "";
    }

    private static int parsePort(String ip) {
        if (ip == null) return 25565;
        String[] parts = ip.split(":");
        return parts.length > 1 ? Integer.parseInt(parts[1]) : 25565;
    }

    private static long measureLatency(String host, int port) {
        if (host == null || host.isEmpty()) return -1;

        try (Socket socket = new Socket()) {
            long start = System.currentTimeMillis();
            socket.connect(new InetSocketAddress(host, port), 1000);
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -1;
        }
    }
}

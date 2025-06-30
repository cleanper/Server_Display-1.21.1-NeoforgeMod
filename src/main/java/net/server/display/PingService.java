package net.server.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PingService {
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static String latency = "0";

    public static void init() {
        executor.scheduleAtFixedRate(() -> {
            ServerData server = Minecraft.getInstance().getCurrentServer();
            if (server != null) {
                String host = parseHost(server.ip);
                int port = parsePort(server.ip);
                latency = String.valueOf(measureLatency(host, port));
            } else if (Minecraft.getInstance().getSingleplayerServer() != null) {
                latency = "0";
            } else {
                latency = "N/A";
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    public static String getLatency() {
        return latency.equals("-1") ? "超时" : latency + "ms";
    }

    private static String parseHost(String ip) {
        return ip.split(":")[0];
    }

    private static int parsePort(String ip) {
        return ip.contains(":") ? Integer.parseInt(ip.split(":")[1]) : 25565;
    }

    private static long measureLatency(String host, int port) {
        try (Socket socket = new Socket()) {
            long start = System.currentTimeMillis();
            socket.connect(new InetSocketAddress(host, port), 1000);
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -1;
        }
    }
}
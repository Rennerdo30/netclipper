package netclipper;

import com.karangandhi.networking.core.TaskNotCompletedException;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class NetClipper {

    private static List<Client> clients = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {

        boolean isServer = args != null && args.length == 1 && args[0].equalsIgnoreCase("server");

        if (isServer) {
            System.out.println("Starting server...");
            try {<
                Server server = new Server("0.0.0.0", 50333, 128000, true);
                server.start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            System.out.println("collecting servers...");
            var hosts = checkHosts("192.168.178");
            System.out.println("got possible clients!");

            List<Client> clients = new ArrayList<>();
            for (String host : hosts) {
                var thread = new Thread(() -> {
                    try {
                        Client client = new Client(host, 50333, true);
                        System.out.println("Got server: " + host);
                        clients.add(client);
                        client.start();
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                });
                thread.start();

                thread.join();
            }
        }
    }

    public static List<String> checkHosts(String subnet) {
        List<String> result = new ArrayList<>();
        int timeout = 15;

        List<String> ips = new ArrayList<>();
        for (int i = 1; i < 255; i++) {
            String host = subnet + "." + i;
            ips.add(host);
        }

        ips.parallelStream().filter(host -> {
            try {
                if (InetAddress.getByName(host).isReachable(timeout)) {
                    System.out.println(host + " is reachable");
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }).forEach(ip -> result.add(ip));

        return result;
    }
}

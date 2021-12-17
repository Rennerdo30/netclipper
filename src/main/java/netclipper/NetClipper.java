package netclipper;


import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.minlog.Log;
import netclipper.networking.Client;
import netclipper.networking.Server;

import java.util.ArrayList;
import java.util.List;

import static com.esotericsoftware.minlog.Log.LEVEL_TRACE;

public class NetClipper {

    private static List<Client> clients = new ArrayList<>();

    public static void main(String[] args) {

        System.out.println("Current OS: '" + System.getProperty("os.name") + "'");

        boolean isServer = args != null && args.length == 1 && args[0].equalsIgnoreCase("server");

        //Log.set(LEVEL_TRACE);

        if (isServer) {
            System.out.println("Starting server...");
            Server.run();
        } else {
            Client.run();
        }
    }

}

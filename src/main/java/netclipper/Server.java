package netclipper;

import com.karangandhi.networking.TCP.Connection;
import com.karangandhi.networking.TCP.TCPServer;
import com.karangandhi.networking.utils.Message;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;

public class Server extends TCPServer {

    /**
     * Creates an Instance of the server
     *
     * @param ip      The address of the server
     * @param port    The port at the address of the server
     * @param backlog The backlog that the server can handle
     * @param verbose This is true if you want the server to be verbose
     * @throws IOException Throws an exception if the server exists on the given ip and port
     */
    public Server(String ip, int port, int backlog, boolean verbose) throws IOException {
        super(ip, port, backlog, verbose);
    }

    @Override
    public boolean onClientConnected(Connection clientConnection) {
        // Here if we return false the client will be rejected.
        // For this example we will except all clients who connect to the server and are authenticated by the server
        System.out.println("Client Connected");
        return true;
    }

    @Override
    public void onMessageReceived(Message receivedMessage, Connection client) {
        if (receivedMessage.getId() == Methods.STRING) {
            // Print the message recieved
            System.out.println("Message recieved: " + receivedMessage.messageBody);

            Message<Methods, String> messageToBroadcast = new Message<Methods, String>(Methods.STRING, (String) receivedMessage.messageBody);
            // Send the message to Everyone
            this.sendAll(messageToBroadcast);
        }
    }

    @Override
    public void onClientDisConnected(Connection clientConnection) {
        // This will be called when the client disconnects
        System.out.println("A client was disconnected");
    }
}

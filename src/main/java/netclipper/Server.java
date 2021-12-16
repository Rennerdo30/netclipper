package netclipper;

import com.karangandhi.networking.TCP.Connection;
import com.karangandhi.networking.TCP.TCPServer;
import com.karangandhi.networking.utils.Message;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class Server extends TCPServer {

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    private final Map<String, PublicKey> clientKeys = new HashMap<>();
    private final Map<String, Connection> clients = new HashMap<>();

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

        Map<String, Object> keys = Util.getRSAKeys();
        this.privateKey = (PrivateKey) keys.get("private");
        this.publicKey = (PublicKey) keys.get("public");
    }

    @Override
    public boolean onClientConnected(Connection clientConnection) {
        // Here if we return false the client will be rejected.
        // For this example we will except all clients who connect to the server and are authenticated by the server
        System.out.println("Client Connected");

        clients.put(clientConnection.getId().toString(), clientConnection);

        sendMessage(new Message<>(Methods.PUB_KEY, this.publicKey), clientConnection);


        return true;
    }

    @Override
    public void onMessageReceived(Message receivedMessage, Connection client) {
        if (receivedMessage.getId() == Methods.PUB_KEY) {
            this.clients.put(client.getId().toString(), client);
            this.clientKeys.put(client.getId().toString(), (PublicKey) receivedMessage.messageBody);
        } else if (receivedMessage.getId() == Methods.STRING) {
            // Print the message recieved
            System.out.println("Message recieved: " + receivedMessage.messageBody);

            String data = (String) receivedMessage.messageBody;
            try {
                data = Util.decryptMessage(data, this.privateKey);
            } catch (Exception ex)
            {
                ex.printStackTrace();
            }

            for (Map.Entry<String, PublicKey> entry : this.clientKeys.entrySet()) {
                String msgPayload = data;

                try {
                    msgPayload = Util.encryptMessage(data, entry.getValue());
                } catch (Exception ex)
                {
                    ex.printStackTrace();
                }

                Message<Methods, String> messageToBroadcast = new Message<>(Methods.STRING, msgPayload);
                this.sendMessage(messageToBroadcast, this.clients.get(entry.getKey()));
            }

        } else if (receivedMessage.getId() == Methods.FILE) {
            // Print the message recieved
            FileTransfer fileTransfer = (FileTransfer) receivedMessage.messageBody;
            System.out.println("Message recieved (file): " + fileTransfer.filename);

            Message<Methods, FileTransfer> messageToBroadcast = new Message<>(Methods.FILE, fileTransfer);
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

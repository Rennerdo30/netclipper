package netclipper;

import com.karangandhi.networking.TCP.Connection;
import com.karangandhi.networking.TCP.TCPClient;
import com.karangandhi.networking.utils.Message;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class Client extends TCPClient {

    private static String lastClipboard = "";


    /**
     * Creates an instance of TCPClient
     *
     * @param ip      The ip address of the server
     * @param port    The port of the server
     * @param verbose If you want the client to be verbose
     * @throws IOException Throws an IOException if no server exists on the given ip and port
     */
    public Client(String ip, int port, boolean verbose) throws IOException {
        super(ip, port, verbose);
    }

    @Override
    public boolean onConnected() {
        System.out.println("Connected to the Server");

        new Thread(() -> {
            while (true)
            {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor))
                {
                    try {
                        String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                        if (!lastClipboard.equals(data) && data != null) {
                            sendMessage(new Message(Methods.STRING, data));

                            lastClipboard = data;
                            System.out.println("new clipboard data: " + lastClipboard);
                        }
                    } catch (UnsupportedFlavorException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        return true;
    }

    @Override
    public void onMessageReceived(Message receivedMessage, Connection client) {
        if (receivedMessage.getId() == Methods.STRING) {
            // Print the message recieved
            System.out.println("Message recieved: " + receivedMessage.messageBody);

            lastClipboard = (String) receivedMessage.messageBody;

            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection stringSelection = new StringSelection((String) receivedMessage.messageBody);
            clipboard.setContents(stringSelection, null);

        } else if (receivedMessage.getId() == Methods.FILE) {
            // Print the details of the client connected
            System.out.println("A New client joined the server at port: " + receivedMessage.messageBody);
        }
    }

    @Override
    public void onDisConnected(Connection clientConnection) {
        System.out.println("Disconnected from the server");
    }

}

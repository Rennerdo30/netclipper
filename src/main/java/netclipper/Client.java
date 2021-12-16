package netclipper;

import com.karangandhi.networking.TCP.Connection;
import com.karangandhi.networking.TCP.TCPClient;
import com.karangandhi.networking.utils.Message;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Client extends TCPClient {

    private static String lastClipboard = "";
    private static File lastFile = null;
    private File lastRecFile = null;

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    private PublicKey serverKey;

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

        Map<String, Object> keys = Util.getRSAKeys();
        this.privateKey = (PrivateKey) keys.get("private");
        this.publicKey = (PublicKey) keys.get("public");
    }

    @Override
    public boolean onConnected() {
        System.out.println("Connected to the Server");

        sendMessage(new Message(Methods.PUB_KEY, this.publicKey));

        new Thread(() -> {
            while (true) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    try {
                        String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                        if (!lastClipboard.equals(data) && data != null && this.serverKey != null) {
                            try {
                                data = Util.encryptMessage(data, this.serverKey);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                            sendMessage(new Message(Methods.STRING, data));

                            lastClipboard = data;
                            System.out.println("new clipboard data: " + lastClipboard);
                        }
                    } catch (UnsupportedFlavorException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                    try {
                        Transferable t = clipboard.getContents(null);
                        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        for (File file : files) {
                            System.out.println(file);
                            if (lastFile != null && file != null && !lastFile.equals(file)) {

                                FileTransfer fileTransfer = new FileTransfer();
                                fileTransfer.filename = file.getName();
                                fileTransfer.buffer = Files.readAllBytes(file.toPath());

                                sendMessage(new Message(Methods.FILE, fileTransfer));
                            }
                        }

                    } catch (UnsupportedFlavorException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println("file in clipboard!");
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
        if (receivedMessage.getId() == Methods.PUB_KEY) {
            this.serverKey = (PublicKey) receivedMessage.messageBody;
        } else if (receivedMessage.getId() == Methods.STRING) {
            // Print the message recieved
            System.out.println("Message recieved: " + receivedMessage.messageBody);

            lastClipboard = (String) receivedMessage.messageBody;

            try {
                lastClipboard = Util.decryptMessage(lastClipboard, this.privateKey);
            } catch (Exception ex) {
                ex.printStackTrace();
            }


            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection stringSelection = new StringSelection(lastClipboard);
            clipboard.setContents(stringSelection, null);

        } else if (receivedMessage.getId() == Methods.FILE) {
            FileTransfer fileTransfer = (FileTransfer) receivedMessage.messageBody;

            System.out.println("Message recieved (file): " + fileTransfer.filename);

            if (lastRecFile != null) {
                lastRecFile.delete();
                lastRecFile = null;
            }

            File file = new File(fileTransfer.filename);
            try {
                Files.write(file.toPath(), fileTransfer.buffer);
                lastRecFile = file;

                List listOfFiles = new ArrayList();
                listOfFiles.add(file);
                FileTransferable ft = new FileTransferable(listOfFiles);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ft, (clipboard, contents) -> System.out.println("Lost ownership"));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDisConnected(Connection clientConnection) {
        System.out.println("Disconnected from the server");
    }

}

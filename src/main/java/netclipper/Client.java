package netclipper;

import com.google.gson.Gson;
import com.karangandhi.networking.TCP.Connection;
import com.karangandhi.networking.TCP.TCPClient;
import com.karangandhi.networking.utils.Message;
import netclipper.transfer.file.FileTransferEnd;
import netclipper.transfer.file.FileTransferHelper;
import netclipper.transfer.file.FileTransferPart;
import netclipper.transfer.file.FileTransferStart;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.List;

import org.apache.commons.codec.binary.Base64;


public class Client extends TCPClient {

    private static Gson gson = new Gson();
    private static String lastClipboard = "";
    private static File lastFile = null;
    private File lastRecFile = null;

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    private PublicKey serverKey;

    private Map<String, FileTransferHelper> fileTransferHelpers = new HashMap<>();

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
                        if (files != null) {
                            for (File file : files) {
                                System.out.println(file);
                                if (file != null && (lastFile == null || !lastFile.equals(file))) {
                                    lastFile = file;

                                    String fileData = Base64.encodeBase64URLSafeString(Util.gzipCompress(Files.readAllBytes(file.toPath())));
                                    List<String> fileDataParts = Util.splitEqually(fileData, 8192);

                                    final String fileID = UUID.randomUUID().toString();
                                    FileTransferStart transferStart = new FileTransferStart();
                                    transferStart.fileID = fileID;
                                    transferStart.packageCount = fileDataParts.size();
                                    transferStart.filename = file.getName();
                                    sendMessage(new Message(Methods.FILE_START, gson.toJson(transferStart)));

                                    for (long i = 0; i < fileDataParts.size(); i++) {
                                        FileTransferPart fileTransfer = new FileTransferPart();
                                        fileTransfer.fileID = fileID;
                                        fileTransfer.idx = i;
                                        fileTransfer.part = fileDataParts.get(Math.toIntExact(i)) + "\0";
                                        sendMessage(new Message(Methods.FILE_PART, gson.toJson(fileTransfer)));

                                        Thread.sleep(1000);

                                    }

                                    FileTransferEnd transferEnd = new FileTransferEnd();
                                    transferEnd.fileID = fileID;
                                    sendMessage(new Message(Methods.FILE_END, gson.toJson(transferEnd)));
                                }
                            }
                        }
                    } catch (UnsupportedFlavorException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
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

        } else if (receivedMessage.getId() == Methods.FILE_START) {
            System.out.println((String) receivedMessage.messageBody);

            FileTransferStart fileTransferStart = gson.fromJson((String) receivedMessage.messageBody, FileTransferStart.class);
            FileTransferHelper fileTransferHelper = new FileTransferHelper(fileTransferStart);
            fileTransferHelpers.put(fileTransferStart.fileID, fileTransferHelper);

        } else if (receivedMessage.getId() == Methods.FILE_PART) {
            System.out.println((String) receivedMessage.messageBody);

            try {
                FileTransferPart fileTransferPart = gson.fromJson((String) receivedMessage.messageBody, FileTransferPart.class);
                if (fileTransferHelpers.containsKey(fileTransferPart.fileID)) {
                    fileTransferHelpers.get(fileTransferPart.fileID).addPart(fileTransferPart);
                }
            } catch (Exception ex)
            {
                ex.printStackTrace();
            }

        } else if (receivedMessage.getId() == Methods.FILE_END) {
            System.out.println((String) receivedMessage.messageBody);

            FileTransferEnd fileTransferEnd = gson.fromJson((String) receivedMessage.messageBody, FileTransferEnd.class);
            if (fileTransferHelpers.containsKey(fileTransferEnd.fileID))
            {
                FileTransferHelper helper = fileTransferHelpers.get(fileTransferEnd.fileID);
                helper.addEnd(fileTransferEnd);

                if (helper.isReady())
                {
                    if (lastRecFile != null)
                        lastRecFile.delete();

                    File tmpFile = helper.store();
                    lastRecFile = tmpFile;

                    List listOfFiles = new ArrayList();
                    listOfFiles.add(tmpFile);
                    FileTransferable ft = new FileTransferable(listOfFiles);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ft, (clipboard, contents) -> System.out.println("Lost ownership"));

                    fileTransferHelpers.remove(fileTransferEnd.fileID);
                }
            }
        }
    }

    @Override
    public void onDisConnected(Connection clientConnection) {
        System.out.println("Disconnected from the server");
    }

}

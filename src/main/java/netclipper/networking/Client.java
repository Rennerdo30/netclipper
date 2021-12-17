package netclipper.networking;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.google.gson.Gson;
import netclipper.FileTransferable;
import netclipper.Util;
import netclipper.transfer.file.FileTransferEnd;
import netclipper.transfer.file.FileTransferHelper;
import netclipper.transfer.file.FileTransferPart;
import netclipper.transfer.file.FileTransferStart;
import org.apache.commons.codec.binary.Base64;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.List;

public class Client {

    public static void run() {
        try {
            com.esotericsoftware.kryonet.Client clientSocket = new com.esotericsoftware.kryonet.Client(819200, 204800);
            InetAddress address = clientSocket.discoverHost(54777, 5000);

            if (address != null) {
                System.out.println("Found Server: " + address);
                Client client = new Client(clientSocket);
                client.start(address, 54555, 54777);
            } else {
                System.err.println("Could not find server!");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static String lastClipboard = "";
    private static File lastFile = null;


    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final com.esotericsoftware.kryonet.Client clientSocket;

    private PublicKey serverKey;
    private Map<String, FileTransferHelper> fileTransferHelpers = new HashMap<>();
    private File lastRecFile = null;

    public Client(com.esotericsoftware.kryonet.Client clientSocket) {
        this.clientSocket = clientSocket;

        Kryo kryo = clientSocket.getKryo();
        kryo.register(Message.class);
        kryo.register(Methods.class);

        Map<String, Object> keys = Util.getRSAKeys();
        this.privateKey = (PrivateKey) keys.get("private");
        this.publicKey = (PublicKey) keys.get("public");
    }

    public void start(InetAddress address, int tcpPort, int udpPort) {
        this.clientSocket.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                handleRequest(connection, object);
            }
        });

        try {
            clientSocket.start();
            clientSocket.connect(5000, address, tcpPort, udpPort);

            clientSocket.sendTCP(new Message<>(Methods.PUB_KEY, this.publicKey));
        } catch (IOException e) {
            e.printStackTrace();
        }

        loop();
    }

    private void handleRequest(Connection connection, Object object) {
        if (object instanceof Message) {
            Message response = (Message) object;

            System.out.println("Got message! type:" + response.method.toString());

            if (response.method == Methods.PUB_KEY) {
                this.serverKey = response.getPublicKeyFromBody();
            } else if (response.method == Methods.STRING) {
                // Print the message recieved
                System.out.println("Message recieved: " + response.payload);


                String payload = (String) response.getPayload(privateKey);
                System.out.println("payload: " + payload);

                lastClipboard = payload;
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection stringSelection = new StringSelection(payload);
                clipboard.setContents(stringSelection, null);


            } else if (response.method == Methods.FILE_START) {
                System.out.println(response.payload);

                FileTransferStart fileTransferStart = (FileTransferStart) response.getPayload(privateKey);
                FileTransferHelper fileTransferHelper = new FileTransferHelper(fileTransferStart);
                fileTransferHelpers.put(fileTransferStart.fileID, fileTransferHelper);

            } else if (response.method == Methods.FILE_PART) {
                System.out.println(response.payload);

                try {
                    FileTransferPart fileTransferPart = (FileTransferPart) response.getPayload(privateKey);
                    if (fileTransferHelpers.containsKey(fileTransferPart.fileID)) {
                        fileTransferHelpers.get(fileTransferPart.fileID).addPart(fileTransferPart);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            } else if (response.method == Methods.FILE_END) {
                System.out.println(response.payload);

                FileTransferEnd fileTransferEnd = (FileTransferEnd) response.getPayload(privateKey);
                if (fileTransferHelpers.containsKey(fileTransferEnd.fileID)) {
                    FileTransferHelper helper = fileTransferHelpers.get(fileTransferEnd.fileID);
                    helper.addEnd(fileTransferEnd);

                    if (helper.isReady()) {
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
        } else {
            System.err.println("Got message, but not of correct type! " + object.toString());
        }
    }

    private void loop() {
        final Client client = this;
        new Thread(() -> {
            while (true) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    try {
                        String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                        if (!lastClipboard.equals(data) && data != null && this.serverKey != null) {
                            client.clientSocket.sendTCP(new Message(Methods.STRING, this.serverKey, data));

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
                        java.util.List<File> files = (java.util.List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
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
                                    client.clientSocket.sendTCP(new Message<>(Methods.FILE_START, serverKey, transferStart));

                                    for (long i = 0; i < fileDataParts.size(); i++) {
                                        FileTransferPart fileTransfer = new FileTransferPart();
                                        fileTransfer.fileID = fileID;
                                        fileTransfer.idx = i;
                                        fileTransfer.part = fileDataParts.get(Math.toIntExact(i)) + "\0";
                                        client.clientSocket.sendTCP(new Message<>(Methods.FILE_PART, serverKey, fileTransfer));
                                    }

                                    FileTransferEnd transferEnd = new FileTransferEnd();
                                    transferEnd.fileID = fileID;
                                    client.clientSocket.sendTCP(new Message<>(Methods.FILE_END, serverKey, transferEnd));
                                }
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

    }
}

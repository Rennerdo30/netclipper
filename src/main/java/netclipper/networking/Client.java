package netclipper.networking;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.google.gson.Gson;
import netclipper.FileTransferable;
import netclipper.OperatingSystem;
import netclipper.Util;
import netclipper.transfer.file.FileTransferEnd;
import netclipper.transfer.file.FileTransferHelper;
import netclipper.transfer.file.FileTransferPart;
import netclipper.transfer.file.FileTransferStart;
import org.apache.commons.codec.binary.Base64;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ScatteringByteChannel;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.List;

public class Client {

    public static void run() {
        try {
            com.esotericsoftware.kryonet.Client clientSocket = new com.esotericsoftware.kryonet.Client(8192000, 2048000);
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

    private static Object lastClipboard = "";

    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final com.esotericsoftware.kryonet.Client clientSocket;

    private PublicKey serverKey;
    private Map<Integer, FileTransferHelper> fileTransferHelpers = new HashMap<>();
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

            clientSocket.setKeepAliveTCP(10000);
            clientSocket.setTimeout(25000);
            clientSocket.setIdleThreshold(0.8f);

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

                boolean retry = false;
                int count = 0;
                do {
                    try {
                        clipboard.setContents(stringSelection, null);
                    } catch (Exception ex) {
                        retry = true;
                    }

                    count++;

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                } while (retry && count < 10);


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

                        if (Util.getOS() != OperatingSystem.MACOS) {
                            List listOfFiles = new ArrayList();
                            listOfFiles.add(tmpFile);
                            FileTransferable ft = new FileTransferable(listOfFiles);
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ft, (clipboard, contents) -> System.out.println("Lost ownership"));
                        } else {
                            Util.copyToClipboard(tmpFile.getAbsolutePath());
                        }

                        fileTransferHelpers.remove(fileTransferEnd.fileID);
                    }
                }
            }
        } else {
            //System.err.println("Got message, but not of correct type! " + object.toString());
        }
    }

    private void loop() {
        final Client client = this;
        new Thread(() -> {
            while (true) {
                try {
                    if (!clientSocket.isConnected())
                    {
                        System.out.println("reconnect...");
                        clientSocket.reconnect();
                    }

                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                        try {
                            Transferable t = clipboard.getContents(null);
                            java.util.List<File> files = (java.util.List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                            if (files != null) {
                                for (File file : files) {
                                    System.out.println(file);
                                    if (file != null && (lastClipboard == null || !lastClipboard.equals(file))) {
                                        lastClipboard = file;

                                        final String fileID = UUID.randomUUID().toString();
                                        FileTransferStart transferStart = new FileTransferStart();
                                        transferStart.fileID = fileID.hashCode();
                                        transferStart.filename = file.getName();
                                        client.clientSocket.sendTCP(new Message<>(Methods.FILE_START, serverKey, transferStart));

                                        byte[] buffer = new byte[8192 * 2];
                                        final double packageCount = file.length() / buffer.length;
                                        int counter = 0;

                                        FileInputStream fis = new FileInputStream(file);
                                        while(fis.read(buffer) != -1)
                                        {
                                            byte[]compress = Util.gzipCompress(buffer);

                                            FileTransferPart fileTransfer = new FileTransferPart();
                                            fileTransfer.fileID = fileID.hashCode();
                                            fileTransfer.idx = counter;
                                            fileTransfer.part = compress;
                                            client.clientSocket.sendTCP(new Message<>(Methods.FILE_PART, serverKey, fileTransfer));

                                            counter++;

                                            double percentage = (counter / packageCount * 100);
                                            if (percentage % 1 == 0) {
                                                System.out.println("file send progress: " + percentage + "%");
                                            }

                                            while (!client.clientSocket.isIdle())
                                            {
                                                Thread.sleep(10);
                                                client.clientSocket.update(5000);
                                            }
                                        }

                                        fis.close();

                                        FileTransferEnd transferEnd = new FileTransferEnd();
                                        transferEnd.fileID = fileID.hashCode();
                                        transferEnd.packageCount = counter;
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

                    } else if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        try {
                            String data = (String) clipboard.getData(DataFlavor.stringFlavor);
                            if (!lastClipboard.equals(data) && data != null && this.serverKey != null) {
                                client.clientSocket.sendTCP(new Message(Methods.STRING, this.serverKey, data));

                                lastClipboard = data;
                                System.out.println("new clipboard data: " + lastClipboard);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }
}

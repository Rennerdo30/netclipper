package netclipper.networking;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import netclipper.Util;
import netclipper.transfer.file.FileTransferEnd;
import netclipper.transfer.file.FileTransferPart;
import netclipper.transfer.file.FileTransferStart;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class Server {


    private static PublicKey publicKey;
    private static PrivateKey privateKey;

    private static Map<Integer, PublicKey> clientKeys = new HashMap<>();

    public static void run()
    {
        try {
            com.esotericsoftware.kryonet.Server server = new com.esotericsoftware.kryonet.Server(1638400, 204800);

            Kryo kryo = server.getKryo();
            kryo.register(Message.class);
            kryo.register(Methods.class);

            server.start();
            server.bind(54555, 54777);

            Map<String, Object> keys = Util.getRSAKeys();
            Server.privateKey = (PrivateKey) keys.get("private");
            Server.publicKey = (PublicKey) keys.get("public");

            server.addListener(new Listener() {
                public void received (Connection connection, Object object) {
                    if (object instanceof Message) {
                        Message request = (Message)object;

                        System.out.println("Got message! type:" + request.method.toString());

                        if (request.method == Methods.PUB_KEY) {
                            try {
                                PublicKey publicKey = request.getPublicKeyFromBody();
                                Server.clientKeys.put(connection.getID(), publicKey);
                                connection.sendTCP(new Message<PublicKey>(Methods.PUB_KEY, Server.publicKey));
                                System.out.println("Send pub key!");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else if (request.method == Methods.STRING) {
                            System.out.println("Message recieved: " + request.payload);

                            String payload = (String) request.getPayload(privateKey);
                            System.out.println("payload: " + payload);

                            for (Map.Entry<Integer, PublicKey> entry : Server.clientKeys.entrySet()) {
                                if (entry.getKey() == connection.getID())
                                    continue;

                                Message response = new Message(Methods.STRING, entry.getValue(), payload);
                                server.sendToTCP(entry.getKey(), response);
                            }

                        } else if (request.method == Methods.FILE_START ) {
                            System.out.println("Message recieved: " + request.payload);

                            FileTransferStart payload = (FileTransferStart) request.getPayload(privateKey);
                            System.out.println("payload: " + payload);

                            for (Map.Entry<Integer, PublicKey> entry : Server.clientKeys.entrySet()) {
                                if (entry.getKey() == connection.getID())
                                    continue;

                                Message response = new Message(Methods.FILE_START, entry.getValue(), payload);
                                server.sendToTCP(entry.getKey(), response);
                            }
                        }else if (request.method == Methods.FILE_PART) {
                            System.out.println("Message recieved: " + request.payload);

                            FileTransferPart payload = (FileTransferPart) request.getPayload(privateKey);
                            System.out.println("payload: " + payload);

                            for (Map.Entry<Integer, PublicKey> entry : Server.clientKeys.entrySet()) {
                                if (entry.getKey() == connection.getID())
                                    continue;

                                Message response = new Message(Methods.FILE_PART, entry.getValue(), payload);
                                server.sendToTCP(entry.getKey(), response);
                            }
                        }else if (request.method == Methods.FILE_END) {
                            System.out.println("Message recieved: " + request.payload);

                            FileTransferEnd payload = (FileTransferEnd) request.getPayload(privateKey);
                            System.out.println("payload: " + payload);

                            for (Map.Entry<Integer, PublicKey> entry : Server.clientKeys.entrySet()) {
                                if (entry.getKey() == connection.getID())
                                    continue;

                                Message response = new Message(Methods.FILE_END, entry.getValue(), payload);
                                server.sendToTCP(entry.getKey(), response);
                            }
                        }
                    } else {
                        System.err.println("Got message, but not of correct type! " + object.toString());
                    }
                }
            });

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}

package netclipper.networking;

import com.google.gson.Gson;
import netclipper.Util;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;

public class Message<T> {

    public Methods method;
    public String payload;
    public String aesKey;

    public Message()
    {
    }

    public Message(Methods method, PublicKey publicKey)
    {
        this.method = method;

        String base64 = "";
        try {
            base64 = Base64.encodeBase64String(Util.serialize(publicKey));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.payload = base64;
    }

    public Message(Methods method, PublicKey publicKey, T payload) {
        this.method = method;

        String base64 = "";
        try {
            // 1. payload to byte array
            byte[] data = Util.serialize(payload);

            // 2. gzip payload
            byte[] gzipData = Util.gzipCompress(data);

            // 3. convert payload to base64 (more or less useless, but i like it :P)
            String base64String = Base64.encodeBase64String(gzipData);

            // 4. encrypt payload with RASE 4096
            String secretAESKeyString = Util.getSecretAESKeyAsString();
            base64 = Util.encryptTextUsingAES(base64String, secretAESKeyString);
            this.aesKey = Util.encryptAESKey(secretAESKeyString, publicKey);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        0

        this.payload = base64;
    }

    public T getPayload(PrivateKey privateKey) {
        try {
            // 1. decrypt rsa
            String decryptedAESKeyString = Util.decryptAESKey(this.aesKey, privateKey);
            String data = Util.decryptTextUsingAES(this.payload, decryptedAESKeyString);

            // 2. base64 to byte[]
            byte[] base64 = Base64.decodeBase64(data);

            // 3. unzip
            byte[] unzipped = Util.gzipUncompress(base64);

            // 4. byte[] to obj
            T obj = (T) Util.deserialize(unzipped);
            return obj;

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    public PublicKey getPublicKeyFromBody()
    {
        try {
            return (PublicKey) Util.deserialize(Base64.decodeBase64(this.payload));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

}

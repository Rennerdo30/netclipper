package netclipper.transfer.file;

import netclipper.Util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;



public class FileTransferHelper {

    private FileTransferStart fileTransferStart;
    private Map<Long, FileTransferPart> fileTransferParts;
    private FileTransferEnd fileTransferEnd;

    public FileTransferHelper(FileTransferStart start) {
        this.fileTransferStart = start;
        this.fileTransferParts = new HashMap<>(1000);
    }

    public void addPart(FileTransferPart part) {
        this.fileTransferParts.put(part.idx, part);
    }

    public void addEnd(FileTransferEnd end) {
        this.fileTransferEnd = end;
    }

    public boolean isReady() {
        return this.fileTransferStart.packageCount == this.fileTransferParts.size() && this.fileTransferEnd != null;
    }

    public File store() {
        File file = new File("tmp_clipboard/" + this.fileTransferStart.filename);

        try {
            Files.write(file.toPath(), Util.gzipUncompress(Base64.decodeBase64(getCompleteBuffer())));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return file;
    }

    private String getCompleteBuffer() {
        StringBuffer buffer = new StringBuffer(1000);

        for (Map.Entry<Long, FileTransferPart> entry : this.fileTransferParts.entrySet()) {
            buffer.append(entry.getValue().part);
        }

        return buffer.toString();
    }
}

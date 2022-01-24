package netclipper.transfer.file;

import java.io.Serializable;

public class FileTransferPart implements Serializable {

    public int fileID;
    public long idx;
    public byte[] part;

}

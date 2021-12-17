package netclipper.transfer.file;

import java.io.Serializable;

public class FileTransferStart implements Serializable {

    public String fileID;
    public long packageCount;
    public String filename;

}

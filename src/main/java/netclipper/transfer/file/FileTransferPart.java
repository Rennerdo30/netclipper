package netclipper.transfer.file;

import java.io.Serializable;

public class FileTransferPart implements Serializable {

    public String fileID;
    public long idx;
    public String part;

}

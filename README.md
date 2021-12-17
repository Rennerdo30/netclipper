# netclipper

netclipper is a application written in java, to share your clipboard across your local network.
This project is more or less for a specific use-case. 
The code is pretty rough at many edges, especially the file transfer!

Feel free to add your PR's if you want to fix something. (I would think writing your own application in this case would be the better idea ;) )

Features
---
* Automatically search for netclipper server in local network if client is launched
* Share clipboard (text) across all client devices
* Share clipboard (file) across all client devices
* AES/RSA encryption for all data exchanges

How to run
---

The server can be run via the following command:
```bash
java -jar <jar from downloads> server
```

The client can be run via the following command:
```bash
java -jar <jar from downloads>
```
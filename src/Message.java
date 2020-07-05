import java.util.*;
import java.io.Serializable;

public class Message implements Serializable {
	
	public String Descriptor;
	public int port;
	//Pong - Ping is just the descriptor
	public String ip;
	//Query
	public int RequestingPort;
	public int RequestID;
	public int TTL;
	public String Filename;
	public int numFiles;
	
	public byte[] fileBytes;
	
	public Message(String descriptor, int port, String ip, int RequestingPort, int RequestID, int ttl, String filename, int numfiles) {
		this.Descriptor = descriptor;
		this.port = port;
		this.ip = ip;
		this.RequestingPort = RequestingPort;
		this.RequestID = RequestID;
		this.TTL = ttl;
		this.Filename = filename;
		this.numFiles = numfiles;
	}

}

import java.util.*;
import java.io.Serializable;

public class Neighbor implements Serializable{
	
	public int port;
	public String ip;
	public List<Integer> neighbors;
	public int MaxCapacity;
	
	public Neighbor(int port, String ip, List<Integer> neighbors,int MaxCapacity) {
		this.port = port;
		this.ip = ip;
		this.neighbors = neighbors;
		this.MaxCapacity = MaxCapacity;
	}
}

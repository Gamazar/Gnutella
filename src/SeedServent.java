import java.util.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.*;

public class SeedServent {
	static String folderPath;
	static String ip;
	static int port;
	static int numFiles;
	//static List<Node> neighbors;
	static int maxNeighbors;
	//static List<Node> neighbors;
	static List<Integer> neighbors;
	static Map<Integer,Neighbor> potential_neighbors;
	static List<Integer> potentialneighs;
	static byte[] portBytes;
	static Thread pinging;
	static int numQueriesOut = 0;
	static List<String> filenames;
	
	static class PotentialNeighbors implements Runnable{ //Receives potential neighbors
		
		@Override
		public void run() {
			
			try {
				DatagramSocket inSocket = new DatagramSocket(port);
				
				byte[] buf = new byte[1024];
				
				while(true) {
					DatagramPacket incoming = new DatagramPacket(buf,buf.length);
					inSocket.receive(incoming);
					byte[] data = incoming.getData();
					
					ByteArrayInputStream in = new ByteArrayInputStream(data);
					ObjectInputStream ois = new ObjectInputStream(in);
					Neighbor neighbor = (Neighbor) ois.readObject();//new Neighbor();
				//	System.out.println("Received Object");
					
					for(int pot_neigh: neighbor.neighbors) {
						if(!potentialneighs.contains(pot_neigh)) potentialneighs.add(pot_neigh);
					}
					
				}
				
				
			} catch (IOException | ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
		}
		
	}
	
	static class PingMessaging implements Runnable{
		
		private boolean requesting(int hostPort) {
			try{
				
				Socket socket = new Socket("127.0.0.1", hostPort);
				
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
				Message message = new Message("GNUTELLA CONNECT",port,ip,0,0,0,null,0);
				
				oos.writeObject(message);
				
				Message reply;
				try {
					reply = (Message) ois.readObject();
					if(reply.Descriptor.equals("GNUTELLA REFUSED")) {
						return false;
					}
					else {
						return true;
					}
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					return false;
				}
				
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				return false;
			}
		}
		
		private void update_neighbors() {
			
			for(int new_neighbors: potentialneighs) {
				boolean check = requesting(new_neighbors);
				
				if(check) {
					if(port != new_neighbors) {
						neighbors.add(new_neighbors);
						break;
					}
				}
			}
		}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			
			boolean hasUpdated = false;
			while(true) {

						Socket socket;
						ObjectInputStream ois = null;
						ObjectOutputStream oos = null;
						for(int i = 0; i< neighbors.size(); i++) {			
							try {
								socket = new Socket("127.0.0.1", neighbors.get(i));
								oos = new ObjectOutputStream(socket.getOutputStream());
								ois = new ObjectInputStream(socket.getInputStream());
								
								Message ping = new Message("PING",port,ip,0,0,0,null,0);
								
								oos.writeObject(ping);
								
								try {
									Message reply = (Message) ois.readObject();
									
									System.out.println("Received pong with port: "+ reply.port);
									
								} catch (ClassNotFoundException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							catch(IOException e) {
								System.out.println("Servent with port number: " + neighbors.get(i) + "has been disconnected. Deleting and updating neighbors.");
								neighbors.remove(i);
								
								update_neighbors();
								hasUpdated = true;
							}
						}
						
						if(hasUpdated) {
							Thread sendingNeighbors = new Thread(new SendNeighbors());
							sendingNeighbors.start();
						}

						hasUpdated = false;
					try {
						Thread.sleep(6000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			}
		}
	}
	
	static class SendNeighbors implements Runnable{

		@Override
		public void run() {
			// TODO Auto-generated method stub
			Neighbor neigh = new Neighbor(port,ip,neighbors,maxNeighbors);
			//Message message = new Message("Neighbors",port,ip);
			ByteArrayOutputStream bStream = new ByteArrayOutputStream();
			ObjectOutputStream oos;
			
			try {
				
				oos = new ObjectOutputStream(bStream);
				oos.writeObject(neigh);
				oos.close();
				
				byte[] buf = bStream.toByteArray();
				
				for(int neighbor : neighbors) {	
					DatagramSocket socket = new DatagramSocket();
					DatagramPacket sendPacket = new DatagramPacket(buf,buf.length,new InetSocketAddress("127.0.0.1",neighbor));
					socket.send(sendPacket);
					socket.close();
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
	}
	
	
	static class Server implements Runnable{
		
		private DataInputStream in = null;
		private DataOutputStream out = null;
		
		int iteration = 0;
		
		public Server() {
			maxNeighbors = 7;
		}
		
		private boolean checkNeighbors(int portClient) throws UnknownHostException, IOException {
			for(int neighbor : neighbors) {
				if(port == neighbor) {
					return false;
				}
			}
			return true;
		}

		@Override
		public void run() { //The seed servent node where no nodes exist in the network yet.
			Map<Integer,Message> queries = new HashMap<>(); //MAKE THIS INTO A LIST OF MESSAGES;
			try {
				//Socket socket = new Socket("127.0.0.1",9999);
				ServerSocket serverSocket = new ServerSocket(port);
				ObjectInputStream ois = null;// new ObjectInputStream();
				ObjectOutputStream oos = null;// new ObjectOutputStream();

				while(true) {
					
					Socket socket = serverSocket.accept();
					ois = new ObjectInputStream(socket.getInputStream());
					oos = new ObjectOutputStream(socket.getOutputStream());
					
					try {
						Message message = (Message) ois.readObject();
						
					//	System.out.println(message.Descriptor);
						
						if(message.Descriptor.equals("GNUTELLA CONNECT")) {
							boolean check = checkNeighbors(message.port);
							
							Message response;
							if(check) {
								response = new Message("GNUTELLA OK",0,null,0,0,0,null,0);
								neighbors.add(message.port);
								
								Thread sn_t = new Thread(new SendNeighbors());
								sn_t.start();
								
							}
							else {
								response = new Message("GNUTELLA REFUSED",0,null,0,0,0,null,0);
							}
							oos.writeObject(response);
							//System.out.println("Got here");
						}
						else if(message.Descriptor.equals("PING")) {
							
							Message response = new Message("PONG",port,ip,0,0,0,null,0);
							
							oos.writeObject(response);
							
						}
						else if(message.Descriptor.equals("QUERY")) {
							
							if(message.RequestingPort != port) {
								if(filenames.contains(message.Filename)) {
									queries.put(message.RequestID,message);
									Thread qh_t = new Thread(new QueryHit(message));
									qh_t.start();
								}
								else if((message.TTL != 1) && (!queries.containsKey(message.RequestID) || (queries.containsKey(message.RequestID) &&(queries.get(message.RequestID).Filename != message.Filename)))) {
									message.TTL--;
									queries.put(message.RequestID,message);
									Thread qs = new Thread(new Client(message));
									qs.start();
								}
							}
							
							oos.writeObject(message);//reply back
						}
						else if(message.Descriptor.equals("QUERY HIT")) {
							FileOutputStream fos = new FileOutputStream(folderPath+message.Filename);
							byte[] file = message.fileBytes;
							
							fos.write(file,0,file.length);
						
						}
						
						
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			//	Message response = new Message("GNUTELLA REFUSED",0,null,0,0,0,null,0);
				//oos.writeObject(response);
			}
			
			
			
		}
		
	}
	static class QueryHit implements Runnable{
		Message m;
		public QueryHit(Message message) {
			m = message;
		}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			
			try {
				Socket socket = new Socket(ip,m.RequestingPort);
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				
				Message queryHit = new Message("QUERY HIT",port,ip,m.RequestingPort,m.RequestID,0,m.Filename,0);
			//	System.out.println("Query Hit got here 1");
				FileInputStream fos = new FileInputStream(folderPath+m.Filename);
			//	System.out.println();
				byte[] file = new byte[10000];
				fos.read(file,0,file.length);
				queryHit.fileBytes = file;
				System.out.println("Sending the file now.");
				oos.writeObject(queryHit);
		//		System.out.println("Sent Query Hit");
				oos.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("If getting this message, the requesting Query Servent is assumed to have died before receiving the file.");
			}
			
		}
		
	}
	
	static class QuerySearch implements Runnable{
		Message m;
		int nPort;
		public QuerySearch(int neighborPort,Message message) {
			m = message;
			nPort = neighborPort;
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			Socket socket;
			try {
				socket = new Socket("127.0.0.1",nPort);
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
				
				oos.writeObject(m);
				//oos.close();
				try {
					Message reply = (Message)is.readObject();
					numQueriesOut--;
					
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				numQueriesOut--;
			}
			
			
		}
		
	}
	
	static class Client implements Runnable{
		Message m;
		public Client(Message message) {
			m = message;
		}
		
		@Override
		public void run() {
			
			for(int neighbor : neighbors) { 
				Thread newQuery = new Thread(new QuerySearch(neighbor,m));
				newQuery.start();
				numQueriesOut++;
			}
			
			while(numQueriesOut != 0) ;
			
			
		}
		
	}
	
	public static void main(String[] args) {
		
		folderPath="./SeedServentFiles/";
		pinging = new Thread(new PingMessaging());
		filenames = new ArrayList<>();
		neighbors = new ArrayList<>();
		potential_neighbors = new HashMap<>();
		potentialneighs = new ArrayList<>();
		ip = "127.0.0.1";
		port = 9990;
		//portBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(port).array();
		numQueriesOut = 0;
		
		File currD = new File("./SeedServentFiles/");
		String[] fileList = currD.list();
		for(String name : fileList) {
			filenames.add(name);
		}
		
		PotentialNeighbors pn = new PotentialNeighbors();
		Thread pn_t = new Thread(pn);
		pn_t.start();
		Server seedServer = new Server();
		Thread ssThread = new Thread(seedServer);
		ssThread.start();
		pinging.start();
		while(true) {
			System.out.print("Enter command (Search: [filename] or Exit: ");
			Scanner scan = new Scanner(System.in);
			
			String command = scan.nextLine().trim();
			
			if(command.substring(0,4).equals("Exit") || command.substring(0,4).equals("exit")) {
				System.out.println("Exiting Servent");
				System.exit(0);
			}
			else if(command.substring(0,7).equals("Search:") || command.substring(0,7).equals("search:")) {
				
				Random r = new Random();
				int randomRID = r.nextInt(100) + 1;
				
				Message message = new Message("QUERY",0,ip,port,randomRID,5,command.substring(7,command.length()).trim(),0);
				Thread newClient = new Thread(new Client(message));
				newClient.start();
			}
			else {
				System.out.println("Unknown Command");
			}
			
		}
		

	}

}

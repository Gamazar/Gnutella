import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.concurrent.*;

public class Servent {
	static String folderPath;
	static String ip;
	static int port;
	static int hostPort; //Existing Servent port number current Servent is trying to connect to.
	static int numFiles;
	static List<Integer> neighbors; //MAKE IT A LIST OF OBJECTS
	static byte[] portBytes;
	static Map<Integer,Neighbor> potential_neighbors;
	static List<Integer> potentialneighs;
	static int maxCapacity;
	static int numQueriesOut;
	static List<String> filenames;
	
	static class PotentialNeighbors implements Runnable{

		@Override
		public void run() {
			// TODO Auto-generated method stub
			
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
	
	static class SendNeighbors implements Runnable{ //sends potential neighbors

		@Override
		public void run() {
			// TODO Auto-generated method stub
			//DatagramSocket sendSocket = new DatagramSocket();
			
			Neighbor neigh = new Neighbor(port,ip,neighbors,maxCapacity);
			
			ByteArrayOutputStream bStream = new ByteArrayOutputStream();
			ObjectOutputStream oos;
			
			try {
				
				oos = new ObjectOutputStream(bStream);
				try {
					oos.writeObject(neigh);
				}catch(IOException e1) {
					System.out.println("LOL");
				}

				oos.close();
				
				byte[] buf = bStream.toByteArray();
				
				for(int i = 0; i<neighbors.size();i++) {
					try {
						DatagramSocket socket = new DatagramSocket();
						DatagramPacket sendPacket = new DatagramPacket(buf,buf.length,new InetSocketAddress("127.0.0.1",neighbors.get(i)));
						socket.send(sendPacket);
						socket.close();
					}catch(IOException e){
						neighbors.remove(i);
					}
					
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
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
				}catch(ClassNotFoundException e) {
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
						//boolean has = potentialneighs.remove(new_neighbors);
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
				try {
					Socket socket;
					ObjectInputStream ois = null;
					ObjectOutputStream oos = null;
				//	System.out.println("Number of Neighbors : " + neighbors.size());
					//System.out.println("Sending ping to all neighbors");
					for(int i = 0 ;i < neighbors.size();i++) {
						try {
							socket = new Socket("127.0.0.1",neighbors.get(i));
							
							oos = new ObjectOutputStream(socket.getOutputStream());
							ois = new ObjectInputStream(socket.getInputStream());
							
							Message ping = new Message("PING",port,ip,0,0,0,null,0);
							
							oos.writeObject(ping);
							try {
								Message reply = (Message) ois.readObject();
								//System.out.println("Received pong with port: " + reply.port);
							} catch(ClassNotFoundException e) {
								
							}
													
						} catch (IOException e) {
							//e.printStackTrace();
							//System.out.println("Servent with port number: " + neighbors.get(i) + " has been disconnected. Deleting and updating neighbors.");
							neighbors.remove(i);
							
							update_neighbors();
							hasUpdated = true;
						} 
					}
					if(hasUpdated) {
						SendNeighbors sn = new SendNeighbors();
						
						Thread sendN = new Thread(sn);
						sendN.start();
					}
					
					hasUpdated = false;
					Thread.sleep(6000);
				} catch (InterruptedException e) {
					//System.out.println();
				}
			}
			
			
		}
		
	}
	
	static class Server implements Runnable{

		private boolean checkNeighbors(int portClient) throws UnknownHostException, IOException {
			if(neighbors.size() == maxCapacity) return false;
			
			for(int neighbor : neighbors) {
				if(port == neighbor) {
					return false;
				}
			}
			return true;
		}
		
		@Override
		public void run() {			
			//Map<Integer,Message> queries = new HashMap<>();
			//List<Message> queries = new ArrayList();
			Map<Integer,Message> queries = new ConcurrentHashMap<>();
			try {
				ServerSocket serverSocket = new ServerSocket(port);
				ObjectInputStream ois = null;
				ObjectOutputStream oos = null;
				
				while(true) {
				//	System.out.println("Num neighbors: " + neighbors.size());
				//	System.out.println("Number of Neighbors: " + neighbors.size());
//					for(int n : neighbors) {
//						System.out.println("We have neighbor: " + n);
//					}
					
					
					Socket socket = serverSocket.accept();
					ois = new ObjectInputStream(socket.getInputStream());
					oos = new ObjectOutputStream(socket.getOutputStream());
					
					try {
						Message message = (Message) ois.readObject();
						
						if(message.Descriptor.equals("GNUTELLA CONNECT")) {
						//	System.out.println("New Servent trying to connect.");
							boolean check = checkNeighbors(message.port);
							
							Message response;
							if(check) {
								response = new Message("GNUTELLA OK",0,null,0,0,0,null,0);
								if(port != message.port)neighbors.add(message.port);
								
								Thread sn_t = new Thread(new SendNeighbors());
								sn_t.start();
							}
							else {
								response = new Message("GNUTELLA REFUSED",0,null,0,0,0,null,0);
							}
							oos.writeObject(response);
							
						}
						else if(message.Descriptor.equals("PING")) {
							Message response = new Message("PONG",port,ip,0,0,0,null,0);
							System.out.println("Received ping with port: "+ message.port+ "sending pong");
							oos.writeObject(response);
						}
						else if(message.Descriptor.equals("QUERY")) {
						//	System.out.println("Received QUERY with ttl: " + message.TTL);
							if(message.RequestingPort != port) {
								if(filenames.contains(message.Filename)) {
									//queries.add(message);
									queries.put(message.RequestID,message);
									Thread qh_t = new Thread(new QueryHit(message));
									qh_t.start();
								}
								else if((message.TTL != 1)&&((!queries.containsKey(message.RequestID)||(!queries.get(message.RequestID).Filename.equals(message.Filename))))) {//(!queries.contains(message))){//(!queries.containsKey(message.RequestID) || (queries.containsKey(message.RequestID) &&(queries.get(message.RequestID).Filename != message.Filename)))) {
									//queries.add(message);
								//	System.out.println("Query got here 2");
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
				//System.out.println();
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				
				Message queryHit = new Message("QUERY HIT",port,ip,m.RequestingPort,m.RequestID,0,m.Filename,0);
			
				FileInputStream fis = new FileInputStream(folderPath+m.Filename);
				byte[] file = new byte[10000];
				fis.read(file,0,file.length);
				queryHit.fileBytes = file;
				oos.writeObject(queryHit);
				
				oos.close();
				
			} catch (IOException e) {
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
					numQueriesOut--;
				}
				is.close();
				oos.close();
				
				
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
	
	public static void main(String[] args) throws UnknownHostException, IOException {
		//pinging = new Thread(new PingMessaging());
		folderPath="./Servent2Files/";
		potentialneighs = new ArrayList<>();
		ip = "127.0.0.1";
		hostPort = 9990;
		port = 9989;
		maxCapacity = 5;
		//portBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(port).array();
		neighbors = new ArrayList<>();
		potential_neighbors = new ConcurrentHashMap<>();
		potentialneighs = new ArrayList<>();
		numQueriesOut = 0;
		filenames = new ArrayList<>();
		
		File currD = new File(folderPath);
		String[] fileList = currD.list();
		for(String name : fileList) {
			//System.out.println("Added file name: " + name);
			filenames.add(name);
		}

		Socket socket = new Socket("127.0.0.1",hostPort);
		ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
		ObjectInputStream is = new ObjectInputStream(socket.getInputStream());
		Message message = new Message("GNUTELLA CONNECT",port,"127.0.0.1",0,0,0,"",0);
		
		oos.writeObject(message);
		
		try {
			Message response = (Message) is.readObject();
			if(response.Descriptor.equals("GNUTELLA OK")) {
				neighbors.add(hostPort);
				
				Server serve = new Server();
				Thread server = new Thread(serve);
//				
				PotentialNeighbors pot_neighbors= new PotentialNeighbors();
				Thread thread_pn = new Thread(pot_neighbors);
				thread_pn.start();
//				
				server.start();
//				
				PingMessaging pm = new PingMessaging();
				Thread pm_t = new Thread(pm);
				pm_t.start();
			}
			else {
				System.out.println("Did not connect to Servent host port.");
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
		while(true) { //Keep looping for User input
			System.out.print("Enter Command (Use 'Search:[filename]' or 'Exit'): ");
			Scanner input = new Scanner(System.in);
			
			String command = input.nextLine().trim();
			
			if(command.substring(0,4).equals("Exit") || command.substring(0,4).equals("exit")) {
				System.out.println("Exiting Servent");
				System.exit(0);
			}
			else if(command.substring(0,7).equals("Search:") || command.substring(0,7).equals("search:")) {
				System.out.println(command);
				
				Random r = new Random();
				int randomRID = r.nextInt(100) + 1;
				Message m = new Message("QUERY",0,ip,port,randomRID,5,command.substring(7,command.length()).trim(),0);
				Thread newClient = new Thread(new Client(m));
				newClient.start();
			}
			else {
				System.out.println("Unkown Command");
			}
		
		}
	}

}

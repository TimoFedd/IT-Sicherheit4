import java.io.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * Simple Web-Server which implements just the GET request of the http 1.0 -
 * Protocol
 */

class WebServer {

	public final String DOCS_DIR = getClasspathDir() + "/Testweb/";

	public final static int SERVER_PORT = 443;


	/** Listen on TCP-connection requests */
	private void runWebServer() {
		ServerSocket welcomeSocket;
		Socket connectionSocket;
		
		String ksName="D:\\IT-Sicherheit4\\MYSSL\\keystore.jks";  //Pfad der jks datei
		char ksPass[] = "123456".toCharArray();   //Passwort des Keystores
		
		try {
			//Ein neues KeyStore Objekt erstellen
			KeyStore ks = KeyStore.getInstance("JKS");
			
			//KeyStore Datei Laden
			ks.load(new FileInputStream(ksName), ksPass);

			//KeyManagerFactory-Objekt erzeugen und initialisieren 
			KeyManagerFactory kmf =  KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks, ksPass);
			
			//SSLContext-Objekt erzeugen und initialisieren
			SSLContext sc = SSLContext.getInstance("TLS");
	        sc.init(kmf.getKeyManagers(), null, null);
	        
	        // SSLServerSocketFactory-Objekt erzeugen
	        SSLServerSocketFactory ssf = sc.getServerSocketFactory();
	        SSLServerSocket s = (SSLServerSocket) ssf.createServerSocket(SERVER_PORT); 
			
	      
	        
	        int cnt = 0;
	        System.out.println("Waiting for connections using port "+ SERVER_PORT);
	    	while (true) {
	    		SSLSocket sslSocket = (SSLSocket) s.accept();
	    	
				// Start new thread for the handling of new socket connection
				// (one https request)
				(new WebServerThread(++cnt, sslSocket, DOCS_DIR)).start();
	    	}
	        
			
		} catch (KeyStoreException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CertificateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnrecoverableKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	
	}

	/** Handle one http request */
	private class WebServerThread extends Thread {

		private int name; // name of current thread
		private Socket sock;
		private String defaultDir;

		// headerLines content: (field name, field value)
		private HashMap<String, String> headerLines = new HashMap<>();
		private BufferedReader inFromClient;
		private DataOutputStream outToClient;
		private String fileName = ""; // filename incl. local path
		private String relFileName = ""; // relative filename
		private long fileLength;
		private FileInputStream inFile;

		// constructor
		public WebServerThread(int num, Socket socket, String dir) {
			this.name = num;
			this.sock = socket;
			this.defaultDir = dir;
		}

		public void run() {
			System.out.println("New connection-socket " + name
					+ " established!");
			try {
				inFromClient = new BufferedReader(new InputStreamReader(
						sock.getInputStream()));

				outToClient = new DataOutputStream(sock.getOutputStream());

				// --------- Start of Protocol ------------------------
				if (readGETrequest()) {
					readHeaderLines(); // for later use
					if (checkFile()) {
						sendContent();
					} else {
						send404ErrorMessage();
					}
				}
				// --------- End of Protocol ------------------------
				sock.close();
				System.out.println("WebServer-Thread " + name + " stopped!");
			} catch (IOException e) {
				System.err.println("Connection aborted by client!");
			}
		}

		/**
		 * Read "GET"-request message and extract filename. Return true if
		 * request is syntactically ok
		 */
		private boolean readGETrequest() throws IOException {
			String requestMessageLine = readFromClient();
			StringTokenizer tokenizedLine = new StringTokenizer(
					requestMessageLine);
			if (tokenizedLine.nextToken().equalsIgnoreCase("GET")) {
				relFileName = tokenizedLine.nextToken();
				if (relFileName.contains("..") || relFileName.equals("/")) {
					// attack with /..-operator or no filename given
					relFileName = "index.html";
				} else if (relFileName.startsWith("/")) {
					// cut leading '/' in filename
					relFileName = relFileName.substring(1);
				}
				fileName = defaultDir + relFileName;
				System.out.println("Filename: " + fileName);
				return true;
			} else {
				writeToClient("HTTP/1.0 400 Bad Request");
				writeToClient("");
				return false;
			}
		}

		/** Read all header lines and put them into the hashMap headerLines */
		private void readHeaderLines() throws IOException {
			String line;
			String[] headerLineArray;

			headerLines.clear();
			line = readFromClient();
			while (line.length() > 0) {
				headerLineArray = line.split(":");
				try {
					headerLines.put(headerLineArray[0].trim(),
							headerLineArray[1].trim());
				} catch (IndexOutOfBoundsException ex) {
				}
				line = readFromClient();
			}
		}

		/**
		 * Instantiate file stream and determine length. Return false if file
		 * not found
		 */
		private boolean checkFile() {
			try {
				inFile = new FileInputStream(fileName);
			} catch (FileNotFoundException e) {
				return false;
			}
			File file = new File(fileName);
			fileLength = (long) file.length();
			return true;
		}

		/** Send the headerlines and the file content */
		private void sendContent() throws IOException {
			try {
				writeToClient("HTTP/1.0 200 OK");
				if (fileName.endsWith(".html"))
					writeToClient("Content-Type:text/html");
				if (fileName.endsWith(".jpg"))
					writeToClient("Content-Type:image/jpeg");
				if (fileName.endsWith(".gif"))
					writeToClient("Content-Type:image/gif");
				if (fileName.endsWith(".pdf"))
					writeToClient("Content-Type:application/pdf");
				writeToClient("Content-Length: " + fileLength);
				writeToClient("");
			} catch (IOException ex) {
				throw ex;
			}
			// write body 
			// --------- Start of file copy operation ------------------------
			byte[] buffer = new byte[4096];
			int len;
			try {
				while ((len = inFile.read(buffer)) > 0) {
					outToClient.write(buffer, 0, len);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/** Read the next line from the client socket */
		private String readFromClient() throws IOException {
			String inputString = inFromClient.readLine();
			if (inputString == null) {
				throw new IOException();
			}
			System.out.println("WebServer-Thread " + name
					+ " has read the message: " + inputString);
			return inputString;
		}

		/** Write one line to the client socket */
		private void writeToClient(String returnString) throws IOException {
			try {
				outToClient.writeBytes(returnString + '\r' + '\n');
			} catch (IOException e) {
				System.err.println(e.toString());
			}
			System.out.println("WebServer-Thread " + name
					+ " has written the message: " + returnString);
		}

		/** Send 404-Error to the client */
		private void send404ErrorMessage() throws IOException {
			String htmlContent = "<html> <head><title>Fehler: Seite nicht gefunden</title></head>"
					+ "<body><h1>Die gew&uuml;nschte Seite konnte leider nicht gefunden werden (HTTP-Fehler 404)!</h1></body></html>";
			writeToClient("HTTP/1.0 404 Not Found");
			writeToClient("Content-Type:text/html");
			writeToClient("Content-Length: " + htmlContent.length());
			writeToClient("");
			writeToClient(htmlContent);
		}
	}

	/** Returns the current classpath directory with separator '/' */
	private String getClasspathDir() {
		String classpath = System.getProperty("java.class.path");
		if (classpath.indexOf(".jar") > 0) {
			/* jar-File als Classpath angegeben */
			classpath = new File(classpath).getParent();
		}
		classpath = classpath
				.replace(System.getProperty("file.separator"), "/");
		return classpath;
	}

	public static void main(String args[]) throws IOException {
		WebServer myServer = new WebServer();
		myServer.runWebServer();
	}
}
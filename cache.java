import java.io.*;
import java.net.*;

public class cache {
    private static final String CACHE_DIRECTORY = "./cache_fl"; // Directory for storing cached files
    private static boolean isRequestInProgress = false; // Flag to show if a req is in progress
    private static final int TIMEOUT = 1000; // 1 second timeout for ACK

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java cache <cachePort> <serverIP> <serverPort> <protocol>");
            return;
        }

        int cachePort = Integer.parseInt(args[0]);
        String serverIP = args[1];
        int serverPort = Integer.parseInt(args[2]);
        String protocol = args[3];

        // Create cache directory if it doesn't exist
        new File(CACHE_DIRECTORY).mkdirs();

        if (protocol.equals("tcp")) {
            try (ServerSocket serverSocket = new ServerSocket(cachePort)) {
                System.out.println("Cache is running on port " + cachePort + "...");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleTCPClient(clientSocket, serverIP, serverPort)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (protocol.equals("snw")) {
            System.out.println("Cache is running on port " + cachePort + " using SNW protocol...");
            try (DatagramSocket serverSocket = new DatagramSocket(cachePort)) {
                while (true) {
                    handleSNWClient(serverSocket, serverIP, serverPort);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Handle TCP client requests
    private static void handleTCPClient(Socket clientSocket, String serverIP, int serverPort) {
        try (InputStream input = clientSocket.getInputStream();
             OutputStream output = clientSocket.getOutputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {

            String request = reader.readLine(); // Read command from the client

            if (request.startsWith("get ")) {
                String fileName = request.substring(4);
                File cachedFile = new File(CACHE_DIRECTORY + File.separator + fileName);

                // Check if file is in cache
                if (cachedFile.exists()) {
                    // If file is in cache send it back
                    try (FileInputStream fileInputStream = new FileInputStream(cachedFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;

                        System.out.println("Delivering file from cache: " + fileName);
                        output.write("File delivered from cache.\n".getBytes()); // before sending contents, show file delivery

                        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                        }
                    }
                } else {
                    //  request from server if file doesnt exist
                    output.write("File not found in cache. Requesting from server...\n".getBytes());
                    System.out.println("File not found in cache. Requesting from server: " + fileName);
                    requestFileFromServer(fileName, serverIP, serverPort, output);
                }
            } else {
                output.write("Invalid command.\n".getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handle SNW client requests
     private static void handleSNWClient(DatagramSocket serverSocket, String serverIP, int serverPort) {
        try {
            byte[] receiveBuffer = new byte[4096];
            DatagramPacket requestPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            serverSocket.receive(requestPacket); // wait for a request from a client

            String request = new String(requestPacket.getData(), 0, requestPacket.getLength());
            System.out.println("Received request: " + request); // debugging purposes

            if (request.startsWith("get ")) {
                String fileName = request.substring(4);

                //avoid redundant requests for same file
                if (isRequestInProgress) {
                    System.out.println("Another request is already in progress. Ignoring duplicate request.");
                    return;
                }

                isRequestInProgress = true; // mark request as in progress
                File cachedFile = new File(CACHE_DIRECTORY + File.separator + fileName);

                if (cachedFile.exists()) {
                    // send back if cache has file
                    sendFileFromCache(fileName, requestPacket, serverSocket);
                } else {
                    //req from server if cache does not have file
                    System.out.println("File not found in cache. Requesting from server: " + fileName);
                    requestFileFromServerSNW(fileName, serverIP, serverPort, requestPacket.getAddress(), requestPacket.getPort(), serverSocket);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            isRequestInProgress = false; // Reset the flag after the request is completed
        }
    }



    // Method to send a file from cache
    private static void sendFileFromCache(String fileName, DatagramPacket requestPacket, DatagramSocket serverSocket) {
        try (FileInputStream fileInputStream = new FileInputStream(CACHE_DIRECTORY + File.separator + fileName)) {
            byte[] buffer = new byte[4096];
            int bytesRead;

            System.out.println("Delivering file from cache: " + fileName);
            byte[] responseBuffer = "File delivered from cache.\n".getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length, requestPacket.getAddress(), requestPacket.getPort());
            serverSocket.send(responsePacket);

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                DatagramPacket dataPacket = new DatagramPacket(buffer, bytesRead, requestPacket.getAddress(), requestPacket.getPort());
                serverSocket.send(dataPacket);
            }
            System.out.println("File transfer from cache completed for: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




    // Method to request a file from the server for TCP
    private static void requestFileFromServer(String fileName, String serverIP, int serverPort, OutputStream output) {
        try (Socket socket = new Socket(serverIP, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Req the file from the server
            System.out.println("Requesting file from server: " + fileName);
            out.println("get " + fileName);

            String responseLine;
            File cachedFile = new File(CACHE_DIRECTORY + File.separator + fileName);
            try (FileOutputStream fileOutputStream = new FileOutputStream(cachedFile)) {
                while ((responseLine = in.readLine()) != null) {
                    fileOutputStream.write((responseLine + "\n").getBytes());
                    System.out.println("Received line from server: " + responseLine);
                }
            }
            output.write(("File saved in cache: " + fileName + "\n").getBytes());
            System.out.println("File saved in cache: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Requesting file from server for SNW with ACK handling
    private static void requestFileFromServerSNW(String fileName, String serverIP, int serverPort, InetAddress clientAddress, int clientPort, DatagramSocket serverSocket) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] requestBuffer = ("get " + fileName).getBytes();
            DatagramPacket requestPacket = new DatagramPacket(requestBuffer, requestBuffer.length, InetAddress.getByName(serverIP), serverPort);
            socket.send(requestPacket); // Send request to server
            System.out.println("Sent request for file: " + fileName);

            byte[] receiveBuffer = new byte[4096];
            DatagramPacket responsePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            socket.receive(responsePacket);
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength()).trim();
            System.out.println("Received response: " + response);

            if (!response.startsWith("LEN:")) {
                throw new IOException("Expected file length but received: " + response);
            }

            int fileLength = Integer.parseInt(response.split(":")[1].trim());
            System.out.println("File length received: " + fileLength + " bytes.");

            // Send ACK after receiving LEN
            sendAck(socket, responsePacket);

            // get file data in chunks
            while (true) {
                socket.receive(responsePacket); // Wait for data packet or END

                response = new String(responsePacket.getData(), 0, responsePacket.getLength()).trim();
                if (response.equals("END")) {
                    System.out.println("End of file transfer from server.");
                    break;
                }

                // send data packet to  client
                DatagramPacket clientResponsePacket = new DatagramPacket(responsePacket.getData(), responsePacket.getLength(), clientAddress, clientPort);
                serverSocket.send(clientResponsePacket);

                // Send a ACK for each received packet
                sendAck(socket, responsePacket);
            }
            System.out.println("File relayed to client.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Helper method to send an ACK
    private static void sendAck(DatagramSocket socket, DatagramPacket receivedPacket) throws IOException {
        String ackMessage = "ACK";
        byte[] ackData = ackMessage.getBytes();
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, receivedPacket.getAddress(), receivedPacket.getPort());
        socket.send(ackPacket);
        System.out.println("Sent ACK for received packet.");
    }



}

import java.io.*;
import java.net.*;


public class server {
    private static boolean receivingFile = false; // Flag for file receiving mode
    private static String currentFileName = null; // Tracks the current file being received
    private static final int TIMEOUT = 1000; // 1 second timeout for ACK

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java server <port> <protocol>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String protocol = args[1];
        String savePath = "./server_fl";

        // Create the directory if it doesn't alr exist
        new File(savePath).mkdirs();

        if (protocol.equals("tcp")) {
            try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))) {
                System.out.println("Server is running on port " + port + " with protocol " + protocol);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleTCPClient(clientSocket, savePath)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else { // SNW protocol
            try (DatagramSocket serverSocket = new DatagramSocket(port)) {
                System.out.println("Server is running on port " + port + " with protocol " + protocol);

                while (true) {
                    byte[] buffer = new byte[4096];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    serverSocket.receive(packet); // waits for request from client

                    new Thread(() -> handleSNWClient(packet, savePath, serverSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleTCPClient(Socket clientSocket, String savePath) {
        try (InputStream input = clientSocket.getInputStream();
             OutputStream output = clientSocket.getOutputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {

            String request = reader.readLine(); // Read command from the client
            System.out.println("Received request: " + request); // debugging purposes

            if (request.startsWith("put ")) {
                String fileName = request.substring(4);
                System.out.println("Uploading file: " + fileName); //debugging purposes
                receiveTCPFile(fileName, input, savePath);
                output.write("File successfully uploaded.\n".getBytes());
            } else if (request.startsWith("get ")) {
                String fileName = request.substring(4);
                sendTCPFile(fileName, output);
            } else {
                output.write("Invalid command.\n".getBytes());
                System.out.println("Invalid command received."); // debugging purposes
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void receiveTCPFile(String fileName, InputStream input, String savePath) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(savePath + File.separator + fileName)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
            System.out.println("File received successfully: " + fileName); // debugging purposes
        } catch (IOException e) {
            System.out.println("Error receiving file: " + savePath + File.separator + fileName);
            e.printStackTrace();
        }
    }

    private static void sendTCPFile(String fileName, OutputStream output) {
        File file = new File("./server_fl" + File.separator + fileName);
        if (file.exists()) {
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.write("END\n".getBytes()); // the end of the file transfer
                System.out.println("File sent successfully: " + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                output.write("File NOT found on server.\n".getBytes());
                System.out.println("File not found: " + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleSNWClient(DatagramPacket packet, String savePath, DatagramSocket serverSocket) {
        String request = new String(packet.getData(), 0, packet.getLength()).trim();
        System.out.println("Received SNW request: '" + request + "'");


        if (receivingFile) {
            receiveSNWFile(currentFileName, packet, savePath, serverSocket);
            return;
        }

        // Handle "get" command for file downloads
        if (request.startsWith("get ")) {
            String fileName = request.substring(4).trim(); // get file name after 'get '
            System.out.println("Preparing to send file: " + fileName);
            sendSNWFile(fileName, packet, serverSocket); // Call method to send the file with snw prot
        }

        // Ignore ACK messages received as part of file transfers
        if (request.equals("ACK")) {
            System.out.println("Received ACK during file transfer, ignoring as command.");
            return;
        }

        else if (request.startsWith("put ")) {
            currentFileName = request.substring(4).trim(); // get the file name
            System.out.println("Preparing to receive file: " + currentFileName);
            sendResponse("ACK", packet, serverSocket); // Ack put command
        }


        else if (request.startsWith("LEN:")) {
            String lengthStr = request.substring(4).trim(); // Extract length after 'LEN:'
            try {
                int fileLength = Integer.parseInt(lengthStr);
                System.out.println("Received file length: " + fileLength + " bytes.");
                sendResponse("ACK", packet, serverSocket); // Ack 'LEN' command
                receivingFile = true; // turn on file receiving mode
            } catch (NumberFormatException e) {
                System.out.println("Invalid file length format: '" + lengthStr + "'");
                sendResponse("Invalid length format.", packet, serverSocket);
            }
        } else {
            System.out.println("Unrecognized command: '" + request + "'");
            sendResponse("Invalid command.", packet, serverSocket);
        }
    }

    private static void receiveSNWFile(String fileName, DatagramPacket packet, String savePath, DatagramSocket serverSocket) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(savePath + File.separator + fileName, true)) {
            byte[] receiveBuffer = new byte[1000];

            // Process the init packet
            String data = new String(packet.getData(), 0, packet.getLength()).trim();
            if (data.equals("END")) {
                finalizeFileTransfer();
                return;
            }
            fileOutputStream.write(packet.getData(), 0, packet.getLength());
            sendResponse("ACK", packet, serverSocket); // ACK for the first data packet

            // recieve and write data until END is reached
            while (receivingFile) {
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket);

                data = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                if (data.equals("END")) {
                    finalizeFileTransfer();
                    break;
                } else {
                    fileOutputStream.write(receivePacket.getData(), 0, receivePacket.getLength());
                    sendResponse("ACK", receivePacket, serverSocket); // Send ACK for each data packet
                }
            }
            System.out.println("File received successfully: " + fileName);
        } catch (IOException e) {
            System.out.println("Error receiving file: " + savePath + File.separator + fileName);
            e.printStackTrace();
        }
    }

    // finish file transfer and reset receiving mode
    private static void finalizeFileTransfer() {
        receivingFile = false;
        currentFileName = null;
        System.out.println("File transfer completed.");
    }


    private static void sendSNWFile(String fileName, DatagramPacket packet, DatagramSocket serverSocket) {
        System.out.println("\nIn sendSNWFile method");
        File file = new File("./server_fl" + File.separator + fileName);
        if (file.exists()) {
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[1000];
                int bytesRead;

                // Send file length
                int fileLength = (int) file.length();
                String lengthMessage = "LEN:" + fileLength;
                DatagramPacket lengthPacket = new DatagramPacket(lengthMessage.getBytes(), lengthMessage.length(), packet.getAddress(), packet.getPort());
                serverSocket.send(lengthPacket);
                System.out.println("Sent file length: " + fileLength + " bytes.");

                // Wait for ACK for LEN with retry if needed
                waitForAckWithRetry(serverSocket, 3); //max retry is 3

                // Begin sending file data in chunks of 1000 bytes
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    DatagramPacket dataPacket = new DatagramPacket(buffer, bytesRead, packet.getAddress(), packet.getPort());
                    serverSocket.send(dataPacket);
                    System.out.println("Sent packet with " + bytesRead + " bytes.");

                    // Wait for ACK for each data packet with retry if needed
                    waitForAckWithRetry(serverSocket, 3);
                }

                // Send end message
                byte[] endMessage = "END".getBytes();
                DatagramPacket endPacket = new DatagramPacket(endMessage, endMessage.length, packet.getAddress(), packet.getPort());
                serverSocket.send(endPacket);
                System.out.println("File transfer completed successfully for: " + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                byte[] notFoundMessage = "File NOT found on server.".getBytes();
                DatagramPacket notFoundPacket = new DatagramPacket(notFoundMessage, notFoundMessage.length, packet.getAddress(), packet.getPort());
                serverSocket.send(notFoundPacket);
                System.out.println("File not found: " + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Method to wait for ACK with retry
    private static void waitForAckWithRetry(DatagramSocket socket, int maxRetries) throws IOException {
        byte[] ackBuffer = new byte[256];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                socket.setSoTimeout(TIMEOUT); // Set timeout for ACK
                socket.receive(ackPacket); // Wait for ACK
                String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength()).trim();
                System.out.println("Received ACK: " + ackMessage);

                if ("ACK".equals(ackMessage)) {
                    return; // ACK received, exit wait
                } else {
                    System.out.println("Unexpected ACK content, retrying...");
                }
            } catch (SocketTimeoutException e) {
                attempts++;
                System.out.println("ACK not received, retry attempt " + attempts + "/" + maxRetries);
            }
        }
        throw new IOException("Did not receive valid ACK after " + maxRetries + " attempts.");
    }

    // Method to send a response back to client
    private static void sendResponse(String response, DatagramPacket packet, DatagramSocket serverSocket) {
        byte[] responseData = response.getBytes();
        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, packet.getAddress(), packet.getPort());
        try {
            serverSocket.send(responsePacket); // Send response packet
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

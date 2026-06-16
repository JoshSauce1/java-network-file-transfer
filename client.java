import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class client {
    public static void main(String[] args) {
        if (args.length != 5) {
            System.out.println("Usage: java client <serverIP> <serverPort> <cacheIP> <cachePort> <protocol>");
            return;
        }

        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String cacheIP = args[2];
        int cachePort = Integer.parseInt(args[3]);
        String protocol = args[4];
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Enter command: ");
            String command = scanner.nextLine();
            if (command.startsWith("put ")) {
                String filePath = command.substring(4);
                System.out.println("Awaiting server response for uploading file: " + filePath);
                String response = protocol.equals("tcp") ?
                        new tcp_transport().send(filePath, serverIP, serverPort) :
                        new snw_transport().send(filePath, serverIP, serverPort);
                System.out.println("Server response: " + response);
            } else if (command.startsWith("get ")) {
                String filePath = command.substring(4);
                System.out.println("Awaiting server response for retrieving file: " + filePath);

                // First try to retrieve file from the cache
                String response = protocol.equals("tcp") ?
                        new tcp_transport().requestFileFromCache(filePath, cacheIP, cachePort, serverIP, serverPort) :
                        new snw_transport().requestFileFromCache(filePath, cacheIP, cachePort, serverIP, serverPort);

                // Check the response from cache
                if (protocol.equals("tcp") && response.contains("File not found in cache.")) {
                    // Only request from the server if the file wasn't found in the cache
                    System.out.println("Requesting from server: " + filePath);
                    response = requestFileFromServer(filePath, serverIP, serverPort, protocol);
                }

                // Print final response, which can be from the cache or server
                System.out.println("Response: " + response);
            }
            else if (command.equals("quit")) {
                System.out.println("Exiting program!");
                break;
            }
            else {
                System.out.println("Invalid command. Please enter 'put', 'get', or 'quit'.");
            }
        }
        scanner.close();
    }

    private static String requestFileFromServer(String fileName, String serverIP, int serverPort, String protocol) {
        if (protocol.equals("tcp")) {
            try (Socket socket = new Socket(serverIP, serverPort);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Request file from server
                System.out.println("Requesting file from server: " + fileName);
                out.println("get " + fileName);

                // Read response from server
                StringBuilder fileContent = new StringBuilder();
                String responseLine;
                while ((responseLine = in.readLine()) != null) {
                    fileContent.append(responseLine).append("\n");
                    System.out.println("Received line from server: " + responseLine);
                }

                // Save the file content to client_fl folder
                saveFileToClientFolder(fileName, fileContent.toString());

                return "File successfully downloaded from server and saved in client_fl.";
            } catch (IOException e) {
                e.printStackTrace();
                return "Error in requesting file from server.";
            }
        } else {
            return requestFileFromServerSNW(fileName, serverIP, serverPort);
        }
    }



    private static String requestFileFromServerSNW(String fileName, String serverIP, int serverPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            // Send the init "get" command to request file
            byte[] requestBuffer = ("get " + fileName).getBytes();
            DatagramPacket requestPacket = new DatagramPacket(requestBuffer, requestBuffer.length, InetAddress.getByName(serverIP), serverPort);
            socket.send(requestPacket); // Send req to server

            StringBuilder fileContent = new StringBuilder();
            byte[] receiveBuffer = new byte[4096];

            while (true) {
                DatagramPacket responsePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(responsePacket); // Wait for response from server

                String responseLine = new String(responsePacket.getData(), 0, responsePacket.getLength()).trim();
                System.out.println("Response from server: " + responseLine);

                // Check for the END marker to stop
                if (responseLine.equals("END")) {
                    System.out.println("End of file transfer received.");
                    break;
                } else {
                    // Append received data to file content
                    fileContent.append(responseLine).append("\n");

                    // Send an ACK for each packet
                    String ackMessage = "ACK";
                    byte[] ackBytes = ackMessage.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackBytes, ackBytes.length, responsePacket.getAddress(), responsePacket.getPort());
                    socket.send(ackPacket);
                    System.out.println("Sent ACK to server.");
                }
            }

            // Save the file content to client_fl folder
            saveFileToClientFolder(fileName, fileContent.toString());

            return "File successfully downloaded from server and saved in client_fl.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Error in requesting file from server.";
        }
    }

    private static void saveFileToClientFolder(String fileName, String content) {
        String clientFolderPath = "./client_fl"; // Path for client_fl folder
        new File(clientFolderPath).mkdirs(); // Create folder if it doesn't exist

        try (FileOutputStream fos = new FileOutputStream(clientFolderPath + File.separator + fileName)) {
            fos.write(content.getBytes());
            System.out.println("File saved successfully: " + clientFolderPath + File.separator + fileName);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error saving file to client_fl folder.");
        }
    }
}

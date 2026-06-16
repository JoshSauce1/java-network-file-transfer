import java.io.*;
import java.net.*;

public class snw_transport {
    private static final int TIMEOUT = 1000; // 1 second timeout ACK

    public String send(String filePath, String ip, int port) {
        try (DatagramSocket socket = new DatagramSocket();
             FileInputStream fileInputStream = new FileInputStream(filePath)) {

            byte[] buffer = new byte[1000]; // hold data
            int dataRead;

            // Send command
            String command = "put " + new File(filePath).getName();
            DatagramPacket commandPacket = new DatagramPacket(command.getBytes(), command.length(), InetAddress.getByName(ip), port);
            socket.send(commandPacket);
            System.out.println("Sent command: " + command);
            waitForAck(socket); // Wait for ACK

            // Send length
            int fileLength = (int) new File(filePath).length();
            String lengthMessage = "LEN:" + fileLength;
            DatagramPacket lengthPacket = new DatagramPacket(lengthMessage.getBytes(), lengthMessage.length(), InetAddress.getByName(ip), port);
            socket.send(lengthPacket);
            System.out.println("Sent file length: " + fileLength + " bytes.");
            waitForAck(socket);

            // Send file data
            while ((dataRead = fileInputStream.read(buffer)) != -1) {
                DatagramPacket dataPacket = new DatagramPacket(buffer, dataRead, InetAddress.getByName(ip), port);
                socket.send(dataPacket);
                System.out.println("Sent packet with " + dataRead + " bytes.");
                waitForAck(socket);
            }

            // Send end message
            byte[] endMessage = "END".getBytes();
            DatagramPacket endPacket = new DatagramPacket(endMessage, endMessage.length, InetAddress.getByName(ip), port);
            socket.send(endPacket);
            System.out.println("File sending completed via SNW.");
            return "File successfully uploaded via SNW.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Error in file upload via SNW.";
        }
    }



    // Method to wait for ACK
    private void waitForAck(DatagramSocket socket) throws IOException {
        byte[] ackBuffer = new byte[256];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        socket.setSoTimeout(TIMEOUT); // Set 1 sec timeout for ACK
        try {
            socket.receive(ackPacket); // Wait for ACK
            String ackMessage = new String(ackPacket.getData(), 0, ackPacket.getLength());
            System.out.println("Received ACK: " + ackMessage);
            if (!ackMessage.equals("ACK")) {
                throw new IOException("Did not receive valid ACK.");
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Did not receive ACK, terminating.");
        }
    }


    // Method to request a file from the cache
    public String requestFileFromCache(String filePath, String cacheIP, int cachePort, String serverIP, int serverPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] requestBuffer = ("get " + filePath).getBytes();
            DatagramPacket requestPacket = new DatagramPacket(requestBuffer, requestBuffer.length, InetAddress.getByName(cacheIP), cachePort);
            socket.send(requestPacket); // Send request to cache

            byte[] receiveBuffer = new byte[4096];
            DatagramPacket responsePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(responsePacket); // Wait for response from cache

            String response = new String(responsePacket.getData(), 0, responsePacket.getLength()).trim();
            System.out.println("Response from cache: " + response);

            if (response.contains("File delivered from cache.")) {
                return "File successfully downloaded from cache.";
            }
            else {
                System.out.println("File not found in cache. Requesting from server...");
                return requestFileFromServer(filePath, serverIP, serverPort);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Error in requesting file from cache.";
        }
    }


    // Method to request a file from the server
    private String requestFileFromServer(String fileName, String serverIP, int serverPort) {
        try (DatagramSocket socket = new DatagramSocket();
             FileOutputStream fileOutputStream = new FileOutputStream("./client_fl" + File.separator + fileName)) {

            byte[] requestBuffer = ("get " + fileName).getBytes();
            DatagramPacket requestPacket = new DatagramPacket(requestBuffer, requestBuffer.length, InetAddress.getByName(serverIP), serverPort);
            socket.send(requestPacket); // Send request to server

            byte[] receiveBuffer = new byte[1000];
            String data;

            while (true) {
                DatagramPacket responsePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(responsePacket); // Wait for response from server

                data = new String(responsePacket.getData(), 0, responsePacket.getLength()).trim();
                System.out.println("Received data from server: " + data);

                if (data.startsWith("LEN:")) {
                    int fileLength = Integer.parseInt(data.substring(4).trim());
                    System.out.println("Expected file length: " + fileLength + " bytes.");
                    sendAck(socket, responsePacket); // Ack LEN
                } else if (data.equals("END")) {
                    System.out.println("End of file transfer from server.");
                    break;
                } else {
                    // Write data to file
                    fileOutputStream.write(responsePacket.getData(), 0, responsePacket.getLength());

                    // Send ACK for each packet
                    sendAck(socket, responsePacket);
                }
            }

            return "File successfully downloaded from server and saved in client_fl.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Error in requesting file from server.";
        }
    }

    // Method to send ACK
    private void sendAck(DatagramSocket socket, DatagramPacket responsePacket) throws IOException {
        String ackMessage = "ACK";
        byte[] ackData = ackMessage.getBytes();
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, responsePacket.getAddress(), responsePacket.getPort());
        socket.send(ackPacket);
        System.out.println("Sent ACK for received packet.");
    }


}

import java.io.*;
import java.net.*;

public class tcp_transport {
    // Function to send a file
    public String send(String filePath, String ip, int port) {
        try (Socket socket = new Socket(ip, port);
             OutputStream outputStream = socket.getOutputStream();
             FileInputStream fileInputStream = new FileInputStream(filePath)) {

            // Send the command first
            PrintWriter out = new PrintWriter(outputStream, true);
            out.println("put " + new File(filePath).getName()); // Send only the file name

            byte[] buffer = new byte[4096];
            int dataRead;
            System.out.println("Awaiting server response for uploading file: " + filePath);
            System.out.println("Sending file: " + filePath);

            while ((dataRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, dataRead);
                System.out.println("Sent " + dataRead + " bytes.");
            }

            return "File successfully uploaded.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Error in file upload.";
        }
    }

    // Method to request file from the cache
    public String requestFileFromCache(String filePath, String cacheIP, int cachePort, String serverIP, int serverPort) {
        try (Socket socket = new Socket(cacheIP, cachePort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send request to the cache
            System.out.println("Requesting file from cache: " + filePath);
            out.println("get " + filePath);
            String response = in.readLine();
            System.out.println("Response from cache: " + response);

            if (response.equals("File delivered from cache.")) {
                return "File successfully downloaded from cache.";
            } else {
                // If not found in cache, request from server
                System.out.println("File not found in cache. Requesting from server...");
                return requestFileFromServer(filePath, serverIP, serverPort);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Error in requesting file from cache.";
        }
    }

    private String requestFileFromServer(String fileName, String serverIP, int serverPort) {
        try (Socket socket = new Socket(serverIP, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Req the file from the server
            System.out.println("Requesting file from server: " + fileName);
            out.println("get " + fileName);

            // Read response from the server
            String responseLine;
            StringBuilder fileContent = new StringBuilder(); // for storing file content

            while ((responseLine = in.readLine()) != null) {
                System.out.println("Response from server: " + responseLine);
                if (responseLine.equals("File sent successfully.")) {
                    break;
                } else {
                    fileContent.append(responseLine).append("\n"); // gather file content
                }
            }

            // save received file content to client_fl folder
            saveFileToClientFolder(fileName, fileContent.toString());

            return "File successfully downloaded from server and saved in client_fl.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Error in requesting file from server.";
        }
    }


    private void saveFileToClientFolder(String fileName, String content) {
        String clientFolderPath = "./client_fl"; // Path for client_fl folder
        new File(clientFolderPath).mkdirs(); // create folder if doesnt exists

        try (FileOutputStream fos = new FileOutputStream(clientFolderPath + File.separator + fileName)) {
            fos.write(content.getBytes());
            System.out.println("File saved successfully: " + clientFolderPath + File.separator + fileName);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error saving file to client_fl folder.");
        }
    }
}

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class WebServer {

    private static final String NOT_FOUND_RESPONSE = "HTTP/1.1 404 Not Found\r\n\r\n";
    private static final String OK_RESPONSE = "HTTP/1.1 200 OK\r\n";
    private static final String UNAUT_RESPONSE = "HTTP/1.1 401 Unauthorized\r\n...\r\n";
    private static final String SERVER_NAME = "my_web_server";
    private static final String DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.GERMANY);

    public static void main(String[] args) {
        if (args.length != 2) {
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String publicDirectory = args[1];

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            System.out.println("Server source file exists!");
            System.out.println("Detailed path: " + publicDirectory);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostName() + ":"
                        + clientSocket.getPort());
                handleRequest(clientSocket, publicDirectory);
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleRequest(Socket clientSocket, String publicDirectory) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null)
                return;

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3)
                return;

            String method = requestParts[0];
            String path = requestParts[1];
            String version = requestParts[2];

            System.out.println("Method: " + method + ", Path: " + path + ", Version: " + version);

            // Ensure path starts with / to prevent directory traversal
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            Path filePath = Paths.get(publicDirectory, path).normalize();
            Path publicDirPath = Paths.get(publicDirectory).normalize();

            if (!filePath.startsWith(publicDirPath) || !Files.exists(filePath)) {
                System.out.println("Requested file does not exist within the public directory!");
                sendResponse(out, NOT_FOUND_RESPONSE);
                System.out.println("Response Header:" + NOT_FOUND_RESPONSE);
                return;
            }

            System.out.println("Requested file exists!");
            if (Files.isDirectory(filePath)) {
                filePath = filePath.resolve("index.html");
            }

            String contentType = Files.probeContentType(filePath);
            byte[] content = Files.readAllBytes(filePath);
            String date = sdf.format(new Date());
            String response;

            if ("POST".equalsIgnoreCase(method) && "/fun.html".equals(path)) {
                StringBuilder payload = new StringBuilder();
                while (in.ready()) {
                    payload.append((char) in.read());
                }
                Map<String, String> postData = parsePostData(payload.toString());
                System.out.println("Username from HTML:" + postData.get("username"));
                System.out.println("Password from HTML:" + postData.get("password"));
                System.out.println("Validate:" + validateUser(postData.get("username"), postData.get("password")));
                if (!validateUser(postData.get("username"), postData.get("password"))) {
                    sendResponse(out, UNAUT_RESPONSE);
                    System.out.println("Response Header:" + UNAUT_RESPONSE);
                    return;
                }
            }

            if ("/redirect.html".equals(path)) {
                String redirectUrl = "https://www.google.se/";
                response = "HTTP/1.1 302 Found\r\n" +
                        "Location: " + redirectUrl + "\r\n" +
                        "Date: " + date + "\r\n" +
                        "Server: " + SERVER_NAME + "\r\n" +
                        "Content-Length: " + content.length + "\r\n" +
                        "Connection: close\r\n" +
                        "Content-Type: " + contentType + "\r\n\r\n";
            } else {
                response = OK_RESPONSE +
                        "Date: " + date + "\r\n" +
                        "Server: " + SERVER_NAME + "\r\n" +
                        "Content-Length: " + content.length + "\r\n" +
                        "Connection: close\r\n" +
                        "Content-Type: " + contentType + "\r\n\r\n";
            }

            // Print the response headers to the console
            System.out.println("Response Headers:\n" + response);

            out.write(response.getBytes());
            if (!"/redirect.html".equals(path)) {
                out.write(content);
            }
            out.flush();

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void sendResponse(OutputStream out, String response) throws IOException {
        out.write(response.getBytes());
        out.flush();
    }

    private static Map<String, String> parsePostData(String data) {
        Map<String, String> postData = new HashMap<>();

        String usernameKey = "username=";
        String passwordKey = "password=";
        int usernameStart = data.indexOf(usernameKey);
        int passwordStart = data.indexOf(passwordKey);

        if (usernameStart != -1) {
            usernameStart += usernameKey.length();
            int usernameEnd = data.indexOf('&', usernameStart);
            if (usernameEnd == -1) { 
                usernameEnd = data.length();
            }
            String username = data.substring(usernameStart, usernameEnd);
            postData.put("username", username); 
        }
        if (passwordStart != -1) {
            passwordStart += passwordKey.length();
            String password = data.substring(passwordStart); 
            postData.put("password", password); 
        }

        return postData;
    }

    private static boolean validateUser(String username, String password) {
        try (BufferedReader br = new BufferedReader(new FileReader("./users.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length == 2) {
                    String fileUsername = parts[0].trim();
                    String filePassword = parts[1].trim();
                    if (fileUsername.equals(username) && filePassword.equals(password)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}

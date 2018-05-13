/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testhttp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 *
 * @author seanb
 */
public class ProxyThread extends Thread {

    public static final Pattern CONNECT_PATTERN = Pattern.compile("CONNECT (.+):(.+) HTTP/(1\\.[01])",
            Pattern.CASE_INSENSITIVE);

    private final Socket clientSocket;
    private boolean previousWasR = false;
    private String wardenIP;
    private ArrayList<String> blockedList = new ArrayList<>();
    private ArrayList<String> wordist = new ArrayList<>();

    public ProxyThread(Socket clientSocket,String ip,ArrayList<String> words, ArrayList<String> sites) {
        this.clientSocket = clientSocket;
        this.wardenIP = ip;
        this.wordist = words;
        this.blockedList = sites;
    }

    @Override
    public void run() {
        try {
            String request = readLine(clientSocket);
            System.out.println("Request:" + request);
            Matcher matcher = CONNECT_PATTERN.matcher(request);
            String httpMethod = "";
            String urlString = "";
            URL url;
            boolean blocksite = false;
            int BUFFER_SIZE = 32768;
            HttpURLConnection conn = null;
            String response = "allow";

            ArrayList<String> badsite = new ArrayList();

            for(String site:blockedList) {
                if (request.contains(site)) {
                    blocksite = true;
                }
            }

            if (matcher.matches()) {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream(),
                        "ISO-8859-1");
                if (blocksite) {
                   
                    outputStreamWriter.write("HTTP/" + matcher.group(3) + " 403 Forbidden\r\n");
                    outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                    outputStreamWriter.write("\r\n");
                    outputStreamWriter.flush();
                    return;
                }

                /*
                    This section of the code scans the webpage using Jsoup.
                 */
                try {

                    int i = 0;
                    final String userAgent = "<Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36>";
                    System.out.println("Website:" + matcher.group(1));
                    Document doc = Jsoup.connect("https://" + matcher.group(1)).userAgent(userAgent).get();
                    String text = doc.body().text();
 
                    for (String badword : wordist) {
                        int word = 0;
                        Pattern p = Pattern.compile(" "+badword+" ");
                        Matcher m = p.matcher(text);
                        while (m.find()) {
                            i++;
                            word++;
                        }
                    }

                    /*
                        This code sends the message to the android app
                     */
                    if (i > 19) {
                        response = "block";
                    } else if (i > 4) {
                        try {
                            String website = "https://" + matcher.group(1);
                            Socket socket = new Socket(wardenIP, 5000);
                            System.out.println(website);
                            DataOutputStream os = new DataOutputStream(socket.getOutputStream());
                            BufferedWriter bufferWriter = new BufferedWriter(new OutputStreamWriter(os));
                            bufferWriter.write(website + "\n");
                            bufferWriter.flush();

                            DataInputStream is = new DataInputStream(socket.getInputStream());
                            BufferedReader br = new BufferedReader(new InputStreamReader(is));

                            response = br.readLine();

                            os.close();
                            is.close();
                            socket.close();
                            System.out.println("Result:" + response);
                        } catch (IOException ex) {
                            response = "block";
                        }

                    }

                    System.out.println("Number of bad words on site " + request + ": " + i);

                } catch (IOException ex) {
                    System.out.println(ex);
                }

                String header;
                do {
                    header = readLine(clientSocket);
                } while (!"".equals(header));

                final Socket forwardSocket;
                try {
                    forwardSocket = new Socket(matcher.group(1), Integer.parseInt(matcher.group(2)));

                    //System.out.println(forwardSocket);
                } catch (IOException | NumberFormatException e) {
                    e.printStackTrace();  // TODO: implement catch
                    outputStreamWriter.write("HTTP/" + matcher.group(3) + " 502 Bad Gateway\r\n");
                    outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                    outputStreamWriter.write("\r\n");
                    outputStreamWriter.flush();
                    return;
                }
                try {

                    if (response.equals("block")) {
                        outputStreamWriter.write("HTTP/" + matcher.group(3) + " 403 Forbidden\r\n");
                        outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                        outputStreamWriter.write("\r\n");
                        outputStreamWriter.flush();
                        return;
                    } else {
                        outputStreamWriter.write("HTTP/" + matcher.group(3) + " 200 Connection established\r\n");
                        outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                        outputStreamWriter.write("\r\n");
                        outputStreamWriter.flush();

                        Thread remoteToClient = new Thread() {
                            @Override
                            public void run() {
                                forwardData(forwardSocket, clientSocket);
                            }
                        };
                        remoteToClient.start();
                        try {
                            if (previousWasR) {
                                int read = clientSocket.getInputStream().read();
                                if (read != -1) {
                                    if (read != '\n') {
                                        forwardSocket.getOutputStream().write(read);
                                    }
                                    forwardData(clientSocket, forwardSocket);
                                } else {
                                    if (!forwardSocket.isOutputShutdown()) {
                                        forwardSocket.shutdownOutput();
                                    }
                                    if (!clientSocket.isInputShutdown()) {
                                        clientSocket.shutdownInput();
                                    }
                                }
                            } else {
                                forwardData(clientSocket, forwardSocket);
                            }
                        } finally {
                            try {
                                remoteToClient.join();
                            } catch (InterruptedException e) {
                                e.printStackTrace();  // TODO: implement catch
                            }
                        }

                    }
                } finally {
                    forwardSocket.close();
                }
            } else if (request != null || !request.equals("")) {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream(),
                        "ISO-8859-1");
                if (blocksite) {

                    outputStreamWriter.write("HTTP/1.1 403 Forbidden\r\n");
                    outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                    outputStreamWriter.write("\r\n");
                    outputStreamWriter.flush();
                    return;
                }
                InputStream clientInput = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream();
                StringTokenizer st = new StringTokenizer(request);
                if (st.hasMoreTokens()) {
                    httpMethod = st.nextToken().toString();
                    //System.out.println("HTTP method......." + httpMethod);
                }

                if (st.hasMoreTokens()) {
                    urlString = st.nextToken().toString();
                } else {

                }

                if (httpMethod.equals("GET")) {
                    if (urlString.contains("http://")) {
                        url = new URL(urlString);
                    } else {
                        String urlString2 = new String("http://" + urlString);
                        url = new URL(urlString2);

                    }

                    try {

                        int i = 0;
                        final String userAgent = "<Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36>";
                        Document doc = Jsoup.connect(url.toString()).userAgent(userAgent).get();
                        String text = doc.body().text();
                        for (String badword : wordist) {
                            int word = 0;
                            Pattern p = Pattern.compile(" "+badword+" ");
                            Matcher m = p.matcher(text);
                            while (m.find()) {
                                i++;
                                word++;
                            }
                            if (word > 0) {
                                System.out.println(badword + ": " + word);
                            }

                        }

                        /*
                        This code sends the message to the android app
                         */
                        if (i > 19) {
                            response = "block";
                        } else if (i > 4) {

                            try {
                                String website = url.toString();
                                Socket socket = new Socket(wardenIP, 5000);
                                System.out.println(website);
                                DataOutputStream os = new DataOutputStream(socket.getOutputStream());
                                BufferedWriter bufferWriter = new BufferedWriter(new OutputStreamWriter(os));
                                bufferWriter.write(website + "\n");
                                bufferWriter.flush();

                                DataInputStream is = new DataInputStream(socket.getInputStream());
                                BufferedReader br = new BufferedReader(new InputStreamReader(is));

                                response = br.readLine();

                                os.close();
                                is.close();
                                socket.close();
                                System.out.println("Result:" + response);
                            } catch (IOException ex) {
                                response = "block";
                            }

                        }

                        System.out.println("Number of bad words on site " + request + ": " + i);

                    } catch (IOException ex) {
                        System.out.println(ex);
                    }

                    if (response.equals("block")) {
                        outputStreamWriter.write("HTTP/1.1 403 Forbidden\r\n");
                        outputStreamWriter.write("Proxy-agent: Simple/0.1\r\n");
                        outputStreamWriter.write("\r\n");
                        outputStreamWriter.flush();
                        return;
                    }

                    conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true);
                    conn.setRequestMethod("GET");

                    // Get server streams.
                    final InputStream streamFromServer = conn.getInputStream();
                    

                    // Read the server's responses
                    // and pass them back to the client.
                    byte by[] = new byte[BUFFER_SIZE];

                    int index = streamFromServer.read(by, 0, BUFFER_SIZE);
                    while (index != -1) {
                        output.write(by, 0, index);
                        output.flush();
                        index = streamFromServer.read(by, 0, BUFFER_SIZE);
                    }
                    //output.flush();

                    // The server closed its connection to us, so we close our
                    // connection to our client.
                    output.close();
                    clientInput.close();
                    conn.disconnect();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();

        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();

            }
        }
    }

    private static void forwardData(Socket inputSocket, Socket outputSocket) {
        try {
            InputStream inputStream = inputSocket.getInputStream();
            try {
                OutputStream outputStream = outputSocket.getOutputStream();
                try {
                    byte[] buffer = new byte[4096];
                    int read;
                    do {
                        read = inputStream.read(buffer);
                        if (read > 0) {
                            outputStream.write(buffer, 0, read);
                            if (inputStream.available() < 1) {
                                outputStream.flush();
                            }
                        }
                    } while (read >= 0);
                } finally {
                    if (!outputSocket.isOutputShutdown()) {
                        outputSocket.shutdownOutput();
                    }
                }
            } finally {
                if (!inputSocket.isInputShutdown()) {
                    inputSocket.shutdownInput();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();  // TODO: implement catch
        }
    }

    private String readLine(Socket socket) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int next;
        readerLoop:
        while ((next = socket.getInputStream().read()) != -1) {
            if (previousWasR && next == '\n') {
                previousWasR = false;
                continue;
            }
            previousWasR = false;
            switch (next) {
                case '\r':
                    previousWasR = true;
                    break readerLoop;
                case '\n':
                    break readerLoop;
                default:
                    byteArrayOutputStream.write(next);
                    break;
            }
        }
        return byteArrayOutputStream.toString("ISO-8859-1");
    }
}

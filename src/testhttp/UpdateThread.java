/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testhttp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author seanb
 */
public class UpdateThread implements Runnable{
    
    private DataInputStream is;
    private String line;
    private InetAddress address;
    private String ip;
    private BufferedReader br;
    private  ServerSocket clientServer;
    private Socket socket;
    private String newSite;
    private String removedSite;
    private int index;
    
    public UpdateThread(){}

    
    @Override
    public void run(){
        try {
            address = InetAddress.getLocalHost();
            ip = address.getHostAddress();
        } catch (UnknownHostException ex) {
            Logger.getLogger(UpdateThread.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        while(true){
            try {
                
                clientServer = new ServerSocket(3000);
                socket = clientServer.accept();
                
                is = new DataInputStream(socket.getInputStream());
                br = new BufferedReader(new InputStreamReader(is));
                
                line = br.readLine();
                
                if(line.equals("add site")){
                    newSite = line = br.readLine();
                    
                    ProxyInfo.blockedList.add(newSite);
                    is.close();
                    socket.close();
                    clientServer.close();
                }else{
                    removedSite = line = br.readLine();
                    index = ProxyInfo.blockedList.indexOf(removedSite);
                    ProxyInfo.blockedList.remove(index);
                    is.close();
                    socket.close();
                    clientServer.close();
                }
                
                
            } catch (IOException ex) {
                //Logger.getLogger(UpdateThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package testhttp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author seanb
 */
public class ProxyMain {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        Properties properties = new Properties();
        InputStream is;
        byte[] key = {'#', '9', 'F', '2', 'd', 'Y', 'V', 'H', '5', 'e', ']', '=', 'x', 't', '8', '(', '%', '8', 'w', 'J', '}', '#', '9', 'F', '2', 'd', 'Y', 'V', 'H', '5', 'e', ']', '=', 'x', 't', '8', '(', '%', '8', 'w', 'J'};
        SecretKeySpec sKey;
        MessageDigest sha = null;
        Connection connection;
        ResultSet results;
        ResultSet results2;
        ResultSet results3;
        String databasePW = "";
        String databaseUN = "";
        String PW = "";
        final String DB_URL = "jdbc:mysql://webwarden.ck4ehi6goau1.eu-west-1.rds.amazonaws.com:3306/proxy";
        String query1 = "select * from proxy.blocked_sites";
        String query2 = "select * from proxy.wordlist";
        String query3 = "select user_ip from proxy.users where warden = 'true'";

        try {
            is = new FileInputStream("config.properties");
            properties.load(is);
        } catch (FileNotFoundException ex) {
            System.out.println(ex);
            //Logger.getLogger(ProxyMain.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            System.out.println(ex);
            // Logger.getLogger(ProxyMain.class.getName()).log(Level.SEVERE, null, ex);
        }

        databaseUN = properties.getProperty("username");
        databasePW = properties.getProperty("password");

        try {

            sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            sKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, sKey);
            PW = new String(cipher.doFinal(Base64.getDecoder().decode(databasePW)));
        } catch (NoSuchAlgorithmException ex) {
            System.out.println(ex);
            //LoginLogger.error("NoSuchAlgorithmException: "+ex);
        } catch (NoSuchPaddingException ex) {
            System.out.println(ex);
            //LoginLogger.error("NoSuchPaddingException: "+ex);
        } catch (InvalidKeyException ex) {
            System.out.println(ex);
            //LoginLogger.error("InvalidKeyException: "+ex);
        } catch (IllegalBlockSizeException ex) {
            System.out.println(ex);
            //LoginLogger.error("IllegalBlockSizeException: "+ex);
        } catch (BadPaddingException ex) {
            System.out.println(ex);
            //LoginLogger.error("BadPaddingException: "+ex);
        }

        try {
            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection(DB_URL, databaseUN, new String(PW));
            PreparedStatement pstmt = connection.prepareStatement(query1);
            PreparedStatement pstmt2 = connection.prepareStatement(query2);
            PreparedStatement pstmt3 = connection.prepareStatement(query3);
            
           

            connection.setAutoCommit(false);

            results = pstmt.executeQuery();
            while (results.next()) {
                ProxyInfo.blockedList.add(results.getString("site"));
            }

            results2 = pstmt2.executeQuery();
            while (results2.next()) {
                ProxyInfo.wordist.add(results2.getString("word"));

            }
            
            results3 = pstmt3.executeQuery();
            while (results3.next()) {
                ProxyInfo.wardenIp = results3.getString("user_ip");
            }

            connection.commit();
            connection.close();
        } catch (ClassNotFoundException ex) {
            System.out.println(ex);
            //Logger.getLogger(ProxyMain.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            System.out.println(ex);
            //Logger.getLogger(ProxyMain.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        new Thread(new UpdateThread()).start();
        try {
            final ServerSocket server = new ServerSocket(8080);
            System.out.println(ProxyInfo.wardenIp);
            while (true) {
                new ProxyThread(server.accept(),ProxyInfo.wardenIp,ProxyInfo.wordist,ProxyInfo.blockedList).start();
            }

        } catch (IOException e) {
            System.out.println(e);
        }
    }

}

package com.ocean.proxy.server;


import com.ocean.proxy.server.service.Socks4ProxyServer;
import com.ocean.proxy.server.service.Socks5ProxyServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class ProxyServerApplication {

    public static void main(String[] args) throws Exception {
        String port;
        if (args != null && args.length > 0) {
            port = args[0];
        } else {
            Properties systemProperties = System.getProperties();
            port = systemProperties.getProperty("proxy.port");
            if (port == null) {
                InputStream resourceAsStream = ProxyServerApplication.class.getClassLoader().getResourceAsStream("application.properties");
                Properties properties = new Properties();
                properties.load(resourceAsStream);
                port = properties.getProperty("proxy.port");
            }
        }

        // final int portNumber = 1080; // 指定监听的端口号
        Socks5ProxyServer socks5Server = new Socks5ProxyServer();
        Socks4ProxyServer socks4Server = new Socks4ProxyServer();
        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(port))) {
            System.out.println("Proxy Server is running on port " + port + ". support socks4 and socks5");

            while (true) {
                // 等待客户端连接
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket.getInetAddress());

                // 开启一个线程处理客户端连接
                Executors.newSingleThreadScheduledExecutor().execute(() -> {
                    try {
                        InputStream clientInput = clientSocket.getInputStream();
                        int version = clientInput.read(); //版本， socks5的值是0x05, socks4的值是0x04
                        System.out.println("socks version:" + version);
                        if (version == 5) {
                            socks5Server.handleClient(clientSocket);
                        } else if (version == 4) {
                            socks4Server.handleClient(clientSocket);
                        } else {
                            System.out.println("error socks version!");
                            clientSocket.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

package com.ocean.proxy.server;


import com.ocean.proxy.server.service.AuthService;
import com.ocean.proxy.server.service.ForwardService;
import com.ocean.proxy.server.service.Socks4ProxyServer;
import com.ocean.proxy.server.service.Socks5ProxyServer;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.*;

public class ProxyServerApplication {

    // 全局线程池，最大2000线程，队列100，超时20秒
    public static final ThreadPoolExecutor GLOBAL_EXECUTOR = new ThreadPoolExecutor(
            300, 2000, 20L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.AbortPolicy()
    );

    public static void main(String[] args) throws Exception {
        String port;
        boolean authEnable = false;
        if (args != null && args.length > 0) {
            port = args[0];
        } else {
            Properties properties = System.getProperties();
            port = properties.getProperty("proxy.port");

            if (port == null) {
                InputStream resourceAsStream = ProxyServerApplication.class.getClassLoader().getResourceAsStream("application.properties");
                properties = new Properties();
                properties.load(resourceAsStream);
                port = properties.getProperty("proxy.port");
                String authConfig = properties.getProperty("auth.enable");
                if (authConfig != null && authConfig.equals("true")) {
                    AuthService.init();
                    authEnable = true;
                }
                ForwardService.startForwardServer(properties);
            }
        }

        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(port))) {
            System.out.println("Proxy Server is running on port " + port + ". support socks4 and socks5");
            while (true) {
                // 等待客户端连接
                Socket clientSocket = serverSocket.accept();
                String clientInfo = clientSocket.getInetAddress() + ":" + clientSocket.getPort();
                System.out.println("==================" + clientInfo + "==================");
                System.out.println("Accepted connection from " + clientInfo);
                final boolean auth = authEnable;
                // 开启一个线程处理客户端连接
                GLOBAL_EXECUTOR.execute(() -> {
                    try {
                        InputStream clientInput = clientSocket.getInputStream();
                        int version = clientInput.read(); //版本， socks5的值是0x05, socks4的值是0x04
                        System.out.println("socks version:" + version);
                        if (version == 5) {
                            Socks5ProxyServer.handleClient(clientSocket, auth);
                        } else if (version == 4) {
                            Socks4ProxyServer.handleClient(clientSocket);
                        } else {
                            System.out.println("error protocol version!");
                            clientSocket.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("==================" + clientInfo + "==================");
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

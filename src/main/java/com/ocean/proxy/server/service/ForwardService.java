package com.ocean.proxy.server.service;

import org.apache.commons.lang3.StringUtils;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * Description:
 *
 * @Author: Ocean
 * @DateTime: 2024/3/21 17:09
 */
public class ForwardService {

    public static void startForwardServer(Properties properties){
        String targetAddress = properties.getProperty("forward.address");
        String portList = properties.getProperty("forward.portList");
        if (StringUtils.isAnyEmpty(targetAddress, portList)) {
            return;
        }
        String[] portArray = portList.split(",");
        for (String portStr : portArray) {
            int port = Integer.parseInt(portStr);
            Executors.newSingleThreadExecutor().execute(()->{
                try (ServerSocket serverSocket = new ServerSocket(port)) {
                    System.out.println("Forward Server is running on port " + port + ".");
                    while (true) {
                        // 等待客户端连接
                        Socket clientSocket = serverSocket.accept();
                        Socket targetSocket = new Socket(targetAddress, port);
                        DataTransHandler.bindClientAndTarget(clientSocket,targetSocket);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
}

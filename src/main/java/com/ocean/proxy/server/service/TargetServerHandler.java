package com.ocean.proxy.server.service;

import com.ocean.proxy.server.ProxyServerApplication;
import com.ocean.proxy.server.util.BytesUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executors;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/8 18:06
 */
public class TargetServerHandler {

    public static void createTargetServerThread(Socket clientSocket, Socket targetSocket) throws IOException {
        InputStream input = targetSocket.getInputStream();
        Executors.newSingleThreadScheduledExecutor().execute(() -> {
            try {
                // 从客户端读取数据并发送到目标服务器
                int defaultLen = 1024;
                byte[] buffer = new byte[defaultLen];
                int bytesRead;
                while (!targetSocket.isClosed() && (bytesRead = input.read(buffer)) != -1) {
                    // 处理从客户端读取的数据，可以根据需要进行相应的操作
                    processTargetData(buffer, bytesRead, clientSocket);
                    buffer = new byte[defaultLen];
                }
            } catch (SocketException e) {
                if ("Connection reset".equals(e.getMessage())) {
                    // 处理Connection Reset状态
                    System.out.println("Connection reset by peer");
                } else {
                    // 处理其他SocketException
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void processTargetData(byte[] data, int length, Socket clientSocket) throws Exception {
        byte[] validData = BytesUtil.splitBytes(data, 0, length);
        // System.out.println("---------------------------- target response data: " + new String(validData));
        // 处理从客户端读取的数据的逻辑
        OutputStream targetOutput = clientSocket.getOutputStream();
        targetOutput.write(validData);
        // 这里可以根据需要进行相应的操作
        // 例如，将数据发送给目标服务器，进行加工处理，等等
    }
}

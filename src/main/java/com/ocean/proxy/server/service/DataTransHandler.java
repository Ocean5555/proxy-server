package com.ocean.proxy.server.service;

import com.ocean.proxy.server.util.BytesUtil;
import com.ocean.proxy.server.ProxyServerApplication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * <b>Description:</b>  <br/>
 * <b>@Author:</b> Ocean <br/>
 * <b>@DateTime:</b> 2023/12/8 17:40
 */
public class DataTransHandler {

    // 使用全局线程池
    private static final ThreadPoolExecutor executorService = ProxyServerApplication.GLOBAL_EXECUTOR;

    /**
     * 绑定客户端与目标的数据传输
     * 启动两个线程，分别从客户端读取数据并发送到目标服务器，以及从目标服务器读取数据并发送到客户端
     *
     * @param clientSocket
     * @param targetSocket
     * @throws Exception
     */
    public static void bindClientAndTarget(Socket clientSocket, Socket targetSocket) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        System.out.println("create connection ：" + sessionId);
        createClientThread(clientSocket, targetSocket, sessionId);
        createTargetThread(clientSocket, targetSocket, sessionId);
        checkConnectStatus(clientSocket, targetSocket, sessionId);
    }

    /**
     * 安全关闭两个Socket
     */
    private static void closeBoth(Socket s1, Socket s2) {
        try { if (s1 != null && !s1.isClosed()) s1.close(); } catch (IOException ignored) {}
        try { if (s2 != null && !s2.isClosed()) s2.close(); } catch (IOException ignored) {}
    }

    public static void createClientThread(Socket clientSocket, Socket targetSocket, String sessionId) throws IOException {
        executorService.execute(() -> {
            try {
                InputStream clientInput = clientSocket.getInputStream();
                // 从客户端读取数据并发送到目标服务器
                int defaultLen = 1024;
                byte[] buffer = new byte[defaultLen];
                int bytesRead;
                while (!clientSocket.isClosed() && (bytesRead = clientInput.read(buffer)) != -1) {
                    // 处理从客户端读取的数据，可以根据需要进行相应的操作
                    processClientData(buffer, bytesRead, targetSocket);
                }
            } catch (SocketException e) {
                if ("Connection reset".equals(e.getMessage())) {
                    // 处理Connection Reset状态
                    System.out.println("Connection reset by peer:" + sessionId);
                } else if ("Socket closed".equals(e.getMessage())) {
                    System.out.println("Socket closed:" + sessionId);
                } else {
                    // 处理其他SocketException
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                closeBoth(clientSocket, targetSocket);
            }
        });
    }

    private static void processClientData(byte[] data, int length, Socket targetSocket) throws Exception {
        byte[] validData = BytesUtil.splitBytes(data, 0, length);
        // System.out.println("=============================== client data: " + new String(validData));
        // 处理从客户端读取的数据的逻辑
        OutputStream targetOutput = targetSocket.getOutputStream();
        targetOutput.write(validData);
        // 这里可以根据需要进行相应的操作
        // 例如，将数据发送给目标服务器，进行加工处理，等等
    }

    public static void createTargetThread(Socket clientSocket, Socket targetSocket, String sessionId) throws IOException {
        executorService.execute(() -> {
            try {
                InputStream targetInput = targetSocket.getInputStream();
                // 从客户端读取数据并发送到目标服务器
                int defaultLen = 1024;
                byte[] buffer = new byte[defaultLen];
                int bytesRead;
                while (!targetSocket.isClosed() && (bytesRead = targetInput.read(buffer)) != -1) {
                    //处理从目标端读取的数据，可以根据需要进行相应的操作
                    processTargetData(buffer, bytesRead, clientSocket);
                }
            } catch (SocketException e) {
                if ("Connection reset".equals(e.getMessage())) {
                    // 处理Connection Reset状态
                    System.out.println("Connection reset by peer:" + sessionId);
                } else if ("Socket closed".equals(e.getMessage())) {
                    System.out.println("Socket closed:" + sessionId);
                } else {
                    // 处理其他SocketException
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                closeBoth(clientSocket, targetSocket);
            }
        });
    }

    private static void processTargetData(byte[] data, int length, Socket clientSocket) throws Exception {
        if (clientSocket.isClosed()) {
            return;
        }
        byte[] validData = BytesUtil.splitBytes(data, 0, length);
        // System.out.println("---------------------------- target response data: " + new String(validData));
        // 处理从目标端读取的数据的逻辑
        OutputStream clientOutput = clientSocket.getOutputStream();
        clientOutput.write(validData);
        // 这里可以根据需要进行相应的操作
        // 例如，将数据发送给目标服务器，进行加工处理，等等
    }

    private static void checkConnectStatus(Socket clientSocket, Socket targetSocket, String sessionId) {
        executorService.execute(() -> {
            while (true) {
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
                try {
                    clientSocket.sendUrgentData(0xFF);
                } catch (IOException e) {
                    System.out.println("-------------------------------- client close :" + sessionId);
                    closeBoth(clientSocket, targetSocket);
                    return;
                }
                try {
                    targetSocket.sendUrgentData(0xFF);
                } catch (IOException e) {
                    System.out.println("================================= target close :" + sessionId);
                    closeBoth(clientSocket, targetSocket);
                    return;
                }
            }
        });
    }

}

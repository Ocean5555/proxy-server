package com.ocean.proxy.server.service;

import com.ocean.proxy.server.ProxyServerApplication;
import com.ocean.proxy.server.util.BytesUtil;
import com.ocean.proxy.server.util.IpUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * socket4 协议的服务实现
 */
public class Socks4ProxyServer {

    public void handleClient(Socket clientSocket) {
        try {
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream clientOutput = clientSocket.getOutputStream();

            //| VN | CD | DSTPORT | DSTIP |
            // 实现 SOCKS4 握手协商和建立连接的逻辑
            int cd = clientInput.read();    //长度1字节，操作符号，在本阶段值为1.
            System.out.println("cd:" + cd);
            if (cd != 1) {
                clientInput.close();
                clientOutput.close();
                System.out.println("cd error!");
                return;
            }
            byte[] dstPort = new byte[2];   //长度2字节，访问目标端口。
            clientInput.read(dstPort);
            int targetPort = (int) BytesUtil.toNumberH(dstPort);
            System.out.println("port:" + targetPort);
            byte[] dstIp = new byte[4];   //长度4字节，访问目标IP
            clientInput.read(dstIp);
            String targetAddress = IpUtil.bytesToIpAddress(dstIp);
            ;
            System.out.println("ip:" + targetAddress);
            byte[] data = new byte[1024];
            int len = clientInput.read(data);
            System.out.println("====================================");
            Socket targetSocket;
            try {
                if (targetAddress.equals("0.0.0.1")) {
                    byte[] bytes = BytesUtil.splitBytes(data, 1, len - 1);
                    String domainName = new String(bytes, StandardCharsets.UTF_8);
                    System.out.println("domainName: " + domainName);
                    targetSocket = new Socket(domainName, targetPort);
                } else {
                    targetSocket = new Socket(targetAddress, targetPort);
                }
                clientOutput.write(new byte[]{(byte) 0x00, (byte) 0x5A, dstPort[1], dstPort[0], dstIp[3], dstIp[2], dstIp[1], dstIp[0]});
            } catch (Exception e) {
                e.printStackTrace();
                clientOutput.write(new byte[]{(byte) 0x00, (byte) 0x5B});
                throw new RuntimeException(e);
            }
            // 返回的响应信息  | VN | CD | DSTPORT | DSTIP |
            // VN：长度1字节，响应操作符，固定为0。
            // CD：长度1字节，响应码.
            // 90: request granted
            // 91: request rejected or failed
            // 92: request rejected because SOCKS server cannot connect to identity on the client
            // 93: request rejected because the client program and identity report different user-ids
            // 然后，启动两个线程，分别从客户端读取数据并发送到目标服务器，以及从目标服务器读取数据并发送到客户端

            ClientHandler.createClientThread(clientSocket, targetSocket);
            TargetServerHandler.createTargetServerThread(clientSocket, targetSocket);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                clientSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

}

package com.ocean.proxy.server.service;

import com.ocean.proxy.server.ProxyServerApplication;
import com.ocean.proxy.server.util.BytesUtil;
import com.ocean.proxy.server.util.IpUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * socket5 协议的服务实现
 */
public class Socks5ProxyServer {

    public void handleClient(Socket clientSocket) {
        try {
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream clientOutput = clientSocket.getOutputStream();
            // 实现 SOCKS 握手协商和建立连接的逻辑
            if (!handleHandshake(clientInput, clientOutput)) {
                System.out.println("auth fail!");
                return;
            }
            // 这部分逻辑需要根据 SOCKS5 协议规范实现
            Socket targetSocket = handleConnectionRequest(clientSocket, clientInput, clientOutput);

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

    /**
     * 握手请求
     *
     * @param input
     * @param output
     * @return
     * @throws IOException
     */
    private boolean handleHandshake(InputStream input, OutputStream output) throws IOException {
        int methodsCount = input.read();    //指示其后的 METHOD 字段所占的字节数
        byte[] methods = new byte[methodsCount];   //methods表示客户端使用的认知方式，0x00表示不认证，0x03表示用户名密码认证
        input.read(methods);
        String s = BytesUtil.toHexString(methods);
        System.out.println("client auth type: 0x" + s);
        // 这里假设支持无需认证的方法，即0x00
        output.write(new byte[]{(byte) 0x05, (byte) 0x00});
        return true;
    }

    /**
     * 建立连接，客户端->代理服务器，代理服务器->目标服务
     * 客户端向代理服务器发起正式请求以指示所要访问的目标进程的地址, 端口
     *
     * @param clientSocket
     * @param input
     * @param output
     * @throws IOException
     */
    private Socket handleConnectionRequest(Socket clientSocket, InputStream input, OutputStream output) throws IOException {
        int version = input.read(); //版本，socks5的值是0x05
        int cmd = input.read(); //共有 3 个取值, 分别为 0x01 (CONNECT), 0x02 (BIND), 0x03 (UDP ASSOCIATE)
        int rsv = input.read(); // 固定为 0x00
        int addressType = input.read();
        Socket targetSocket;
        // 目标地址类型，IPv4地址为0x01，IPv6地址为0x04，域名地址为0x03
        if (addressType == 0x01) {
            byte[] ipv4 = new byte[4];
            input.read(ipv4);
            String targetAddress = IpUtil.bytesToIpAddress(ipv4);
            int targetPort = input.read() << 8 | input.read();
            try {
                targetSocket = new Socket(targetAddress, targetPort);
                if (cmd == 0x01) {
                    sendConnectionResponse(output, (byte) 0x00, ipv4, targetPort);
                } else if (cmd == 0x03) {
                    handleUdpAssociateRequest(output);
                } else {
                    System.out.println("not support cmd!");
                }
            } catch (IOException e) {
                sendConnectionResponse(output, (byte) 0x01, ipv4, targetPort);
                throw new RuntimeException("连接目标服务失败。", e);
            }
        } else if (addressType == 0x03) {
            // 域名地址
            int domainLength = input.read();
            byte[] domainBytes = new byte[domainLength];
            input.read(domainBytes);
            String targetDomain = new String(domainBytes);
            int targetPort = input.read() << 8 | input.read();
            // 在实际应用中，可以根据 targetDomain 和 targetPort 与目标服务器建立连接
            try {
                targetSocket = new Socket(targetDomain, targetPort);
                if (cmd == 0x01) {
                    // 发送连接成功的响应
                    sendConnectionResponse(output, (byte) 0x00, targetDomain, targetPort);
                } else if (cmd == 0x03) {
                    handleUdpAssociateRequest(output);
                } else {
                    System.out.println("not support cmd!");
                }
            } catch (IOException e) {
                sendConnectionResponse(output, (byte) 0x01, targetDomain, targetPort);
                throw new RuntimeException("连接目标服务失败。", e);
            }
        } else {
            // 不支持的地址类型
            throw new RuntimeException("not support address type!");
        }
        return targetSocket;
    }

    private void handleUdpAssociateRequest(OutputStream output) throws IOException {
        // 假设监听 UDP 请求的端口为 5000
        int udpPort = 5000;
        output.write(new byte[]{(byte) 0x05, (byte) 0x00, (byte) 0x00, (byte) 0x03, (byte) 127, (byte) 0, (byte) 0, (byte) 1, (byte) (udpPort >> 8), (byte) udpPort});
    }

    /**
     * VER 字段占 1 字节, 表征协议版本, 固定为 0x05
     * REP 字段占 1 字节, 可以理解为状态码, 它的值表征了此次连接的状态:
     * 0x00 连接成功
     * 0x01 代理服务器出错
     * 0x02 连接不允许
     * 0x03 网络不可达
     * 0x04 主机不可达
     * 0x05 连接被拒绝
     * 0x06 TTL 到期
     * 0x07 命令 (CMD) 不支持
     * 0x08 地址类型不支持
     * 0x09 ~ 0xFF 目前没有分配
     * RSV 字段占 1 字节, 为保留字段, 固定为 0x00
     * ATYP 字段与请求的 ATYP 字段含义相同
     * BND.ADDR 与 BND.PORT 的含义随请求中的 CMD 的不同而不同, 下面我们依次展开讨论 3 种 CMD: CONNECT, BIND 以及 UDP ASSOCIATE
     *
     * @param status
     * @param ipv4
     * @param targetPort
     * @return
     */
    private void sendConnectionResponse(OutputStream output, byte status, byte[] ipv4, int targetPort) throws IOException {
        // 发送连接响应
        output.write(new byte[]{(byte) 0x05, status, (byte) 0x00, (byte) 0x01, ipv4[0], ipv4[1], ipv4[2], ipv4[3], (byte) (targetPort >> 8), (byte) targetPort});
    }

    private void sendConnectionResponse(OutputStream output, byte status, String targetDomain, int targetPort) throws IOException {
        // 发送连接响应
        byte[] domainBytes = targetDomain.getBytes();
        int domainLength = domainBytes.length;
        output.write(new byte[]{(byte) 0x05, status, (byte) 0x00, (byte) 0x03, (byte) domainLength});
        output.write(domainBytes);
        output.write(new byte[]{(byte) (targetPort >> 8), (byte) targetPort});
    }
}

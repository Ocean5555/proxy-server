package com.ocean.proxy.server.service;

import com.ocean.proxy.server.util.BytesUtil;
import com.ocean.proxy.server.util.IpUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * socket5 协议的服务实现
 */
public class Socks5ProxyServer {

    public static void handleClient(Socket clientSocket, boolean auth) {
        try {
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream clientOutput = clientSocket.getOutputStream();
            // 实现 SOCKS 握手协商和建立连接的逻辑
            int methodsCount = clientInput.read();    //指示其后的 METHOD 字段所占的字节数
            byte[] methods = new byte[methodsCount];   //methods表示客户端使用的认知方式，0x00表示不认证，0x00：无认证。 0x01：GSSAPI认证（较少使用）。0x02：用户名/密码认证。
            clientInput.read(methods);
            String s = BytesUtil.toHexString(methods);
            System.out.println("client auth type: 0x" + s);
            if(!auth){
                // 无需认证的方法，即0x00
                clientOutput.write(new byte[]{(byte) 0x05, (byte) 0x00});
            }else{
                //用户名密码认证：0x02
                clientOutput.write(new byte[]{(byte) 0x05, (byte) 0x02});
                int VER = clientInput.read();
                int ULEN = clientInput.read();
                byte[] usernameBytes = new byte[ULEN];
                clientInput.read(usernameBytes);
                int PLEN = clientInput.read();
                byte[] passwdBytes = new byte[PLEN];
                clientInput.read(passwdBytes);
                String username = new String(usernameBytes);
                String passwd = new String(passwdBytes);
                System.out.println("收到用户名和密码：" + username + " " + passwd);
                if (!AuthService.checkAuth(username, passwd)) {
                    System.out.println("认证失败");
                    clientOutput.write(new byte[]{(byte) 0x05, (byte) 0x01});
                    return;
                }else{
                    System.out.println("认证成功");
                    clientOutput.write(new byte[]{(byte) 0x05, (byte) 0x00});
                }
            }

            createDataInteraction(clientSocket);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                clientSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    private static void createDataInteraction(Socket clientSocket)throws Exception{
        // 这部分逻辑需要根据 SOCKS5 协议规范实现
        Socket targetSocket = handleConnectionRequest(clientSocket);
        DataTransHandler.bindClientAndTarget(clientSocket, targetSocket);
    }

    /**
     * 建立连接，客户端->代理服务器，代理服务器->目标服务
     * 客户端向代理服务器发起正式请求以指示所要访问的目标进程的地址, 端口
     *
     * @param clientSocket
     * @throws IOException
     */
    private static Socket handleConnectionRequest(Socket clientSocket) throws IOException {
        InputStream clientInput = clientSocket.getInputStream();
        OutputStream clientOutput = clientSocket.getOutputStream();
        int version = clientInput.read(); //版本，socks5的值是0x05
        int cmd = clientInput.read(); //共有 3 个取值, 分别为 0x01 (CONNECT), 0x02 (BIND), 0x03 (UDP ASSOCIATE)
        int rsv = clientInput.read(); // 固定为 0x00
        int addressType = clientInput.read();
        Socket targetSocket;
        // 目标地址类型，IPv4地址为0x01，IPv6地址为0x04，域名地址为0x03
        if (addressType == 0x01) {
            byte[] ipv4 = new byte[4];
            clientInput.read(ipv4);
            String targetAddress = IpUtil.bytesToIpAddress(ipv4);
            int targetPort = clientInput.read() << 8 | clientInput.read();
            System.out.println("target:" + targetAddress + ":" + targetPort);
            try {
                targetSocket = new Socket(targetAddress, targetPort);
                if (cmd == 0x01) {
                    sendConnectionResponse(clientOutput, (byte) 0x00, ipv4, targetPort);
                } else if (cmd == 0x03) {
                    handleUdpAssociateRequest(clientOutput);
                } else {
                    System.out.println("not support cmd!");
                }
            } catch (IOException e) {
                sendConnectionResponse(clientOutput, (byte) 0x01, ipv4, targetPort);
                throw new RuntimeException("连接目标服务失败。", e);
            }
        } else if (addressType == 0x03) {
            // 域名地址
            int domainLength = clientInput.read();
            byte[] domainBytes = new byte[domainLength];
            clientInput.read(domainBytes);
            String targetDomain = new String(domainBytes);
            int targetPort = clientInput.read() << 8 | clientInput.read();
            System.out.println("target:" + targetDomain + ":" + targetPort);
            try {
                targetSocket = new Socket(targetDomain, targetPort);
                if (cmd == 0x01) {
                    sendConnectionResponse(clientOutput, (byte) 0x00, targetDomain, targetPort);
                } else if (cmd == 0x03) {
                    handleUdpAssociateRequest(clientOutput);
                } else {
                    System.out.println("not support cmd!");
                }
            } catch (IOException e) {
                sendConnectionResponse(clientOutput, (byte) 0x01, targetDomain, targetPort);
                throw new RuntimeException("连接目标服务失败。", e);
            }
        } else if (addressType == 0x04) {
            // IPv6地址
            byte[] ipv6 = new byte[16];
            clientInput.read(ipv6);
            String targetAddress = ipv6BytesToString(ipv6);
            int targetPort = clientInput.read() << 8 | clientInput.read();
            System.out.println("target(IPv6):" + targetAddress + ":" + targetPort);
            try {
                targetSocket = new Socket(targetAddress, targetPort);
                if (cmd == 0x01) {
                    sendConnectionResponseIPv6(clientOutput, (byte) 0x00, ipv6, targetPort);
                } else if (cmd == 0x03) {
                    handleUdpAssociateRequest(clientOutput);
                } else {
                    System.out.println("not support cmd!");
                }
            } catch (IOException e) {
                sendConnectionResponseIPv6(clientOutput, (byte) 0x01, ipv6, targetPort);
                throw new RuntimeException("连接目标服务失败。", e);
            }
        } else {
            // 不支持的地址类型
            throw new RuntimeException("not support address type!");
        }
        return targetSocket;
    }

    // 新增：IPv6字节数组转字符串
    private static String ipv6BytesToString(byte[] bytes) {
        if (bytes.length != 16) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i += 2) {
            sb.append(String.format("%02x%02x", bytes[i], bytes[i + 1]));
            if (i < 14) sb.append(":");
        }
        return sb.toString().replaceAll("(:0{1,3}){2,}", "::"); // 简单压缩
    }

    // 新增：发送IPv6响应
    private static void sendConnectionResponseIPv6(OutputStream output, byte status, byte[] ipv6, int targetPort) throws IOException {
        // 发送连接响应
        output.write(new byte[]{(byte) 0x05, status, (byte) 0x00, (byte) 0x04});
        output.write(ipv6);
        output.write(new byte[]{(byte) (targetPort >> 8), (byte) targetPort});
    }

    private static void handleUdpAssociateRequest(OutputStream output) throws IOException {
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
    private static void sendConnectionResponse(OutputStream output, byte status, byte[] ipv4, int targetPort) throws IOException {
        // 发送连接响应
        output.write(new byte[]{(byte) 0x05, status, (byte) 0x00, (byte) 0x01, ipv4[0], ipv4[1], ipv4[2], ipv4[3], (byte) (targetPort >> 8), (byte) targetPort});
    }

    private static void sendConnectionResponse(OutputStream output, byte status, String targetDomain, int targetPort) throws IOException {
        // 发送连接响应
        byte[] domainBytes = targetDomain.getBytes();
        int domainLength = domainBytes.length;
        output.write(new byte[]{(byte) 0x05, status, (byte) 0x00, (byte) 0x03, (byte) domainLength});
        output.write(domainBytes);
        output.write(new byte[]{(byte) (targetPort >> 8), (byte) targetPort});
    }
}

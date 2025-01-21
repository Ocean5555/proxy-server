package com.ocean.proxy.server.service;

import com.ocean.proxy.server.ProxyServerApplication;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Description:
 *
 * @author Ocean
 * datetime: 2025/1/21 17:07
 */
public class AuthService {

    private static final Map<String, String> userMap = new HashMap<>();

    public static void init(){
        InputStream resourceAsStream = AuthService.class.getClassLoader().getResourceAsStream("user.txt");
        if (resourceAsStream != null) {
            Properties properties = new Properties();
            try {
                properties.load(resourceAsStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                userMap.put(entry.getKey().toString(), entry.getValue().toString());
            }
        }
    }

    public static boolean checkAuth(String username, String passwd) {
        if (userMap.containsKey(username)) {
            if (userMap.get(username).equals(passwd)) {
                return true;
            }
        }
        return false;
    }


}

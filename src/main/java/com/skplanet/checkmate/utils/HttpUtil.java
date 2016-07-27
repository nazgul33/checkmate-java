package com.skplanet.checkmate.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class HttpUtil {
    
	private static final Logger LOG = LoggerFactory.getLogger(HttpUtil.class);
	
    private static int CONNECTION_TIMEOUT = 5000;
    private static int READ_TIMEOUT = 3000;
    public final String userAgent;
    
    public HttpUtil() {
        this("CheckMate 1.0");
    }

    public HttpUtil(String userAgent) {
        this.userAgent = userAgent;
    }

    // HTTP GET request
    // returns response code
    // if response code is 200, parameter "response" should have the result
    public int get(String url, StringBuilder response) throws Exception {
        return get(url, response, null);
    }

    public int getJson(String url, StringBuilder response) throws Exception {
        return get(url, response, "application/json");
    }

    public int get(String url, StringBuilder response, String accept) throws Exception {
        URL obj = new URL(url);
        HttpURLConnection con = null;
        try {
        	con = (HttpURLConnection) obj.openConnection();
            con.setConnectTimeout(CONNECTION_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);
            // optional default is GET
            con.setRequestMethod("GET");

            //add request header
            con.setRequestProperty("User-Agent", userAgent);

            if (accept != null) {
                con.setRequestProperty("Accept", accept);
            }

            int responseCode = con.getResponseCode();
            LOG.debug("sending 'GET' request to URL : {}", url);
            LOG.debug("Response Code : {}", responseCode);

            if (responseCode == 200) {
                BufferedReader in = null;
                try {
                	in = new BufferedReader(
                            new InputStreamReader(con.getInputStream()));
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine).append("\n");
                    }
                } finally {
                	if (in != null) {
                		in.close();
                	}
                }
            }
            return responseCode;
        } finally {
        	if (con != null) {
        		con.disconnect();
        	}
        }
    }
    
    public static String get(String url) throws Exception {
    	HttpUtil http = new HttpUtil();
    	StringBuilder sb = new StringBuilder();
    	int code = http.get(url, sb);
    	if (code == 200) {
    		return sb.toString();
    	} else {
    		throw new RuntimeException("response code "+ code);
    	}
    }
}

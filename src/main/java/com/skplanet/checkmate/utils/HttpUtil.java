package com.skplanet.checkmate.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by nazgul33 on 15. 1. 27.
 */
public class HttpUtil {
    private static boolean DEBUG = false;
    private static int CONNECTION_TIMEOUT = 5000;
    private static int READ_TIMEOUT = 3000;
    public final String userAgent;
    public HttpUtil(String userAgent) {
        this.userAgent = userAgent;
    }
    public HttpUtil() {
        this.userAgent = "CheckMate 1.0";
    }

    // HTTP GET request
    // returns response code
    // if response code is 200, parameter "response" should have the result
    public int get(String url, StringBuffer response) throws Exception {
        return get(url, response, null);
    }

    public int getJson(String url, StringBuffer response) throws Exception {
        return get(url, response, "application/json");
    }

    public int get(String url, StringBuffer response, String accept) throws Exception {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
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
        if (DEBUG) {
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);
        }

        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
        }
        return responseCode;
    }
}

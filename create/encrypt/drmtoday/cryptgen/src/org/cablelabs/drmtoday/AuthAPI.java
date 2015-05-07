/* Copyright (c) 2015, CableLabs, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.cablelabs.drmtoday;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * Helper class to generate REST commands for DRMToday CAS (Central Authentication Service)
 */
public class AuthAPI {
    
    
    private static final String AUTH_API_LOGIN = "/cas/v1/tickets";

    private String ticketURL;
    
    private Header contentTypeHdr = new BasicHeader("Content-Type","application/x-www-form-urlencoded");
    private Header acceptHdr = new BasicHeader("Accept", "*/*");
    private Header hostHdr;
    
    private String username;
    private String password;
    private String authHost;
    
    public AuthAPI(String username, String password, String authHost) {
        this.username = username;
        this.password = password;
        this.authHost = authHost;
        hostHdr = new BasicHeader("Host", authHost);
    }
    
    /**
     * Login to the DRMToday CAS.  This establishes the ticket URL to be used for future ticket
     * access
     * @throws Exception if any error occurs
     */
    public void login() throws Exception {
        
        CloseableHttpClient client = HttpClients.createDefault();
        
        HttpPost post = new HttpPost("https://" + authHost + AUTH_API_LOGIN);
        post.addHeader(contentTypeHdr);
        post.addHeader(hostHdr);
        post.addHeader(acceptHdr);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("username", username));
        params.add(new BasicNameValuePair("password", password));
        post.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));
        
        System.out.println("DRMToday Login (username = " + username + ")...");
            
        CloseableHttpResponse resp = null;
        try {
            resp = client.execute(post);
            if (resp.getStatusLine().getStatusCode() == 201) {
                ticketURL = resp.getFirstHeader("location").getValue();
                System.out.println("Login success! Ticket Location = " + ticketURL);
            } else {
                throw new Exception("Login API received status code " + resp.getStatusLine().getStatusCode());
            }
            
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
        
    }
    
    /**
     * Returns a ticket for the given service
     * 
     * @param serviceURL the URL of the service (REST API) 
     * @return a base64-encoded ticket value to be used in the REST API call
     * @throws Exception 
     */
    public String getTicket(URL serviceURL) throws Exception {
        if (ticketURL == null)
            throw new IllegalAccessException("Must call DRMToday Login API before requesting ticket!");
        
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost(ticketURL);
        post.addHeader(contentTypeHdr);
        post.addHeader(hostHdr);
        post.addHeader(acceptHdr);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("service", serviceURL.toString()));
        post.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));
            
        System.out.println("Retrieving DRMToday ticket for " + serviceURL + "...");
        
        CloseableHttpResponse resp = null;
        try {
            resp = client.execute(post);
            if (resp.getStatusLine().getStatusCode() == 200) {
                String ticket = EntityUtils.toString(resp.getEntity());
                System.out.println("Success!  Ticket = " + ticket);
                return ticket;
            }
                
            throw new Exception("Ticket retrieval (" + serviceURL + ") received status code " + resp.getStatusLine().getStatusCode());
            
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }
}
    

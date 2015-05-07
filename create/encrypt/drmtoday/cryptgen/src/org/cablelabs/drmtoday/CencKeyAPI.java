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

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class CencKeyAPI {
    
    private static final String FE_API_CENC_KEY_INGEST = "/frontend/rest/keys/v1/cenc/merchant/%s/key/";

    private AuthAPI auth;
    private String feHost;
    private String merchant;
    
    private Header contentTypeHdr = new BasicHeader("Content-Type","application/json");
    private Header acceptHdr = new BasicHeader("Accept", "application/json");
    private Header hostHdr;
    
    /**
     * 
     * @param auth
     * @param feHost
     */
    public CencKeyAPI(AuthAPI auth, String feHost, String merchant) {
        
        this.auth = auth;
        this.merchant = merchant;
        this.feHost = feHost;
        hostHdr = new BasicHeader("Host", feHost);
    }
    
    public String ingestKey(CencKey key) throws Exception {
        
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        Gson prettyGson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        
        // Build the API URL
        URIBuilder builder = new URIBuilder();
        builder.setScheme("https").setHost(feHost).setPath(String.format(FE_API_CENC_KEY_INGEST, merchant));
        
        // Get CAS ticket for this API and add to URL as query param
        String keyIngestTicket = auth.getTicket(builder.build().toURL());
        builder.setParameter("ticket", keyIngestTicket);
        
        // Create HTTP POST
        CloseableHttpClient client = HttpClients.createDefault();
        String urlStr = builder.build().toString();
        HttpPost post = new HttpPost(urlStr);
        post.addHeader(contentTypeHdr);
        post.addHeader(hostHdr);
        post.addHeader(acceptHdr);
        post.setEntity(new StringEntity(gson.toJson(key)));
        
        System.out.println("Sending key ingest message:");
        System.out.println(prettyGson.toJson(key));
            
        // Execute
        CloseableHttpResponse resp = null;
        try {
            resp = client.execute(post);
            int code = resp.getStatusLine().getStatusCode();
            if (code == 200) {
                System.out.println("DRMToday CencKey (" + key.keyId + ") ingested successfully!");
                String respData = EntityUtils.toString(resp.getEntity());
                System.out.println("Response = " + respData);
                return respData;
            } 
            
            String message;
            if (code == 401)
                message = "Invalid CAS Ticket";
            else if (code == 409)
                message = "Key already exists";
            else if (code == 412)
                message = "Provided input is invalid";
            else
                message = "Unknown error";
                
            throw new Exception("Key ingest (" + urlStr + ") received status code " + code + " (" + message + ") for keyID " + key.keyId);
            
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }
}

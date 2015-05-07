// COPYRIGHT_BEGIN
// COPYRIGHT_END

package org.cablelabs.drmtoday;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * Helper class to generate REST commands for DRMToday CAS (Central Authentication Service)
 */
public class Auth {
    
    private static final String AUTH_PROPS_USERNAME = "username";
    private static final String AUTH_PROPS_PASSWORD = "password";
    private static final String AUTH_PROPS_HOST     = "authHost";
    
    private static final String AUTH_API_LOGIN = "/cas/v1/tickets";

    private String username;
    private String password;
    private String authHost;
    
    private String ticketURL;
    
    private Header contentTypeHdr = new BasicHeader("Content-Type","application/x-www-form-urlencoded");
    private Header hostHdr = new BasicHeader("Host", authHost);
    private Header acceptHdr = new BasicHeader("Accept", "*/*");
    
    public Auth(String authPropsFile) throws FileNotFoundException, IOException {
        String prop;
        Properties loginprops = new Properties();
        loginprops.load(new FileInputStream(authPropsFile));
            
        if ((prop = loginprops.getProperty(AUTH_PROPS_USERNAME)) == null) 
            throw new IllegalArgumentException("'" + AUTH_PROPS_USERNAME + "' property not found in login props file");
        username = prop;
        if ((prop = loginprops.getProperty(AUTH_PROPS_PASSWORD)) == null)
            throw new IllegalArgumentException("'" + AUTH_PROPS_PASSWORD + "' property not found in login props file");
        password = prop;
        if ((prop = loginprops.getProperty(AUTH_PROPS_HOST)) == null)
            throw new IllegalArgumentException("'" + AUTH_PROPS_HOST + "' property not found in login props file");
        authHost = prop;
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
            
        CloseableHttpResponse resp = null;
        try {
            resp = client.execute(post);
            if (resp.getStatusLine().getStatusCode() == 201) {
                ticketURL = resp.getFirstHeader("location").getValue();
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
    public String getTicket(String serviceURL) throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost(ticketURL);
        post.addHeader(contentTypeHdr);
        post.addHeader(hostHdr);
        post.addHeader(acceptHdr);
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("service", serviceURL));
        post.setEntity(new UrlEncodedFormEntity(params, Consts.UTF_8));
            
        CloseableHttpResponse resp = null;
        try {
            resp = client.execute(post);
            if (resp.getStatusLine().getStatusCode() == 200) 
                return EntityUtils.toString(resp.getEntity());
                
            throw new Exception("Ticket retrieval (" + serviceURL + ") received status code " + resp.getStatusLine().getStatusCode());
            
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }
}
    

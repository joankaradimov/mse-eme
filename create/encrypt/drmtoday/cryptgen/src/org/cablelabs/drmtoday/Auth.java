// COPYRIGHT_BEGIN
// COPYRIGHT_END

package org.cablelabs.drmtoday;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;

import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;

/**
 * Helper class to generate REST commands for DRMToday CAS (Central Authentication Service)
 */
public class Auth {
    
    private static final String AUTH_PROPS_MERCHANT = "merchant";
    private static final String AUTH_PROPS_PASSWORD = "password";
    private static final String AUTH_PROPS_HOST     = "authHost";
    
    private static final String AUTH_API_LOGIN = "/cas/v1/tickets";

    private String merchant;
    private String password;
    private String authHost;
    
    private Header contentTypeHdr = new BasicHeader("Content-Type","application/x-www-form-urlencoded");
    private Header hostHdr = new BasicHeader("Host", authHost);
    private Header acceptHdr = new BasicHeader("Accept", "*/*");
    
    public Auth(String authPropsFile) throws FileNotFoundException, IOException {
        String prop;
        Properties loginprops = new Properties();
        loginprops.load(new FileInputStream(authPropsFile));
            
        if ((prop = loginprops.getProperty(AUTH_PROPS_MERCHANT)) == null) 
            throw new IllegalArgumentException("'" + AUTH_PROPS_MERCHANT + "' property not found in login props file");
        merchant = prop;
        if ((prop = loginprops.getProperty(AUTH_PROPS_PASSWORD)) == null)
            throw new IllegalArgumentException("'" + AUTH_PROPS_PASSWORD + "' property not found in login props file");
        password = prop;
        if ((prop = loginprops.getProperty(AUTH_PROPS_HOST)) == null)
            throw new IllegalArgumentException("'" + AUTH_PROPS_HOST + "' property not found in login props file");
        authHost = prop;
        hostHdr = new BasicHeader("Host", authHost);
    }
    
    public void login() {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("https://" + authHost + AUTH_API_LOGIN);
        post.addHeader(contentTypeHdr);
        post.addHeader(hostHdr);
        post.addHeader(acceptHdr);
    }
}
    

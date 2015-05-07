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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * DRMToday Properties File
 */
public class PropsFile {

    private static final String AUTH_PROPS_MERCHANT = "merchant";
    private static final String AUTH_PROPS_USERNAME = "username";
    private static final String AUTH_PROPS_PASSWORD = "password";
    private static final String AUTH_PROPS_HOST     = "authHost";
    private static final String AUTH_PROPS_FE_HOST  = "feHost";
    
    private String merchant;
    private String username;
    private String password;
    private String authHost;
    private String feHost;
    
    public PropsFile(String propsFile) throws FileNotFoundException, IOException {
        
        String prop;
        Properties props = new Properties();
        props.load(new FileInputStream(propsFile));
            
        if ((prop = props.getProperty(AUTH_PROPS_MERCHANT)) == null) 
            throw new IllegalArgumentException("'" + AUTH_PROPS_MERCHANT + "' property not found in login props file");
        merchant = prop;
        if ((prop = props.getProperty(AUTH_PROPS_USERNAME)) == null) 
            throw new IllegalArgumentException("'" + AUTH_PROPS_USERNAME + "' property not found in login props file");
        username = prop;
        if ((prop = props.getProperty(AUTH_PROPS_PASSWORD)) == null)
            throw new IllegalArgumentException("'" + AUTH_PROPS_PASSWORD + "' property not found in login props file");
        password = prop;
        if ((prop = props.getProperty(AUTH_PROPS_HOST)) == null)
            throw new IllegalArgumentException("'" + AUTH_PROPS_HOST + "' property not found in login props file");
        authHost = prop;
        if ((prop = props.getProperty(AUTH_PROPS_FE_HOST)) == null)
            throw new IllegalArgumentException("'" + AUTH_PROPS_FE_HOST + "' property not found in login props file");
        feHost = prop;
    }
    
    public String getMerchant() { return merchant; }
    
    public String getUsername() { return username; }
    
    public String getPassword() { return password; }
    
    public String getAuthHost() { return authHost; }
    
    public String getFeHost() { return feHost; }
}

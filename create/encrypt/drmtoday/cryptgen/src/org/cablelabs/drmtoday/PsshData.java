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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.cablelabs.cryptfile.KeyPair;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class PsshData {
    
    private String systemName;
    private byte[] systemID;
    private byte[] psshData;
    
    /**
     * Create a list of PsshData objects from JSON.  The JSON object is returned
     * from the DRMToday Cenc key ingest API call
     * 
     * @param json the DRMToday returned json object
     * @return list of pssh data
     */
    public static List<PsshData> parseFromDrmTodayJson(String json) {
        ArrayList<PsshData> retVal = new ArrayList<PsshData>();
        
        JsonParser parser = new JsonParser();
        JsonObject root = parser.parse(json).getAsJsonObject().get("systemId").getAsJsonObject();
        for (Map.Entry<String, JsonElement> systemId : root.entrySet()) {
            String systemName = null;
            byte[] psshData = null;
            byte[] systemID = KeyPair.parseGUID(systemId.getKey());
            for (Map.Entry<String, JsonElement> system : systemId.getValue().getAsJsonObject().entrySet()) {
                if (system.getKey().equals("name")) {
                    systemName = system.getValue().getAsString();
                } else if (system.getKey().equals("psshBoxContent")) {
                    psshData = Base64.decodeBase64(system.getValue().getAsString());
                }
            }
            retVal.add(new PsshData(systemName, systemID, psshData));
        }
        
        return retVal;
    }
    
    /**
     * Create a new PSSH data
     * 
     * @param systemName string system name
     * @param systemID system ID
     * @param psshData raw PSSH data
     */
    public PsshData(String systemName, byte[] systemID, byte[] psshData) {
        this.systemName = systemName;
        this.systemID = systemID;
        this.psshData = psshData;
    }
    
    public String getSystemName() {
        return systemName;
    }
    
    public byte[] getSystemID() {
        return systemID;
    }
    
    public byte[] getData() {
        return psshData;
    }
}

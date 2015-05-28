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


package org.cablelabs.access.cryptfile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import org.cablelabs.cryptfile.Bitstream;
import org.cablelabs.cryptfile.DRMInfoPSSH;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AccessPSSH extends DRMInfoPSSH {
    
    private static final byte[] ACCESS_SYSTEM_ID = {
        (byte)0xf2, (byte)0x39, (byte)0xe7, (byte)0x69,
        (byte)0xef, (byte)0xa3, (byte)0x48, (byte)0x50,
        (byte)0x9c, (byte)0x16, (byte)0xa9, (byte)0x03,
        (byte)0xc6, (byte)0x93, (byte)0x2e, (byte)0xfb
    };
    
    private byte[] accessMetadataBoxData;
    
    public AccessPSSH(List<byte[]> keyIDs) throws IOException {
        super(ACCESS_SYSTEM_ID);
        
        // Write AccessMetadata box data first
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream box = new DataOutputStream(baos);
        
        // Key IDs
        box.writeInt(keyIDs.size());
        for (byte[] keyID : keyIDs) {
            box.write(keyID);
        }
        
        // Data (none for now)
        box.writeInt(0);
        
        accessMetadataBoxData = baos.toByteArray();
    }

    /*
     * (non-Javadoc)
     * @see org.cablelabs.cryptfile.DRMInfoPSSH#generateContentProtection(org.w3c.dom.Document)
     */
    @Override
    public Element generateContentProtection(Document d) throws IOException {
        Element e = super.generateContentProtection(d);
        generateCENCContentProtectionData(d);
        return e;
    }

    /*
     * (non-Javadoc)
     * @see org.cablelabs.cryptfile.DRMInfoPSSH#generatePSSHData(java.io.DataOutputStream)
     */
    @Override
    protected void generatePSSHData(DataOutputStream dos) throws IOException {
        
        // Size is 4-byte "size" + 4-byte "boxtype" + 4-byte "version+flags" + datasize
        dos.write(12 + accessMetadataBoxData.length);
        
        // boxtype
        dos.write('a'); dos.write('m'); dos.write('e'); dos.write('t');
        
        // version & flags
        dos.writeInt(0);
        
        // data
        dos.write(accessMetadataBoxData);
    }

    /*
     * (non-Javadoc)
     * @see org.cablelabs.cryptfile.MP4BoxXML#generateXML(org.w3c.dom.Document)
     */
    @Override
    public Node generateXML(Document d) {
        Element e = generateDRMInfo(d);
        Bitstream b = new Bitstream();
        
        // Size 
        b.setupInteger(12 + accessMetadataBoxData.length, 32);
        e.appendChild(b.generateXML(d));
        
        // Type
        char[] amet = {'a','m','e','t'};
        b.setupFourCC(amet);
        e.appendChild(b.generateXML(d));
        
        // Version & Flags
        b.setupInteger(0, 32);
        e.appendChild(b.generateXML(d));
        
        // Box data
        b.setupData(accessMetadataBoxData);
        e.appendChild(b.generateXML(d));
        
        return e;
    }
}

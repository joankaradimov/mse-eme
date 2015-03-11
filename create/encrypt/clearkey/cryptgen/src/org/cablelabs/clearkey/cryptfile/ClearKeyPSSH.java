/* Copyright (c) 2014, CableLabs, Inc.
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

package org.cablelabs.clearkey.cryptfile;

import org.cablelabs.cryptfile.Bitstream;
import org.cablelabs.cryptfile.DRMInfoPSSH;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Abstract base class for CableLabs ClearKey PSSH variants
 */
public class ClearKeyPSSH extends DRMInfoPSSH {
    
    private static final byte[] CLEARKEY_SYSTEM_ID = {
        (byte)0x10, (byte)0x77, (byte)0xef, (byte)0xec,
        (byte)0xc0, (byte)0xb2, (byte)0x4d, (byte)0x02,
        (byte)0xac, (byte)0xe3, (byte)0x3c, (byte)0x1e,
        (byte)0x52, (byte)0xe2, (byte)0xfb, (byte)0x4b
    };
    
    // Must be PSSH version 1 
    public ClearKeyPSSH(byte[][] keyIDs) {
        super(CLEARKEY_SYSTEM_ID, 1, keyIDs);
    }

    @Override
    public Node generateXML(Document d) {
        Element e = generateDRMInfo(d);
                
        // 0 data size  
        Bitstream b = new Bitstream();
        b.setupInteger(0, 32);
        e.appendChild(b.generateXML(d));
        return e;
    }
}

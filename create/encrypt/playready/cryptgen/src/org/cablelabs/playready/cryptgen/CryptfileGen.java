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

package org.cablelabs.playready.cryptgen;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.cablelabs.clearkey.cryptfile.ClearKeyPSSH;
import org.cablelabs.cmdline.CmdLine;
import org.cablelabs.cryptfile.CryptKey;
import org.cablelabs.cryptfile.CryptTrack;
import org.cablelabs.cryptfile.CryptfileBuilder;
import org.cablelabs.cryptfile.DRMInfoPSSH;
import org.cablelabs.playready.PlayReadyKeyPair;
import org.cablelabs.playready.WRMHeader;
import org.cablelabs.playready.cryptfile.PlayReadyPSSH;
import org.w3c.dom.Document;

public class CryptfileGen {
    
    private static class Usage implements org.cablelabs.cmdline.Usage {
        public void usage() {
            System.out.println("Microsoft PlayReady MP4Box cryptfile generation tool.");
            System.out.println("");
            System.out.println("usage:  CryptfileGen [OPTIONS] <track_id>:{@<keyid_file>|<key_id>[,<key_id>...]} [<track_id>:{@<keyid_file>|<key_id>[,<key_id>...]}]...");
            System.out.println("");
            System.out.println("\t<track_id> is the track ID from the MP4 file to be encrypted.");
            System.out.println("\tAfter the '<track_id>:', you can specify either a file containing key IDs OR a");
            System.out.println("\tcomma-separated list of key IDs.  Key IDs are always represented in GUID form");
            System.out.println("\t(xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx). Multiple key IDs indicate the use of");
            System.out.println("\trolling keys.");
            System.out.println("");
            System.out.println("\t\t<keyid_file> is a file that contains a list of key IDs, one key ID per line.");
            System.out.println("");
            System.out.println("\t\t<keyid> is a key ID in GUID form.");
            System.out.println("");
            System.out.println("\tOPTIONS:");
            System.out.println("");
            System.out.println("\t-help");
            System.out.println("\t\tDisplay this usage message.");
            System.out.println("");
            System.out.println("\t-out <filename>");
            System.out.println("\t\tIf present, the cryptfile will be written to the given file. Otherwise output will be");
            System.out.println("\t\twritten to stdout.");
            System.out.println("");
            System.out.println("\t-version {4000|4100}");
            System.out.println("\t\tIf present, specifies the WRMHeader version to generate.  Must be either '4000' for v4.0.0.0");
            System.out.println("\t\tor '4100' for v4.1.0.0.  Default is '4000'.");
            System.out.println("");
            System.out.println("\t-url <license_url>");
            System.out.println("\t\tIf present, specifies the license URL to embed in the WRMHeaders.  If not specified, will");
            System.out.println("\t\tuse the default url of:");
            System.out.println("\t\t'http://playready.directtaps.net/pr/svc/rightsmanager.asmx?PlayRight=1&UseSimpleNonPersistentLicense=1'");
            System.out.println("");
            System.out.println("\t-roll <sample_count>");
            System.out.println("\t\tUsed for rolling keys only.  <sample_count> is the number of consecutive samples to be");
            System.out.println("\t\tencrypted with each key before moving to the next.");
            System.out.println("");
            System.out.println("\t-ck");
            System.out.println("\t\tAdd ClearKey PSSH to the cryptfile.");
            System.out.println("");
            System.out.println("\t-cp");
            System.out.println("\t\tPrint a DASH <ContentProtection> element that can be pasted into the MPD");
        }
    }
    
    private static class Track {
        List<String> keyIDs = new ArrayList<String>();
        int id;
    }
    
    public static void main(String[] args) {

        CmdLine cmdline = new CmdLine(new Usage());
        
        // Rolling keys
        int rollingKeySamples = -1;
        
        String outfile = null;
        String url = "http://playready.directtaps.net/pr/svc/rightsmanager.asmx?PlayRight=1&UseSimpleNonPersistentLicense=1";
        List<Track> tracks = new ArrayList<Track>();
        WRMHeader.Version headerVersion = WRMHeader.Version.V_4000;
        
        // Clearkey
        boolean clearkey = false;
        
        // Print content protection element?
        boolean printCP = false;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            
            // Parse options
            if (args[i].startsWith("-")) {
                String[] subopts;
                if ((subopts = cmdline.checkOption("-help", args, i, 0)) != null) {
                    (new Usage()).usage();
                    System.exit(0);
                }
                else if ((subopts = cmdline.checkOption("-out", args, i, 1)) != null) {
                    outfile = subopts[0];
                    i++;
                }
                else if ((subopts = cmdline.checkOption("-version", args, i, 1)) != null) {
                    if ("4000".equals(subopts[0])) {
                        headerVersion = WRMHeader.Version.V_4000;
                    }
                    else if ("4100".equals(subopts[0])) {
                        headerVersion = WRMHeader.Version.V_4000;
                    }
                    else {
                        cmdline.errorExit("Illegal WRMHeader version: " + subopts[0]);
                    }
                    i++;
                }
                else if ((subopts = cmdline.checkOption("-roll", args, i, 1)) != null) {
                    rollingKeySamples = Integer.parseInt(subopts[0]);
                    i++;
                }
                else if ((subopts = cmdline.checkOption("-url", args, i, 1)) != null) {
                    url = subopts[0];
                    i++;
                }
                else if ((subopts = cmdline.checkOption("-ck", args, i, 0)) != null) {
                    clearkey = true;
                }
                else if ((subopts = cmdline.checkOption("-cp", args, i, 0)) != null) {
                    printCP = true;
                }
                else {
                    cmdline.errorExit("Illegal argument: " + args[i]);
                }
                
                continue;
            }
            
            // Parse tracks
            String track_desc[] = args[i].split(":");
            if (track_desc.length != 2) {
                cmdline.errorExit("Illegal track specification: " + args[i]);
            }
            try {
                Track t = new Track();
                t.id = Integer.parseInt(track_desc[0]);
                
                // Read key IDs from file
                if (track_desc[1].startsWith("@")) {
                    String keyfile = track_desc[1].substring(1);
                    BufferedReader br = new BufferedReader(new FileReader(keyfile));
                    String line;
                    while ((line = br.readLine()) != null) {
                        t.keyIDs.add(line.trim());
                    }
                    br.close();
                }
                else { // Key IDs on command line
                    String[] keyIDs = track_desc[1].split(",");
                    for (String keyID : keyIDs) {
                        t.keyIDs.add(keyID);
                    }
                }
                
                tracks.add(t);
            }
            catch (IllegalArgumentException e) {
                cmdline.errorExit("Illegal track specification (" + e.getMessage() + ") -- " + track_desc[1]);
            }
            catch (FileNotFoundException e) {
                cmdline.errorExit("Key ID file not found: " + e.getMessage());
            }
            catch (IOException e) {
                cmdline.errorExit("Error reading from Key ID file: " + e.getMessage());
            }
        }
        
        List<WRMHeader> wrmHeaders = new ArrayList<WRMHeader>();
        List<CryptTrack> cryptTracks = new ArrayList<CryptTrack>();
        
        // Build one CryptTrack for every track and gather a list of all
        // WRMHeaders to put in one PSSH
        for (Track t : tracks) {
            List<CryptKey> cryptKeys = new ArrayList<CryptKey>();
            for (String keyID : t.keyIDs) {
                PlayReadyKeyPair prKey = new PlayReadyKeyPair(keyID);
                wrmHeaders.add(new WRMHeader(headerVersion, prKey, url));
                
                cryptKeys.add(new CryptKey(prKey));
            }
            cryptTracks.add(new CryptTrack(t.id, 8, null, cryptKeys, rollingKeySamples));
        }
        
        // Create our PSSH
        List<DRMInfoPSSH> psshList = new ArrayList<DRMInfoPSSH>();
        psshList.add(new PlayReadyPSSH(wrmHeaders));
        
        // Add clearkey PSSH if requested
        if (clearkey) {
            int keyCount = 0;
            for (CryptTrack t : cryptTracks) {
                keyCount += t.getKeys().size();
            }
            byte[][] keyIDs = new byte[keyCount][];
            int i = 0;
            System.out.println("Ensure the following keys are available to the client:");
            for (CryptTrack t : cryptTracks) {
                for (CryptKey key : t.getKeys()) {
                    System.out.println("\t" + Hex.encodeHexString(key.getKeyPair().getID()) +
                                       " : " + Hex.encodeHexString(key.getKeyPair().getKey()) +
                                       " (" + Base64.encodeBase64String(key.getKeyPair().getID()) +
                                       " : " + Base64.encodeBase64String(key.getKeyPair().getKey()) + ")");
                    keyIDs[i++] = key.getKeyPair().getID();
                }
            }
            System.out.println("");
            psshList.add(new ClearKeyPSSH(keyIDs));
        }
        
        // Print ContentProtection element
        if (printCP) {
            System.out.println("############# Content Protection Element #############");
            for (DRMInfoPSSH pssh : psshList) {
                Document d = CryptfileBuilder.newDocument();
                try {
                    d.appendChild(pssh.generateContentProtection(d));
                }
                catch (IOException e) {
                    System.out.println("Could not generate ContentProtection element!");
                    continue;
                }
                CryptfileBuilder.writeXML(d, System.out);
            }
            System.out.println("######################################################");
        }
        
        // Create the cryptfile builder
        CryptfileBuilder cfBuilder = new CryptfileBuilder(CryptfileBuilder.ProtectionScheme.AES_CTR,
                                                          cryptTracks, psshList);
        
        // Write the output
        Document d = cfBuilder.buildCryptfile();
        CryptfileBuilder.writeXML(d, System.out);
        try {
            if (outfile != null) {
                System.out.println("Writing cryptfile to: " + outfile);
                CryptfileBuilder.writeXML(d, new FileOutputStream(outfile));
            }
        }
        catch (FileNotFoundException e) {
            cmdline.errorExit("Could not open output file (" + outfile + ") for writing");
        }
    }
}

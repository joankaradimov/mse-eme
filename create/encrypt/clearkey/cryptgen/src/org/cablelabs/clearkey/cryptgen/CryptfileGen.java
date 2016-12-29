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

package org.cablelabs.clearkey.cryptgen;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.cablelabs.clearkey.cryptfile.ClearKeyPSSH;
import org.cablelabs.cmdline.CmdLine;
import org.cablelabs.cryptfile.CryptKey;
import org.cablelabs.cryptfile.CryptTrack;
import org.cablelabs.cryptfile.CryptfileBuilder;
import org.cablelabs.cryptfile.DRMInfoPSSH;
import org.cablelabs.cryptfile.KeyPair;
import org.w3c.dom.Document;

/**
 * This utility will build a MP4Box ClearKey cryptfile for a given piece of content.
 */
public class CryptfileGen {

    private static class Usage implements org.cablelabs.cmdline.Usage {
        public void usage() {
            System.out.println("ClearKey MP4Box cryptfile generation tool.");
            System.out.println("");
            System.out.println("usage:  CryptfileGen [OPTIONS] <track_id>:{@<key_file>|<key_id>=[<key>][,<key_id>=[<key>]...]} [<track_id>:{@<key_file>|<key_id>=[<key>][,<key_id>=[<key>]...]}]...");
            System.out.println("");
            System.out.println("\t<track_id> is the track ID from the MP4 file to be encrypted.");
            System.out.println("\tAfter the '<track_id>:', you can specify either a file containing key/keyID pairs");
            System.out.println("\tOR a comma-separated list of keyID/key pairs separated by ':'.  Key IDs are always");
            System.out.println("\trepresented in GUID form (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).  Key values are");
            System.out.println("\talways in hexadecimal.  Multiple key IDs indicate the use of rolling keys.");
            System.out.println("");
            System.out.println("\t\t<keyid_file> is a file that contains a list of key IDs, one pair per line in");
            System.out.println("\t\tGUID form.");
            System.out.println("");
            System.out.println("\t\t<keyid> is a key ID in GUID form.");
            System.out.println("");
            System.out.println("\t\t<key> is a 16-byte key value in hexadecimal (with or without the leading '0x').");
            System.out.println("\t\tIf key is ommitted, a random key will be generated for you.");
            System.out.println("");
            System.out.println("\tOPTIONS:");
            System.out.println("");
            System.out.println("\t-help");
            System.out.println("\t\tDisplay this usage message.");
            System.out.println("");
            System.out.println("\t-quiet");
            System.out.println("\t\tDo not log information on stdout.");
            System.out.println("");
            System.out.println("\t-out <filename>");
            System.out.println("\t\tIf present, the cryptfile will be written to the given file. Otherwise output will be");
            System.out.println("\t\twritten to stdout.");
            System.out.println("");
            System.out.println("\t-roll <sample_count>");
            System.out.println("\t\tUsed for rolling keys only.  <sample_count> is the number of consecutive samples to be");
            System.out.println("\t\tencrypted with each key before moving to the next.");
            System.out.println("");
            System.out.println("\t-cp");
            System.out.println("\t\tPrint a DASH <ContentProtection> element that can be pasted into the MPD");
        }
    }
    
    private static class Track {
        List<KeyPair> keypairs = new ArrayList<KeyPair>();
        int id;
    }
    
    public static void main(String[] args) {

        CmdLine cmdline = new CmdLine(new Usage());
        
        // Rolling keys
        int rollingKeySamples = -1;
        
        // Print content protection element?
        boolean printCP = false;
        
        String outfile = null;
        List<Track> tracks = new ArrayList<Track>();

        PrintStream outputStream = System.out;
        
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
                else if ((subopts = cmdline.checkOption("-roll", args, i, 1)) != null) {
                    rollingKeySamples = Integer.parseInt(subopts[0]);
                    i++;
                }
                else if ((subopts = cmdline.checkOption("-cp", args, i, 0)) != null) {
                    printCP = true;
                }
                else if ((subopts = cmdline.checkOption("-quiet", args, i, 0)) != null) {
                    outputStream = new PrintStream(new OutputStream() {
                        public void write(int b) {
                        }
                    });
                }
                else {
                    cmdline.errorExit("Illegal argument: " + args[i]);
                }
                
                continue;
            }
            
            // Parse tracks
            String track_desc[] = args[i].split(":");
            if (track_desc.length != 2) {
                cmdline.errorExit("Illegal track description");
            }
            try {
                Track t = new Track();
                t.id = Integer.parseInt(track_desc[0]);
                
                // Read key pairs from file
                if (track_desc[1].startsWith("@")) {
                    String keyfile = track_desc[1].substring(1);
                    BufferedReader br = new BufferedReader(new FileReader(keyfile));
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] keypair = line.split(":");
                        if (keypair.length != 2) {
                            cmdline.errorExit("Illegal keypair from file: " + line);
                        }
                        t.keypairs.add(new KeyPair(keypair[0], keypair[1]));
                    }
                    br.close();
                }
                else { // Key pairs on command line
                    String[] keypairsarg = track_desc[1].split(",");
                    for (String keypairs : keypairsarg) {
                        String[] keypair = keypairs.split("=");
                        if (keypair.length == 2) {
                            t.keypairs.add(new KeyPair(keypair[0], keypair[1]));
                        } else if (keypair.length == 1) {
                            Random r = new Random();
                            byte[] key = new byte[16];
                            r.nextBytes(key);
                            t.keypairs.add(new KeyPair(keypair[0], key));
                        } else {
                            cmdline.errorExit("Illegal keypair: " + keypairs);
                        }
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
        
        List<CryptTrack> cryptTracks = new ArrayList<CryptTrack>();
        List<KeyPair> keypairs = new ArrayList<KeyPair>(); // Need this for URL-based PSSH
        for (Track t : tracks) {
            List<CryptKey> cryptKeys = new ArrayList<CryptKey>();
            for (KeyPair key : t.keypairs) {
                cryptKeys.add(new CryptKey(key));
            }
            keypairs.addAll(t.keypairs);
            
            cryptTracks.add(new CryptTrack(t.id, 8, null, cryptKeys, rollingKeySamples));
        }
        
        ClearKeyPSSH ckPSSH = null;
        
        byte[][] keyIDs = new byte[keypairs.size()][];
        int i = 0;
        outputStream.println("Ensure the following keys are available to the client:");
        for (KeyPair keypair : keypairs) {
            outputStream.println("\t" + Hex.encodeHexString(keypair.getID()) +
                               " : " + Hex.encodeHexString(keypair.getKey()) +
                               " (" + Base64.encodeBase64URLSafeString(keypair.getID()) +
                               " : " + Base64.encodeBase64URLSafeString(keypair.getKey()) + ")");
            keyIDs[i++] = keypair.getID();
        }
        outputStream.println("");
        ckPSSH = new ClearKeyPSSH(keyIDs);
        
        List<DRMInfoPSSH> psshList = new ArrayList<DRMInfoPSSH>();
        psshList.add(ckPSSH);
        
        // Print ContentProtection element
        if (printCP) {
            outputStream.println("############# Content Protection Element #############");
            for (DRMInfoPSSH pssh : psshList) {
                Document d = CryptfileBuilder.newDocument();
                try {
                    d.appendChild(pssh.generateContentProtection(d));
                }
                catch (IOException e) {
                    outputStream.println("Could not generate ContentProtection element!");
                    continue;
                }
                CryptfileBuilder.writeXML(d, outputStream);
            }
            outputStream.println("######################################################");
        }
        
        CryptfileBuilder cfBuilder = new CryptfileBuilder(CryptfileBuilder.ProtectionScheme.AES_CTR,
                                                          cryptTracks, psshList);
        
        // Write the output
        Document d = cfBuilder.buildCryptfile();
        CryptfileBuilder.writeXML(d, outputStream);
        if (outfile != null) {
            try {
                outputStream.println("Writing cryptfile to: " + outfile);
                CryptfileBuilder.writeXML(d, new FileOutputStream(outfile));
            }
            catch (FileNotFoundException e) {
                cmdline.errorExit("Could not open output file (" + outfile + ") for writing");
            }
        }
    }

}

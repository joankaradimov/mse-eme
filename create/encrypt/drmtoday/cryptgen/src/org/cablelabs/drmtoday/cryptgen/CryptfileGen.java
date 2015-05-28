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

package org.cablelabs.drmtoday.cryptgen;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import org.cablelabs.cryptfile.KeyPair;
import org.cablelabs.drmtoday.AuthAPI;
import org.cablelabs.drmtoday.CencKey;
import org.cablelabs.drmtoday.CencKeyAPI;
import org.cablelabs.drmtoday.PropsFile;
import org.cablelabs.drmtoday.PsshData;
import org.cablelabs.drmtoday.cryptfile.DRMTodayPSSH;
import org.cablelabs.playready.PlayReadyKeyPair;
import org.cablelabs.playready.WRMHeader;
import org.cablelabs.playready.cryptfile.PlayReadyPSSH;
import org.cablelabs.widevine.cryptfile.WidevinePSSH;
import org.w3c.dom.Document;

/**
 * This utility will build a MP4Box cryptfile for a given piece of content using DRMToday.  The steps
 * involved are:
 * <ol>
 *   <li>Generate randomg keys/keyIDs for each track</li>
 *   <li>Ingest the created keys into the CommonEncryption keystore for the given DRMToday account</li>
 *   <li>Generate the PSSH boxes for each desired DRM as returned by the DRMToday key ingest</li>
 *   <li>Generate the MP4Box cryptfile</li>
 * </ol>
 *
 * The 
 */
public class CryptfileGen {
    
    private static class Usage implements org.cablelabs.cmdline.Usage {
        public void usage() {
            System.out.println("DRMToday MP4Box cryptfile generation tool.");
            System.out.println("");
            System.out.println("usage:  CryptfileGen [OPTIONS] <drmtoday_props_file> <assetId> <track_id>:<track_type> [<track_id>:<track_type>]...");
            System.out.println("");
            System.out.println("\t<drmtoday_props_file>");
            System.out.println("\t\tDRMToday properties file that contains merchant login info.  <drmtoday_props_file>");
            System.out.println("\t\tis a Java properties file with the following properties:");
            System.out.println("\t\t\tmerchant: Your assigned merchant ID");
            System.out.println("\t\t\tusername: Your DRMToday frontend username");
            System.out.println("\t\t\tpassword: Your DRMToday frontend password");
            System.out.println("\t\t\tauthHost: Host to use for DRMToday CAS operations");
            System.out.println("\t\t\tfeHost: Host to use for DRMToday frontend operations");
            System.out.println("");
            System.out.println("\t<assetId> The DRMToday assetId");
            System.out.println("");
            System.out.println("\t<track_id> is the track ID from the MP4 file to be encrypted");
            System.out.println("");
            System.out.println("\t<track_type> is one of AUDIO, VIDEO, or VIDEO_AUDIO describing the content type of the");
            System.out.println("\tassociated track");
            System.out.println("");
            System.out.println("\tOPTIONS:");
            System.out.println("");
            System.out.println("\t-help");
            System.out.println("\t\tDisplay this usage message.");
            System.out.println("");
            System.out.println("\t-out <filename>");
            System.out.println("\t\tIf present, the cryptfile will be written to the given file. Otherwise output will be");
            System.out.println("\t\twritten to stdout");
            System.out.println("");
            System.out.println("\t-variantId");
            System.out.println("\t\tOptional DRMToday asset variantId.");
            System.out.println("");
            System.out.println("\t-ck");
            System.out.println("\t\tAdd ClearKey PSSH to the cryptfile.");
            System.out.println("");
            System.out.println("\t-wv");
            System.out.println("\t\tAdd Widevine PSSH to the cryptfile.");
            System.out.println("");
            System.out.println("\t-pr");
            System.out.println("\t\tAdd PlayReady PSSH to the cryptfile.");
            System.out.println("");
            System.out.println("\t-prdt");
            System.out.println("\t\tAdd PlayReady PSSH (provided by DRMToday) to the cryptfile.");
            System.out.println("");
            System.out.println("\t-cp");
            System.out.println("\t\tPrint a DASH <ContentProtection> element (for each DRM) that can be pasted into the MPD");
        }
    }
    
    private enum StreamType {
        VIDEO,
        AUDIO,
        VIDEO_AUDIO,
        NUM_TYPES
    }
    
    private static class Track {
        int id;
        KeyPair keypair;
        StreamType streamType;
    }
    
    public static void main(String[] args) {
        
        CmdLine cmdline = new CmdLine(new Usage());

        // Tracks
        Track[] track_args = new Track[StreamType.NUM_TYPES.ordinal()];
        
        // DRMToday login properties file
        String dtPropsFile = null;
        
        String assetId = null;
        String variantId = null;
        
        String outfile = null;
        
        // DRMs
        boolean clearkey = false;
        boolean widevine = false;
        boolean playready = false;
        boolean playreadyDT = false;
        
        // Print content protection element?
        boolean printCP = false;
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            
            // Parse options
            if (args[i].startsWith("-")) {
                String[] subopts;
                if ((subopts = cmdline.checkOption("-help", args, i, 0)) != null ||
                     (subopts = cmdline.checkOption("-h", args, i, 0)) != null) {
                    (new Usage()).usage();
                    System.exit(0);
                }
                else if ((subopts = cmdline.checkOption("-out", args, i, 1)) != null) {
                    outfile = subopts[0];
                    i++;
                }
                else if ((subopts = cmdline.checkOption("-variantId", args, i, 1)) != null) {
                    variantId = subopts[0];
                    i++;
                }
                else if ((subopts = cmdline.checkOption("-ck", args, i, 0)) != null) {
                    clearkey = true;
                }
                else if ((subopts = cmdline.checkOption("-wv", args, i, 0)) != null) {
                    widevine = true;
                }
                else if ((subopts = cmdline.checkOption("-pr", args, i, 0)) != null) {
                    playready = true;
                }
                else if ((subopts = cmdline.checkOption("-prdt", args, i, 0)) != null) {
                    playreadyDT = true;
                }
                else if ((subopts = cmdline.checkOption("-cp", args, i, 0)) != null) {
                    printCP = true;
                }
                else {
                    cmdline.errorExit("Illegal argument: " + args[i]);
                }
                
                continue;
            }
            
            // Get login properties file
            if (dtPropsFile == null) {
                dtPropsFile = args[i];
                continue;
            }
            
            // Get login properties file
            if (assetId == null) {
                assetId = args[i];
                continue;
            }
            
            // Parse tracks
            String track_desc[] = args[i].split(":");
            if (track_desc.length != 2) {
                cmdline.errorExit("Illegal track specification: " + args[i]);
            }
            try {
                Track t = new Track();
                StreamType streamType = StreamType.valueOf(track_desc[1]);
                t.streamType = streamType;
                t.id = Integer.parseInt(track_desc[0]);
                t.keypair = KeyPair.random(); // Create a random key pair
                track_args[t.streamType.ordinal()] = t;
            }
            catch (IllegalArgumentException e) {
                cmdline.errorExit("Illegal track_type -- " + track_desc[1]);
            }
        }
        
        if (dtPropsFile == null) {
            cmdline.errorExit("Must specify login props file!");
        }
        
        if (assetId == null) {
            cmdline.errorExit("Must specify assetId!");
        }
        
        if (playready && playreadyDT) {
            cmdline.errorExit("Can not specify both -pr and -prdt!");
        }
        
        // Validate track stream types.  Can have AUDIO and/or VIDEO or VIDEO_AUDIO
        if (track_args[StreamType.VIDEO_AUDIO.ordinal()] != null &&
            (track_args[StreamType.VIDEO.ordinal()] != null || track_args[StreamType.AUDIO.ordinal()] != null)) {
            cmdline.errorExit("Illegal track specification!  Can have (AUDIO and/or VIDEO) or (VIDEO_AUDIO) only!");
        }
        
        // Request keys
        List<Track> trackList = new ArrayList<Track>();
        for (Track t : track_args) {
            if (t != null)
                trackList.add(t);
        }
        if (trackList.isEmpty()) {
            cmdline.errorExit("Must specify at least one track!");
        }
        
        // Load properties file
        PropsFile props = null;
        try {
            props = new PropsFile(dtPropsFile);
        } catch (Exception e) {
            cmdline.errorExit("Error loading DRMToday properties file! -- " + e.getMessage());
        }
        
        // Must use one non-ClearKey DRM
        if (!widevine && !playready) {
            cmdline.errorExit("Must specify at least one non-ClearKey DRM!");
        }
        
        // Login and get ticket for key ingest API
        AuthAPI drmtodayAuth = new AuthAPI(props.getUsername(), props.getPassword(), props.getAuthHost());
        try {
            drmtodayAuth.login();
            
        } catch (Exception e) {
            cmdline.errorExit("Error during DRMToday CAS process! -- " + e.getMessage());
        }
        
        List<DRMInfoPSSH> psshList = new ArrayList<DRMInfoPSSH>();
        List<CryptTrack> cryptTracks = new ArrayList<CryptTrack>();
        
        // Ingest key for each track
        CencKey cencKey = new CencKey();
        cencKey.assetId = assetId;
        if (variantId != null) {
            cencKey.variantId = variantId;
        }
        CencKeyAPI cencKeyAPI = new CencKeyAPI(drmtodayAuth, props.getFeHost(), props.getMerchant());
        for (Track t : trackList) {
            cencKey.key = Base64.encodeBase64String(t.keypair.getKey());
            cencKey.keyId = Base64.encodeBase64String(t.keypair.getID());
            cencKey.streamType = t.streamType.toString();
            try {
                String resp = cencKeyAPI.ingestKey(cencKey);
                List<PsshData> psshdata = PsshData.parseFromDrmTodayJson(resp);
                for (PsshData d : psshdata) {
                    // Add DRMToday PSSH boxes if requested
                    if ((WidevinePSSH.isWidevine(d.getSystemID()) && widevine) || 
                        (PlayReadyPSSH.isPlayReady(d.getSystemID()) && playreadyDT)) {
                        psshList.add(new DRMTodayPSSH(d));
                    }
                }
            }
            catch (Exception e) {
                // TODO Auto-generated catch block
                System.out.println("Error during Cenc key ingest! -- " + e.getMessage());
            }
            
            // Add our PlayReady PSSH box if requested
            if (playready) {
                PlayReadyKeyPair keyPair = new PlayReadyKeyPair(t.keypair);
                List<WRMHeader> headers = new ArrayList<WRMHeader>();
                headers.add(new WRMHeader(WRMHeader.Version.V_4000, keyPair, PlayReadyPSSH.TEST_URL));
                psshList.add(new PlayReadyPSSH(headers, PlayReadyPSSH.ContentProtectionType.CENC));
            }
            
            List<CryptKey> keyList = new ArrayList<CryptKey>();
            keyList.add(new CryptKey(t.keypair));
            cryptTracks.add(new CryptTrack(t.id, 8, null, keyList, 0));
        }
        
        
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

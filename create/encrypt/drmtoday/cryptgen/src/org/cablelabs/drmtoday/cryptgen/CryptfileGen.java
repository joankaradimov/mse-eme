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

package org.cablelabs.drmtoday.cryptgen;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.cablelabs.clearkey.cryptfile.ClearKeyPSSH;
import org.cablelabs.cmdline.CmdLine;
import org.cablelabs.cryptfile.CryptKey;
import org.cablelabs.cryptfile.CryptTrack;
import org.cablelabs.cryptfile.CryptfileBuilder;
import org.cablelabs.cryptfile.DRMInfoPSSH;
import org.cablelabs.cryptfile.KeyPair;
import org.w3c.dom.DOMException;
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
            System.out.println("\t\t\tpassword: Your merchant password");
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
        String loginPropsFile = null;
        
        String assetId = null;
        String variantId = null;
        
        String outfile = null;
        
        // DRMs
        boolean clearkey = false;
        boolean widevine = false;
        boolean playready = false;
        
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
                else if ((subopts = cmdline.checkOption("-cp", args, i, 0)) != null) {
                    printCP = true;
                }
                else {
                    cmdline.errorExit("Illegal argument: " + args[i]);
                }
                
                continue;
            }
            
            // Get login properties file
            if (loginPropsFile == null) {
                loginPropsFile = args[i];
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
        
        if (loginPropsFile == null) {
            cmdline.errorExit("Must specify login props file!");
        }
        
        if (assetId == null) {
            cmdline.errorExit("Must specify assetId!");
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
        
        // Login
        String ticket;
        String merchant;
        String password;
        try {
            
        } catch (Exception e) {
            cmdline.errorExit(e.getMessage());
        }
        
        KeyRequest request = (rollingKeyCount != -1 && rollingKeyStart != -1) ?
            new KeyRequest(content_id_str, trackList, rollingKeyStart, rollingKeyCount) :
            new KeyRequest(content_id_str, trackList);
        if (signingFile != null) {
            try {
                request.setSigningProperties(signingFile);
            }
            catch (Exception e) {
                System.err.println("Error in signing file: " + e.getMessage());
                System.exit(1);
            }
        }
        ResponseMessage m = request.requestKeys();
        if (m.status != ResponseMessage.StatusCode.OK) {
            System.err.println("Received error from key server! Code = " + m.status.toString());
            System.exit(1);
        }
    
        // The Widevine key server provides the PSSH data directly to us.  Optionally, we could
        // build our own WidevineCencHeader protobuf object from the information in the response.
        // For rolling keys it might be a better solution to build our own, since the widevine server
        // currently sends a new PSSH for every key in every track.  Building our own, we could keep
        // a single PSSH per track
        
        List<DRMInfoPSSH> psshList = new ArrayList<DRMInfoPSSH>();
        List<CryptTrack> cryptTracks = new ArrayList<CryptTrack>();
        
        // Build PSSH's (has to be one for each track in the Widevine world)
        for (ResponseMessage.Track track : m.tracks) {
            for (ResponseMessage.Track.PSSH pssh : track.pssh) {
                
                // Only widevine DRM for now
                if (!pssh.drm_type.equalsIgnoreCase("widevine"))
                    continue;
                
                WidevinePSSHProtoBuf.WidevineCencHeader wvPSSH = null;
                try {
                    wvPSSH = WidevinePSSHProtoBuf.WidevineCencHeader.parseFrom(Base64.decodeBase64(pssh.data));
                }
                catch (InvalidProtocolBufferException e) {
                    errorExit("Could not parse PSSH protobuf from key response message");
                }
                psshList.add(new WidevinePSSH(wvPSSH));
            }
                
            // Get the keys for this track and add to our cryptfile
            List<CryptKey> keyList = new ArrayList<CryptKey>();
            keyList.add(new CryptKey(new KeyPair(Base64.decodeBase64(track.key_id),
                                                 Base64.decodeBase64(track.key))));
            cryptTracks.add(new CryptTrack(track_args[track.type.ordinal()].id, 8, null,
                                           keyList, rollingKeySamples));
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

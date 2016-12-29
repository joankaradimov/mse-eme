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

package org.cablelabs.widevine.cryptgen;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
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
import org.cablelabs.widevine.Track;
import org.cablelabs.widevine.TrackType;
import org.cablelabs.widevine.cryptfile.WidevinePSSH;
import org.cablelabs.widevine.keyreq.KeyRequest;
import org.cablelabs.widevine.keyreq.ResponseMessage;
import org.cablelabs.widevine.proto.WidevinePSSHProtoBuf;
import org.w3c.dom.Document;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * This utility will build a MP4Box Widevine cryptfile for a given piece of content.  The steps
 * involved are:
 * <ol>
 *   <li>Request encryption keys/keyIDs from the Widevine key server for each track</li>
 *   <li>Build the Widevine PSSH</li>
 *   <li>Generate the MP4Box cryptfile</li>
 * </ol>
 *
 */
public class CryptfileGen {

    private static class Usage implements org.cablelabs.cmdline.Usage {
        public void usage() {
            System.out.println("Google Widevine MP4Box cryptfile generation tool.");
            System.out.println("");
            System.out.println("usage:  CryptfileGen [OPTIONS] <content_id> <track_id>:<track_type> [<track_id>:<track_type>]...");
            System.out.println("");
            System.out.println("\t<content_id> is a unique string representing the content to be encrypted");
            System.out.println("");
            System.out.println("\t<track_id> is the track ID from the MP4 file to be encrypted");
            System.out.println("");
            System.out.println("\t<track_type> is one of HD, SD, or AUDIO describing the type of the associated track");
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
            System.out.println("\t\twritten to stdout");
            System.out.println("");
            System.out.println("\t-sign <sign_props_file>");
            System.out.println("\t\tIf present, key requests will be signed with the given key information.  <sign_props_file> is");
            System.out.println("\t\ta Java properties file with the following properties:");
            System.out.println("\t\t\turl:      Your assigned key server URL");
            System.out.println("\t\t\tkey:      Your assigned 32-byte signing key, base64 notation");
            System.out.println("\t\t\tiv:       Your assigned 16-byte initialization vector, base64 notation");
            System.out.println("\t\t\tprovider: Your assigned provider name");
            System.out.println("\t\tIf this argument is not present, the requests will be unsigned and the");
            System.out.println("\t\t\"widevine_test\" provider and URL will be used");
            System.out.println("");
            System.out.println("\t-roll <start_time>,<key_count>,<sample_count>");
            System.out.println("\t\tUsed for rolling keys only.  <start_time> is the integer time basis for the first");
            System.out.println("\t\trequested key.  Could be epoch or media time or anything else meaningful.  <key_count>");
            System.out.println("\t\tis the integer number of keys requested.  <sample_count> is the number of consecutive");
            System.out.println("\t\tsamples to be encrypted with each key before moving to the next.");
            System.out.println("");
            System.out.println("\t-ck");
            System.out.println("\t\tAdd ClearKey PSSH to the cryptfile.");
            System.out.println("");
            System.out.println("\t-cp");
            System.out.println("\t\tPrint a DASH <ContentProtection> element that can be pasted into the MPD");
        }
    }
    
    public static void main(String[] args) {

        CmdLine cmdline = new CmdLine(new Usage());
        
        // Track list -- one slot for each track type
        Track[] track_args = new Track[TrackType.NUM_TYPES.ordinal()];
        
        // Should the request be signed
        String signingFile = null;
        
        // Rolling keys
        int rollingKeyStart = -1;
        int rollingKeyCount = -1;
        int rollingKeySamples = -1;
        
        String outfile = null;
        
        // Clearkey
        boolean clearkey = false;
        
        // Print content protection element?
        boolean printCP = false;

        PrintStream outputStream = System.out;

        
        // Parse arguments
        String content_id_str = null;
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
                else if ((subopts = cmdline.checkOption("-sign", args, i, 1)) != null) {
                    signingFile = subopts[0];
                    i++;
                }
                else if ((subopts = cmdline.checkOption("-ck", args, i, 0)) != null) {
                    clearkey = true;
                }
                else if ((subopts = cmdline.checkOption("-roll", args, i, 3)) != null) {
                    rollingKeyStart = Integer.parseInt(subopts[0]);
                    rollingKeyCount = Integer.parseInt(subopts[1]);
                    rollingKeySamples = Integer.parseInt(subopts[2]);
                    i++;
                }
                else if ((subopts = cmdline.checkOption("-quiet", args, i, 0)) != null) {
                    outputStream = new PrintStream(new OutputStream() {
                        public void write(int b) {
                        }
                    });
                }
                else if ((subopts = cmdline.checkOption("-cp", args, i, 0)) != null) {
                    printCP = true;
                }
                else {
                    cmdline.errorExit("Illegal argument: " + args[i]);
                }
                
                continue;
            }
            
            // Get content ID
            if (content_id_str == null) {
                content_id_str = args[i];
                continue;
            }
            
            // Parse tracks
            String track_desc[] = args[i].split(":");
            if (track_desc.length != 2) {
                cmdline.errorExit("Illegal track specification: " + args[i]);
            }
            try {
                Track t = new Track();
                TrackType trackType = TrackType.valueOf(track_desc[1]);
                t.type = trackType;
                t.id = Integer.parseInt(track_desc[0]);
                track_args[t.type.ordinal()] = t;
            }
            catch (IllegalArgumentException e) {
                cmdline.errorExit("Illegal track_type -- " + track_desc[1]);
            }
        }
        
        if (content_id_str == null) {
            cmdline.errorExit("Must specify content_id string!");
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
                    cmdline.errorExit("Could not parse PSSH protobuf from key response message");
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
            outputStream.println("Ensure the following keys are available to the client:");
            for (CryptTrack t : cryptTracks) {
                for (CryptKey key : t.getKeys()) {
                    outputStream.println("\t" + Hex.encodeHexString(key.getKeyPair().getID()) +
                                       " : " + Hex.encodeHexString(key.getKeyPair().getKey()) +
                                       " (" + Base64.encodeBase64String(key.getKeyPair().getID()) +
                                       " : " + Base64.encodeBase64String(key.getKeyPair().getKey()) + ")");
                    keyIDs[i++] = key.getKeyPair().getID();
                }
            }
            outputStream.println("");
            psshList.add(new ClearKeyPSSH(keyIDs));
        }
        
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
        try {
            if (outfile != null) {
                outputStream.println("Writing cryptfile to: " + outfile);
                CryptfileBuilder.writeXML(d, new FileOutputStream(outfile));
            }
        }
        catch (FileNotFoundException e) {
            cmdline.errorExit("Could not open output file (" + outfile + ") for writing");
        }
        
    }
}

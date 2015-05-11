
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

/**
 * To include your own Widevine server credentials,
 * create a new node.js module file and pass the full path
 * of that module file (as would be necessary for passing
 * to the 'require' API) on the command line when running.
 * For example,
 *
 * node proxy.js /var/data/myserver
 *
 * In /var/data, there would exist a file called 'myserver.js'.  In that
 * file, you must define the following module, replacing your own
 * data values for 'url', 'key', 'iv', and 'provider'.
 *
 * var wvServer = {};
 *
 * // Server URL
 * wvServer.url = "https://license.widevine.com/cenc/your_url";
 *
 * // 32-byte request signing key in base64 format
 * wvServer.key = "HtQS+5CSGFBt0NrjTTaXS9+tTwYWl12l2rsTi/+GQp4=";
 *
 * // 16-byte initialization vector used in signing the requests
 * wvServer.iv = "MD1GNrtCwMd1M/eoSwKb8Q==";
 *
 * // String provider name
 * wvServer.provider = "my_provider";
 *
 * module.exports = wvServer;
 */

var port = 8202;
var express = require('express');
var app = express();

//app.get('/right/user/[userId]/asset/[assetId]?variantId=[variantId]&sessionId=[sessionId]&clientId=[clientId]');
var params = ['userId', 'assetId', 'variantId', 'sessionId', 'clientId'];
app.param('userId', function(req, res, next, userId) {
    req.userId = userId;
    next();
});
app.param('assetId', function(req, res, next, assetId) {
    req.assetId = assetId;
    next();
});
app.get('/right/user/:userId/asset/:assetId', function(req, res) {
    console.log("In app GET");
    var variantId = (req.query.hasOwnProperty("variantId")) ? req.query.variantId : null;
    var sessionId = req.query.sessionId;
    if (!sessionId) {
        console.log("No sessionId query param found! Returning 404");
        res.status(400).send("No sessionId query param!").end();
    } else {
        // Parse requested rights (encoded in base64)
        var crtJSON = (new Buffer(sessionId, 'base64')).toString('utf8');
        var crt = JSON.parse(crtJSON);

        console.log("Requested rights: " + crtJSON);
        // Add 'assetId' and 'variantId' and return
        crt.assetId = req.assetId;
        if (variantId) {
            crt.variantId = variantId;
        }

        res.type('json');
        res.vary('Accept');
        res.send(JSON.stringify(crt));
    }
});

var server = app.listen(port, function() {

});



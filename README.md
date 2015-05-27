# CableLabs MSE/EME Reference Tools

A reference toolkit for creating premium, adaptive bitrate (ABR) content for playback using the latest HTML5 extensions.  [Media Source Extensions (MSE)](http://www.w3.org/TR/media-source/) allow Javascript applications to deliver individual buffers of audio, video, and data to the browser's media pipeline which enables more flexible playback of ABR content such as [MPEG-DASH](http://mpeg.chiariglione.org/standards/mpeg-dash) or [Apple HLS](https://developer.apple.com/streaming/).  [Encrypted Media Extensions (EME)](http://www.w3.org/TR/encrypted-media/) allow Javascript applications to pass content license request/response messages between a DRM-specific Content Decryption Module (CDM) within the browser and a remote license server.

## Scope

The current CableLabs reference tools are designed to generate content using a specific set of industry standards that we will think capture a large footprint of needs amongst the user community.  We hope to expand the scope in the future to encompass additional codecs and container types.

* MP4 (ISOBMFF) Container
* AVC/H.264 Video Codec
* AAC Audio Codec
* MPEG-DASH Adaptive Bitrate Packaging
* ISO Common Encryption

## Documentation

See the latest documentation [here](https://html5.cablelabs.com/mse-eme/doc/overview.html)

## DRM

Our tools will include support for proprietary and open DRM systems as documentation and test servers are made available to us.  Here is a table that indicates the current integration status of each DRM or licensing service with our tools.

| DRM | Status | Notes |
|-----|--------|-------|
|Microsoft PlayReady|Working|Uses the [PlayReady test server](http://playready.directtaps.net/pr/doc/customrights/)|
|Google Widevine|Working|Users will need to contact Widevine to setup their own license portal.|
|CableLabs ClearKey|Working|CableLabs-specific implementation of [ClearKey](http://www.w3.org/TR/encrypted-media/#simple-decryption-clear-key)|
|CastLabs [DRMToday](http://www.drmtoday.com)|Partial|Widevine is OK.  PlayReady license requests are being rejected in dash.js.  This could be a problem with dash.js and not the creation tools|
|Adobe Access/PrimeTime|Not Started||
|Apple FairPlay|Not Started||

## HTML5 Player Application

For playback of encrypted DASH content using MSE/EME, we have augmented the dash.js player with support for some additional DRMs, improved support for playback of content that uses ISO Common Encryption, and support for multiple EME versions available on production browsers..

## 3rd Party Acknowledgments

Our tools rely heavily on the following great open source and/or free library projects

* [LibAV](http://libav.org/): transcoding and adaptive bitrate generation
* [MP4Box](http://gpac.wp.mines-telecom.fr/mp4box/): encryption and MPEG-DASH packaging
* [x264](http://www.videolan.org/developers/x264.html): AVC/H.264 codec for video
* [libfdk_aac](http://www.iis.fraunhofer.de/en/bf/amm/implementierungen/fdkaaccodec.html): Fraunhofer FDK AAC codec for audio


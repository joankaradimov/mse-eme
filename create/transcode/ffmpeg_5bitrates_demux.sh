#!/bin/bash -x

# Copyright (c) 2014, CableLabs, Inc.
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice,
# this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
# this list of conditions and the following disclaimer in the documentation
# and/or other materials provided with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.

ffmpeg=/home/grutz/Projects/CableLabs/ffmpeg/ffmpeg
ffprobe=/home/grutz/Projects/CableLabs/ffmpeg/ffprobe

function usage {
  echo ""
  echo "Transcode/Transrate Script"
  echo "usage:"
  echo "   video_scaled_demux_5bitrates <input_file> <output_dir>"
}

if [ -z $1 ]; then
  echo "Must provide input media file"
  usage
  exit 1
fi
if [ -z $2 ]; then
  echo "Must provide output directory for transcoded/transrated files"
  usage
  exit 1
fi

mkdir -p $2

framerate=$((`./ffprobe $1 -select_streams v -show_entries stream=avg_frame_rate -v quiet -of csv="p=0"`))

function transcode_video {
  vbitrate=$1
  res=$2
  profile=$3
  level=$4
  outdir=$5
  infile=$6
  outfile=abr_`echo $res | sed 's/:/x/'`_h264-${vbitrate}.mp4

  $ffmpeg -i $infile -s $res -map_chapters -1 -maxrate $vbitrate -minrate $vbitrate -bufsize $vbitrate -an -codec:v libx264 -profile:v $profile -level $level -b:v $vbitrate -x264opts "keyint=$framerate:min-keyint=$framerate:no-scenecut" $outdir/$outfile
}

function transcode_audio {
  abitrate=$1
  outdir=$2
  infile=$3
  outfile=abr_aac-lc_${abitrate}.mp4

  $ffmpeg -i $infile -map_chapters -1 -vn -codec:a libfdk_aac -profile:a aac_low -b:a $abitrate $outdir/$outfile
}

transcode_video "360k" "512:288" "main" 30 $2 $1
transcode_video "620k" "704:396" "main" 30 $2 $1
transcode_video "1340k" "896:504" "high" 31 $2 $1
transcode_video "2500k" "1280:720" "high" 32 $2 $1
transcode_video "4500k" "1920:1080" "high" 40 $2 $1

transcode_audio "128k" $2 $1
transcode_audio "192k" $2 $1


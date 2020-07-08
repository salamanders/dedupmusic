# Find Duplicate MP3 Files

There are much better methods to do this! 😶

This method reads a directory tree for mp3 files, 
and produces a CRC from the uncompressed first 60 seconds of audio.

These CRCs are used to find **perfectly** identical music after ignoring the Tags.

It will not work with the same song compressed twice, 
or for songs compressed at two different bitrates.

# Find Duplicate MP3 Files

There are much better methods to do this! 😶
This app is a prototype, and intended to have readable code.

You must have `ffmpeg` and `fpcalc` installed.  (On MacOS I suggest `brew`)

`ScanAll.kt` reads a directory tree for mp3 and m4a files, 
and produces a CRC from the uncompressed first 60 seconds of audio
and a raw chromaprint,
saving the results to `audio_scan.tsv`.

These CRCs are used to find **perfectly** identical music after ignoring the Tags.
CRCs will not work with the same song compressed twice, 
or for songs compressed at two different bitrates.

The chromaprint is used to find audio distances, 
and a bit xor distance of < 8 is increasingly likely to be a duplicate.

FindDup.kt lists out likely duplicates.


All parts use kotlin flows with coroutines to perform operations in parallel
and should use all cores.

## TODO

- [ ] Non-hardcoded music folders
- [ ] Pick the "better" file to keep and delete the leftover
- [ ] Store chromaprints using default instead of raw
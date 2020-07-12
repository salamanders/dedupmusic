package info.benjaminhill.audio

import info.benjaminhill.utils.retryOrNull
import info.benjaminhill.utils.runCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import java.io.File
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


// TODO: https://github.com/mpatric/mp3agic


/**
 * Everything you ever wanted to know about an audio file.  And more.
 */
@ExperimentalUnsignedTypes
@ExperimentalTime
data class AudioInfo(
    val file: File,
    val bytes: ULong = file.length().toULong(),
    val duration: Duration,
    val crc: ULong,
    val chroma: List<UInt>,
) {

    override fun toString(): String = listOf(
        file.absolutePath,
        bytes,
        duration.inSeconds.toInt(),
        crc,
        chroma.joinToString(","),
    ).joinToString("\t")


    @ExperimentalUnsignedTypes
    infix fun chromaDistance(other: AudioInfo): Double = chroma.zip(other.chroma).map { (c0, c1) ->
        (c0 xor c1).countOneBits()
    }.also {
        require(it.isNotEmpty())
    }.average()

    companion object {

        fun String.toAudioInfo() = split('\t').let { (file, bytes, durationSec, crc, chromas) ->
            AudioInfo(
                file = File(file),
                bytes = bytes.toULong(),
                duration = durationSec.toInt().seconds,
                crc = crc.toULong(),
                chroma = chromas.split(",").map { it.toUInt() },
            )
        }

        @ExperimentalCoroutinesApi
        @ExperimentalUnsignedTypes
        @ExperimentalTime
        suspend fun File.toAudioInfo(): AudioInfo? {
            val crc: ULong? = retryOrNull { audioToCrc(this) }
            if (crc == null) {
                println("Retry CRC failed: $absolutePath")
            }
            val chromaDuration = retryOrNull { audioToChromaprintDuration(this) }
            if (chromaDuration?.first == null || chromaDuration.second == null) {
                println("Retry chroma failed: $absolutePath")
            }
            return if (crc != null && chromaDuration != null &&
                chromaDuration.first != null && chromaDuration.second != null
            ) {
                AudioInfo(
                    file = this,
                    crc = crc,
                    chroma = chromaDuration.first!!,
                    duration = chromaDuration.second!!.seconds
                )
            } else {
                null
            }
        }

        private const val CHROMA_FP = "FINGERPRINT="
        private const val CHROMA_DURATION = "DURATION="

        @ExperimentalUnsignedTypes
        @ExperimentalTime
        @ExperimentalCoroutinesApi
        private suspend fun audioToChromaprintDuration(file: File): Pair<List<UInt>?, Int?>? = runCommand(
            arrayOf(
                "fpcalc",
                "-raw", // list of integers FINGERPRINT=
                //"-signed", // pg_acoustid compatibility
                file.absolutePath
            )
        ).toList().let { results ->
            Pair(
                results.firstOrNull { it.startsWith(CHROMA_FP) }?.substring(CHROMA_FP.length)?.split(",")
                    ?.map { it.toUInt() },
                results.firstOrNull { it.startsWith(CHROMA_DURATION) }?.substring(CHROMA_DURATION.length)?.toInt()
            )
        }

        private const val CRC_START = "CRC=0x"

        @ExperimentalUnsignedTypes
        @ExperimentalTime
        @ExperimentalCoroutinesApi
        /**
         * @return {Pair<List<String>, List<String>>} output, errors (which may not be bad)
         */
        private suspend fun audioToCrc(file: File) = runCommand(
            arrayOf(
                "/usr/local/bin/ffmpeg",
                "-nostdin",
                "-t", "120", // matches fpcalc default
                "-i", file.absolutePath, // NOT quoted
                "-c:a", "copy",
                "-f", "crc", "-"
            )
        ).toList().firstOrNull { it.startsWith(CRC_START) }?.substring(CRC_START.length)?.toULong(radix = 16)
    }
}
package info.benjaminhill.audio

import com.google.gson.Gson
import com.mpatric.mp3agic.Mp3File
import info.benjaminhill.utils.runCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import java.io.File
import java.net.URI
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

/**
 * Everything you ever wanted to know about an audio file.  And more.
 */
@ExperimentalUnsignedTypes
@ExperimentalTime
class AudioInfo(
    val uri: URI,
    val bytes: Long = File(uri).length(),
    val bitrate: Int,
    val duration: Duration,
    val chroma: UIntArray,
) {
    override fun toString(): String = GSON.toJson(this)!!

    fun file(): File = File(uri)

    fun fileParentName(): String = "${file().parentFile.name}/${file().name}"

    /**
     * @return percent distance (0.0..1.0)
     */
    @ExperimentalUnsignedTypes
    infix fun chromaDistance(other: AudioInfo): Double = chroma.zip(other.chroma).map { (c0, c1) ->
        (c0 xor c1).countOneBits() / UInt.SIZE_BITS.toDouble()
    }.also {
        require(it.isNotEmpty())
    }.average()

    companion object {

        private val GSON = Gson()

        fun String.toAudioInfo() = GSON.fromJson(this, AudioInfo::class.java)!!

        @ExperimentalCoroutinesApi
        @ExperimentalUnsignedTypes
        @ExperimentalTime
        suspend fun File.toAudioInfo(): AudioInfo? = try {
            val chroma = this.toChromaprint() ?: error("Chroma failed: $absolutePath")
            val duration: Duration
            val bitrate: Int
            Mp3File(this).also {
                duration = it.lengthInSeconds.seconds
                bitrate = it.bitrate
            }
            AudioInfo(
                uri = this.absoluteFile.toURI(),
                bitrate = bitrate,
                duration = duration,
                chroma = chroma,
            )
        } catch (e: Exception) {
            println("Skipping ${this.absolutePath}: $e")
            null
        }

        @ExperimentalUnsignedTypes
        @ExperimentalTime
        @ExperimentalCoroutinesApi
        internal suspend fun File.toChromaprint(): UIntArray? {
            val chromaFpHeader = "FINGERPRINT="
            return runCommand(
                command = arrayOf(
                    "fpcalc",
                    "-raw", // list of integers FINGERPRINT=
                    //"-signed", // pg_acoustid compatibility
                    this.absolutePath
                ),
                maxDuration = 10.seconds
            )
                .toList()
                .firstOrNull { it.startsWith(chromaFpHeader) }?.substring(chromaFpHeader.length)
                ?.split(",")?.map { it.toUInt() }?.toUIntArray()
        }
    }
}
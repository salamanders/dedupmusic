package info.benjaminhill.audio

import info.benjaminhill.utils.CountHits
import info.benjaminhill.utils.concurrentIndexedMap
import info.benjaminhill.utils.runCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.ExperimentalTime

const val CRC_START = "CRC=0x"
val SAVED_RESULTS_FILE = File("audio_scan.tsv")

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
).toList().partition { it.startsWith(CRC_START) }

const val CHROMA_FP = "FINGERPRINT="

@ExperimentalTime
@ExperimentalCoroutinesApi
private suspend fun audioToChromaprint(file: File) = runCommand(
    arrayOf(
        "fpcalc",
        "-raw", // list of integers FINGERPRINT=
        //"-signed", // pg_acoustid compatibility
        file.absolutePath
    )
).toList().partition { it.startsWith(CHROMA_FP) }

@ExperimentalTime
@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
fun main() = runBlocking {
    val hits = CountHits(hitFrequency = 100)
    val allFiles = listOf("Music", "beets_music", "picard_music")
        .flatMap { File(System.getProperty("user.home"), it).walk() }
        .filter { listOf("mp3", "m4a").contains(it.extension.toLowerCase()) }

    println("Total Files: ${allFiles.size}")

    SAVED_RESULTS_FILE.printWriter().use { out ->
        allFiles
            .asFlow()
            .concurrentIndexedMap(concurrencyLevel = Runtime.getRuntime().availableProcessors()) { _, mp3file ->
                val crc = audioToCrc(mp3file).let { (resultsCrc, errors) ->
                    if (resultsCrc.size == 1) {
                        resultsCrc.first().substring(CRC_START.length).toULong(radix = 16)
                    } else {
                        println("Error getting CRC for ${mp3file.canonicalPath}:")
                        println(resultsCrc.joinToString("\n"))
                        println(errors.joinToString("\n"))
                        null
                    }
                }

                val chroma: List<UInt>? = audioToChromaprint(mp3file).let { (resultChroma, errors) ->
                    if (resultChroma.size == 1) {
                        resultChroma.first().substring(CHROMA_FP.length).split(",").map { it.toUInt() }
                    } else {
                        println("Error getting Chromaprint for ${mp3file.canonicalPath}:")
                        println(resultChroma.joinToString("\n"))
                        println(errors.joinToString("\n"))
                        null
                    }
                }
                hits.hit()
                if (crc != null && chroma != null) {
                    Triple(mp3file, crc, chroma)
                } else {
                    null
                }
            }
            .filterNotNull()
            .toList()
            .sortedBy { it.first.absolutePath }
            .forEach { (file, crc, chroma) ->
                out.println("${file.absolutePath}\t$crc\t${chroma.joinToString(",")}")
            }
    }

}

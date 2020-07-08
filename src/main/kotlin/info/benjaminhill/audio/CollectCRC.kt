package info.benjaminhill.audio

import info.benjaminhill.utils.CountHits
import info.benjaminhill.utils.concurrentMap
import info.benjaminhill.utils.runCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.ExperimentalTime


@ExperimentalTime
@ExperimentalCoroutinesApi
/**
 * @return {Pair<List<String>, List<String>>} output, errors (which may not be bad)
 */
internal suspend fun audioToCrc(file: File) = runCommand(
    arrayOf(
        "/usr/local/bin/ffmpeg",
        "-nostdin",
        "-t", "60",
        "-i", file.absolutePath, // NOT quoted
        "-c:a", "copy",
        "-f", "crc", "-"
    )
).toList().partition { it.startsWith("CRC=") }

@ExperimentalTime
@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
fun main() = runBlocking {
    val hits = CountHits(hitFrequency = 100)

    File("mp3crc.tsv").printWriter().use { out ->

        val allFiles = File("/Users/benhill/Music").walk().filter {
            it.extension.toLowerCase() == "mp3"
        }.toMutableList()

        allFiles.addAll(File("/Users/benhill/beets_music").walk().filter {
            it.extension.toLowerCase() == "mp3"
        })

        allFiles.addAll(File("/Users/benhill/picard_music").walk().filter {
            it.extension.toLowerCase() == "mp3"
        })


        println("Total Files: ${allFiles.size}")
        allFiles
            .asFlow()
            .concurrentMap { mp3file ->
                hits.hit()
                val (results, errors) = audioToCrc(mp3file)
                if (results.size == 1) {
                    val hash = results.first().substring(6).toULong(radix = 16)
                    Pair(hash, mp3file)
                } else {
                    println("Error getting CRC for ${mp3file.canonicalPath}:")
                    println(results.joinToString("\n"))
                    println(errors.joinToString("\n"))
                    null
                }
            }
            .filterNotNull()
            .collect { (hash, file) ->
                out.println("$file\t$hash")
            }
    }

}


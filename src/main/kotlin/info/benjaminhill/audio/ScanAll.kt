package info.benjaminhill.audio

import info.benjaminhill.audio.AudioInfo.Companion.toAudioInfo
import info.benjaminhill.utils.LogInfrequently
import info.benjaminhill.utils.pMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

val SAVED_RESULTS_FILE = File("audio_scan.tsv")
const val MAX_CHROMA_DIFFERENCE = 10.0

@ExperimentalTime
val MAX_DURATION_DIFFERENCE = 10.seconds

@ExperimentalTime
@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
fun main() = runBlocking(Dispatchers.IO) {
    val hits = LogInfrequently()

    if (!SAVED_RESULTS_FILE.exists()) {
        val allFiles = listOf("Music", "beets_music", "picard_music")
            .flatMap { File(System.getProperty("user.home"), it).walk() }
            .filter { listOf("mp3", "m4a").contains(it.extension.toLowerCase()) }

        println("Fingerprinting and saving ${allFiles.size} audio files.")
        SAVED_RESULTS_FILE.printWriter().use { out ->
            allFiles
                .asFlow()
                .pMap { _, mp3file ->
                    hits.hit()
                    mp3file.toAudioInfo()
                }
                .filterNotNull()
                .toList()
                .sortedBy { it.file.absolutePath }
                .forEach { audioInfo -> out.println(audioInfo) }
        }
    }

    val files = SAVED_RESULTS_FILE.readLines().map { it.toAudioInfo() }.filter { it.file.exists() }
    println("Read saved info.  Comparing ${files.size} existing files with valid info.")

    println("# Exact Duplicates (CRC)")
    files.groupBy { it.crc }
        .filterValues { it.size > 1 }
        .forEach { (crc, paths) ->
            println(crc)
            paths.forEach {
                println("  ${it.file.absolutePath}")
            }
        }

    println("# Near Duplicates (chromaprint)")
    val bestFriends = files.asFlow()
        .pMap { index, audioInfo ->
            hits.hit()
            files.drop(index + 1)
                .filter { audioInfo.duration.minus(it.duration).absoluteValue < MAX_DURATION_DIFFERENCE }
                .map { Triple(audioInfo, it, audioInfo chromaDistance it) }
                .toList().minByOrNull { it.third }
        }.filterNotNull()
        .filter { it.third < MAX_CHROMA_DIFFERENCE }
        .toList()
        .sortedBy { it.third }

    val pathPrefixSize = System.getProperty("user.home").length
    bestFriends.forEachIndexed { idx, (audioInfo0, audioInfo1, distance) ->
        println(
            "$idx: '${audioInfo0.file.absolutePath.drop(pathPrefixSize)}' to '${
                audioInfo1.file.absolutePath.drop(pathPrefixSize)
            }' = $distance"
        )
    }
}


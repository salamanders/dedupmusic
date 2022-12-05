package info.benjaminhill.audio

import info.benjaminhill.audio.AudioInfo.Companion.toAudioInfo
import info.benjaminhill.utils.LogInfrequently
import info.benjaminhill.utils.pMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Paths
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

val SAVED_RESULTS_FILE = File("audio_scan.jsonl")
const val MAX_CHROMA_DIFFERENCE = 0.1 // Seems sane.

@ExperimentalTime
val MAX_DURATION_DIFFERENCE = 10.seconds

@ExperimentalTime
val hits = LogInfrequently(delay = 20.seconds)

@ExperimentalTime
@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
fun main() = runBlocking(Dispatchers.IO) {

    scanAllFiles(
        Paths.get(System.getProperty("user.home"), "Downloads", "Takeout").toFile()
    )

    val audioInfos = SAVED_RESULTS_FILE
        .readLines()
        .map { it.toAudioInfo() }
        .filter { File(it.uri).exists() && it.chroma.size > 10 }

    dedupFiles(audioInfos)
}


@ExperimentalCoroutinesApi
@ExperimentalUnsignedTypes
@ExperimentalTime
private suspend fun scanAllFiles(vararg allDirs: File) {
    if (SAVED_RESULTS_FILE.exists()) {
        return
    }

    require(allDirs.isNotEmpty()) { "No directories targeted for scanning." }

    val localFiles = allDirs.flatMap { it.walk() }
    require(localFiles.isNotEmpty()) { "No files found!" }
    val allFiles = localFiles
        .filter { listOf("mp3", "m4a").contains(it.extension.toLowerCase()) }
        .filter { !it.isHidden }
        .shuffled()
    // .take(1000) // DEBUG
    require(allFiles.isNotEmpty()) { "No audio files found!" }

    println("Fingerprinting and saving ${allFiles.size} audio files.")

    // Try all twice if missing - easier than some sort of "Failed/retry" queue.
    val results = mutableMapOf<URI, AudioInfo>()
    repeat(3) { repeatNum ->
        println("Pass $repeatNum")
        allFiles
            .filter { !results.containsKey(it.toURI()) }
            .asFlow()
            .pMap { _, mp3file ->
                mp3file.toAudioInfo().also {
                    hits.hit()
                }
            }
            .filterNotNull()
            .collect { results[it.uri] = it }
    }

    println("Final list to save: ${results.size}")
    SAVED_RESULTS_FILE.printWriter().use { out ->
        results.values
            .sortedBy { it.uri.toString() }
            .forEach { audioInfo -> out.println(audioInfo) }
    }

}

@ExperimentalUnsignedTypes
@ExperimentalTime
private suspend fun dedupFiles(
    allAudioInfos: List<AudioInfo>
) {
    println("Excluding deleted files...")
    val audioInfos = allAudioInfos.filter { it.file().exists() }

    println("# Near Duplicates (chromaprint) of ${audioInfos.size} files.")
    val bestFriends = audioInfos.asFlow()
        .pMap { _, audioInfo ->
            hits.hit()
            audioInfos.filter { other ->
                audioInfo.duration.minus(other.duration).absoluteValue <= MAX_DURATION_DIFFERENCE &&
                        audioInfo.uri.compareTo(other.uri) != 0
            }
                .filter { other -> File(audioInfo.uri).parent.startsWith(File(other.uri).parent) }
                .map { Triple(audioInfo, it, audioInfo chromaDistance it) }
                .toList().minByOrNull { it.third }
        }.filterNotNull()
        .filter { it.third <= MAX_CHROMA_DIFFERENCE }
        .toList()
        .sortedBy { it.third }

    bestFriends
        .forEachIndexed { idx, (ai0, ai1, distance) ->
            val (s0, s1) = listOf(ai0, ai1)
                .sortedBy { it.fileParentName().length }
                .sortedByDescending { it.bitrate }
            println("$idx: dist:$distance")
            println(" Keep:'${s0.fileParentName()}' ${s0.bitrate}")
            println(" Trash:'${s1.fileParentName()}' ${s1.bitrate}")
            if (s0.file().exists() &&
                s1.file().exists()
            ) {
                println(" ...trashing ${s1.file()}")
                Desktop.getDesktop().moveToTrash(s1.file())
            }

        }
}



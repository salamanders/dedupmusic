package info.benjaminhill.audio

import info.benjaminhill.audio.AudioInfo.Companion.toAudioInfo
import info.benjaminhill.utils.LogInfrequently
import info.benjaminhill.utils.pMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.Exception
import java.net.URI
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

val SAVED_RESULTS_FILE = File("audio_scan.jsonl")
const val MAX_CHROMA_DIFFERENCE = 0.1

@ExperimentalTime
val MAX_DURATION_DIFFERENCE = 10.seconds

@ExperimentalTime
val hits = LogInfrequently(delay = 20.seconds)

@ExperimentalTime
@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
fun main() = runBlocking(Dispatchers.IO) {

    scanAllFiles()

    val audioInfos = SAVED_RESULTS_FILE
        .readLines()
        .map { it.toAudioInfo() }
        .filter { File(it.uri).exists()  && it.chroma.size > 10 }
        .filter { it.uri.toString().contains("32_MINI") }

    println("Read saved info.  Comparing ${audioInfos.size} existing files with valid info.")

    audioInfos.forEach {
        try {
            println(it.file())
            println(it.fileLazy)
        } catch(e:Exception) {
            println(e.stackTraceToString())
            println(it)
            exitProcess(1)
        }
    }

    println("# Near Duplicates (chromaprint)")
    val bestFriends = audioInfos.asFlow()
        .filter { it.uri.toString().contains("32_MINI") }
        .pMap { _, audioInfo ->
            hits.hit()
            audioInfos.filter { other ->
                        audioInfo.duration.minus(other.duration).absoluteValue <= MAX_DURATION_DIFFERENCE &&
                        audioInfo.uri.compareTo(other.uri) != 0
            }
                .filter { other->
                    true ||
                    File(audioInfo.uri).parent.startsWith(File(other.uri).parent)
                }
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
            println(" Trask:'${s1.fileParentName()}' ${s1.bitrate}")
            if(s0.file().exists() &&
                   s1.file().exists()) {
                //Desktop.getDesktop().moveToTrash(s1)
            }

        }
}

@ExperimentalCoroutinesApi
@ExperimentalUnsignedTypes
@ExperimentalTime
private suspend fun scanAllFiles() {
    if (!SAVED_RESULTS_FILE.exists()) {
        val localFiles = listOf("Music", "beets_music", "picard_music")
            .flatMap { File(System.getProperty("user.home"), it).walk() }
        val usbFiles = File("/Volumes/32_MINI/").walk()
            .toList()
        val allFiles = (localFiles + usbFiles)
            .filter { listOf("mp3", "m4a").contains(it.extension.toLowerCase()) }
            .filter { !it.isHidden }
            .shuffled()
        //.take(1000) // DEBUG

        println("Fingerprinting and saving ${allFiles.size} audio files.")

        // Try all twice if missing - easier than some sort of "Failed/retry" queue.
        val results = mutableMapOf<URI, AudioInfo>()
        repeat(2) { repeatNum ->
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
}


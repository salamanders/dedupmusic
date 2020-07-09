package info.benjaminhill.audio

import info.benjaminhill.utils.CountHits
import info.benjaminhill.utils.concurrentIndexedMap
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import java.io.File
import kotlin.time.ExperimentalTime

data class AudioInfo @ExperimentalUnsignedTypes constructor(
    val path: String,
    val crc: ULong,
    val chroma: List<UInt>
)

@ExperimentalTime
@ExperimentalUnsignedTypes
suspend fun main() {
    val files = SAVED_RESULTS_FILE.readLines().mapNotNull { line ->
        val (path, crc, chromas) = line.split('\t')
        if (crc.isBlank() || chromas.isBlank() || crc == "null" || chromas == "null") {
            null
        } else {
            AudioInfo(
                path = path,
                crc = crc.toULong(),
                chroma = chromas.split(",").map { it.toUInt() }
            )
        }
    }.filter {
        File(it.path).exists()
    }

    println(files.size)

    println("# Exact Duplicates")
    files.groupBy { it.crc }
        .filterValues { it.size > 1 }
        .forEach { (crc, paths) ->
            println(crc)
            paths.forEach {
                println("  ${it.path}")
            }
        }

    println("# Near Duplicates")

    // Match each files to closest other
    val hits = CountHits(hitFrequency = 200)
    val bestFriends = files
        //.take(500)
        .asFlow().concurrentIndexedMap(concurrencyLevel = 8) { index, audioInfo ->
            files.drop(index + 1).minByOrNull { chromaDistance(audioInfo.chroma, it.chroma) }?.let { closestOther ->
                hits.hit()
                Triple(audioInfo, closestOther, chromaDistance(audioInfo.chroma, closestOther.chroma))
            }
        }.filterNotNull()
        .filter { (_, _, distance) -> distance < 10.0 }
        .toList()
        .sortedBy { it.third }

    val dropSize = System.getProperty("user.home").length

    bestFriends.forEachIndexed { idx, (audioInfo0, audioInfo1, distance) ->
        println("$idx: '${audioInfo0.path.drop(dropSize)}' to '${audioInfo1.path.drop(dropSize)}' = $distance")
    }

}

@ExperimentalUnsignedTypes
fun chromaDistance(cl0: List<UInt>, cl1: List<UInt>): Double = cl0.zip(cl1).map { (c0, c1) ->
    (c0 xor c1).countOneBits()
}.also {
    require(it.isNotEmpty())
}.average()
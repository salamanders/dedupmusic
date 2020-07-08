package info.benjaminhill.audio

import java.io.File

data class AudioCRC @ExperimentalUnsignedTypes constructor(
    val path:String,
    val crc:ULong
)

@ExperimentalUnsignedTypes
fun main()  {
    val crcs = File("mp3crc.tsv").readLines().map {
        val (path, crc) = it.split('\t')
        AudioCRC(path, crc.toULong())
    }.filter {
        File(it.path).exists()
    }

    println(crcs.size)

    val dups = crcs.groupBy {
        it.crc
    }.filterValues {
        it.size > 1
    }

    dups.forEach { (crc, paths) ->
        println(crc)
        paths.forEach {
            println("  ${it.path}")
        }
    }

}
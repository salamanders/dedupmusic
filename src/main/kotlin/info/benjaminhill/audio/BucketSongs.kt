package info.benjaminhill.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths
import kotlin.time.ExperimentalTime

internal val BAD_CHARS = Regex("[^a-zA-Z0-9.\\- ,']+")

@ExperimentalCoroutinesApi
@ExperimentalTime
fun main() = runBlocking {

    val files = Paths.get(System.getProperty("user.home"), "Downloads", "Takeout")
        .toFile()
        .walk()
        .filter { listOf("mp3", "m4a").contains(it.extension.toLowerCase()) }
        .filter { !it.isHidden }
        .toList()
        .shuffled()

    println("Renaming ${files.size} files.")

    files.forEach { file ->
        delay(500)
        try {
            println(file.absolutePath)
            val (artist, album, title) = SongTagger.tag(file)
            println("artist:$artist, album:$album, title:$title")

            val destinationDir = Paths.get(
                System.getProperty("user.home"),
                "Desktop",
                "Sorted",
                artist.replace(BAD_CHARS, "").trim()
            )

            destinationDir.toFile().mkdirs()
            var destFile = destinationDir.resolve(
                title.replace(BAD_CHARS, "").trim() + "." + file.extension
            ).toFile()

            var dupNum = 0
            while (destFile.exists()) {
                destFile = destinationDir.resolve(
                    title.replace(BAD_CHARS, "").trim() +
                            " " + dupNum++
                            + "." + file.extension
                ).toFile()
            }
            file.renameTo(destFile)
        } catch (e: Exception) {
            println("file:${file.absolutePath} caused $e")
        }
    }
}
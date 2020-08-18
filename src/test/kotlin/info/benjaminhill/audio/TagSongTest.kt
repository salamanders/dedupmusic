package info.benjaminhill.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

import org.junit.Assert.*
import java.io.File
import java.nio.file.Paths
import kotlin.time.ExperimentalTime

class TagSongTest {

    @ExperimentalCoroutinesApi
    @ExperimentalTime
    @Test
    fun test1() {
        runBlocking {

            val file = Paths.get(System.getProperty("user.home"), "Downloads", "Takeout")
                .toFile()
                .walk()
            .filter { listOf("mp3", "m4a").contains(it.extension.toLowerCase()) }
            .filter { !it.isHidden }
                .toList()
                .random()

            println(file.absolutePath)
            val(artist, album, title) = SongTagger.tag(file)
            println("artist:$artist, album:$album, title:$title")
        }
    }
}
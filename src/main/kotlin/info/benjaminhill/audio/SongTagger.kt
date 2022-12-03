package info.benjaminhill.audio

import com.google.gson.Gson
import info.benjaminhill.utils.runCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import java.io.BufferedReader
import java.io.File
import java.net.URL
import kotlin.time.ExperimentalTime
import kotlin.time.seconds


object SongTagger {

    private var gson = Gson()

    private const val PREFIX_DUR = "DURATION="
    private const val PREFIX_FP = "FINGERPRINT="
    private const val MY_CLIENT_ID = "kDv3v9F2WP"

    /**
     * @return artist, album, song_name
     */
    @ExperimentalCoroutinesApi
    @ExperimentalTime
    suspend fun tag(file: File): Triple<String, String, String> {
        require(file.canRead()) { "Unable to read file '${file.absolutePath}'" }

        // "-signed" for pg_acoustid compatibility
        val fpcalcLines = runCommand(
            command = arrayOf("fpcalc", "-signed", file.absolutePath),
            maxDuration = 10.seconds
        ).toList()

        val duration = fpcalcLines
            .first { it.startsWith(PREFIX_DUR) }
            .removePrefix(PREFIX_DUR).trim().toInt()
        val fingerprint = fpcalcLines
            .first { it.startsWith(PREFIX_FP) }
            .removePrefix(PREFIX_FP).trim()

        val contentJson = URL(
            "https://api.acoustid.org/v2/lookup?" +
                    "client=$MY_CLIENT_ID" +
                    "&meta=recordings+releasegroups+compress" +
                    "&duration=$duration" +
                    "&fingerprint=$fingerprint"
        )
            .openStream()
            .bufferedReader()
            .use(BufferedReader::readText)

        // println(contentJson)
        val lookup = gson.fromJson(contentJson, AcousticLookup::class.java)!!

        lookup.results.forEach { result ->
            result.recordings
                ?.filter { it.artists != null && it.artists.isNotEmpty() }
                ?.forEach { recording ->
                    recording.releasegroups?.forEach { releaseGroup ->
                        return Triple(
                            recording.artists!!.first().name,
                            releaseGroup.title,
                            recording.title
                        )

                    }
                }
        }
        error("No valid song from lookup")
    }
}

/**
 * Mirrors the JSON structure returned from the API
 */
private data class AcousticLookup(
    val status: String,
    val results: List<Result>,
) {
    data class Result(
        val id: String,
        val recordings: List<Recording>?,
    ) {
        data class Recording(
            val artists: List<Artist>?,
            val duration: Int,
            val id: String,
            val releasegroups: List<ReleaseGroup>?,
            val title: String,
        ) {
            data class Artist(
                val id: String,
                val name: String,
            )

            data class ReleaseGroup(
                val artists: List<Artist>?,
                val id: String,
                val secondarytypes: List<String>?,
                val title: String,
                val type: String,
            )
        }
    }
}


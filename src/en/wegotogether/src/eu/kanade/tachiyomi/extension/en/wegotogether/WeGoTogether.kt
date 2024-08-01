package eu.kanade.tachiyomi.extension.en.wegotogether

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class WeGoTogether : HttpSource() {
	override val baseUrl = "https://wegotogethercomic.com"
	override val lang = "en"
	override val name = "we go together"
	override val supportsLatest = false

	override val client = network.client.newBuilder()
		.build()

	private fun getManga() = SManga.create().apply {
		title = name
		artist = CREATOR
		author = CREATOR
		status = SManga.ONGOING
		initialized = true
		// Image and description from: https://wegotogethercomic.com/about/
		thumbnail_url = "$baseUrl/wp-content/uploads/2023/10/wegotogether_banner.png"
		description = "we go together is a surreal slice-of-life webcomic by Pim about friendship and stories.\n" +
			"it updates every mon/wed/fri at 12pm pacific time."
		url = "/comic"
	}

	override fun fetchPopularManga(page: Int): Observable<MangasPage> =
		Observable.just(MangasPage(listOf(getManga()), hasNextPage = false))

	override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
		name.split(" ").forEach {
			if (query.contains(it, true)) {
				return fetchPopularManga(page)
			}
		}
		return Observable.just(MangasPage(emptyList(), hasNextPage = false))
	}

	override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
		Observable.just(getManga())

	override fun chapterListParse(response: Response): List<SChapter> {
		val chapters = mutableListOf<SChapter>()

		val archiveUrl = "$baseUrl/archive/"
		val comicsDoc = client.newCall(GET(archiveUrl, headers)).execute().asJsoup()
		val comics = comicsDoc.select("#content .post-content > div.entry > p")
		for (element in comics) {
			val link = element.selectFirst("a") ?: continue // this paragraph is not a comic link
			val comicTitle = element.ownText() + " " + link.ownText()
			Log.i("TacLog", "Comic Title: $comicTitle")

			val comicUrl = link.attr("href")
			val chapDoc = client.newCall(GET(comicUrl, headers)).execute().asJsoup()
			val linkToComicChaptersList = chapDoc.selectFirst(".post-info .comic-chapter > a") ?: continue
			val comicChaptersUrl = linkToComicChaptersList.attr("href")
			Log.i("TacLog", "Comic Chapters URL: $comicChaptersUrl")

			val comicChaptersDoc = client.newCall(GET(comicChaptersUrl, headers)).execute().asJsoup()
			val comicChapters = comicChaptersDoc.selectFirst("#content > .archiveresults") ?: continue
			val amountOfChapters = comicChapters.ownText().split(" ").first().toIntOrNull() ?: continue
			Log.i("TacLog", "Amount of Chapters: $amountOfChapters")

			//TODO: Get proper date
			//TODO: Get better sorting
			if (amountOfChapters == 1) {
				Log.i("TacLog", "Chapter Title: $comicTitle ($comicUrl)")
				chapters.add(
					SChapter.create().apply {
						setUrlWithoutDomain(comicUrl)
						name = comicTitle
					},
				)
			} else {
				for (i in amountOfChapters downTo 1) {
					val chapterPageUrl = "$comicChaptersUrl/page/$i/"
					val chapterDoc = client.newCall(GET(chapterPageUrl, headers)).execute().asJsoup()

					val chapterDate = chapterDoc.selectFirst(".archivecomicthumbwrap .archivecomicthumbdate")?.ownText() ?: continue
					val chapterLink = chapterDoc.selectFirst(".archivecomicthumbwrap a")?.attr("href") ?: continue
					val chapterTitle = "$comicTitle ($i)"
					Log.i("TacLog", "Chapter Title: $chapterTitle ($chapterDate) ($chapterLink)")
					chapters.add(
						SChapter.create().apply {
							setUrlWithoutDomain(chapterLink)
							name = chapterTitle
						},
					)
				}
			}
			Log.i("TacLog", "----------------")
		}

		return chapters
	}

	private fun parseDate(dateStr: String): Long {
		return try {
			dateFormat.parse(dateStr)!!.time
		} catch (_: ParseException) {
			0L
		}
	}

	private val dateFormat by lazy {
		SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
	}

	override fun pageListParse(response: Response): List<Page> {
		val doc = response.asJsoup()
		val pages = mutableListOf<Page>()

		val images = doc.select("#content .post-content .entry img")
		for (image in images) {
			val imageUrl = image.attr("src")
			pages.add(Page(pages.size, "", imageUrl))
		}

		return pages
	}

	override fun imageUrlParse(response: Response) =
		throw UnsupportedOperationException()

	override fun latestUpdatesParse(response: Response) =
		throw UnsupportedOperationException()

	override fun latestUpdatesRequest(page: Int) =
		throw UnsupportedOperationException()

	override fun mangaDetailsParse(response: Response) =
		throw UnsupportedOperationException()

	override fun popularMangaParse(response: Response) =
		throw UnsupportedOperationException()

	override fun popularMangaRequest(page: Int) =
		throw UnsupportedOperationException()

	override fun searchMangaParse(response: Response) =
		throw UnsupportedOperationException()

	override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
		throw UnsupportedOperationException()

	companion object {
		private const val CREATOR = "Pim"
	}
}

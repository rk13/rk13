#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.freemarker:freemarker:2.3.34")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.12.0")
@file:DependsOn("no.api.freemarker:freemarker-java8:2.1.0")

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import no.api.freemarker.java8.Java8ObjectWrapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

data class Post(val published: LocalDate, val title: String, val link: String)

val FEED_URL = "https://blog.kotov.lv/feed.xml"
val MAX_POSTS = 6

// --- RSS parsing ---

val RSS_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

fun String.toLocalDate(): LocalDate =
    ZonedDateTime.parse(trim(), RSS_DATE_FORMAT).toLocalDate()

fun Node.toPost(): Post {
    val xpath = XPathFactory.newInstance().newXPath()
    return Post(
        published = xpath.evaluate("pubDate", this).toLocalDate(),
        title = xpath.evaluate("title", this).trim(),
        link = xpath.evaluate("link", this).trim()
    )
}

fun parsePosts(xml: String): List<Post> {
    val document = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray()))
    val nodes = XPathFactory.newInstance().newXPath()
        .evaluate("/rss/channel/item", document, XPathConstants.NODESET) as NodeList
    return (0 until minOf(nodes.length, MAX_POSTS)).map { nodes.item(it).toPost() }
}

// --- HTTP ---

fun fetchFeed(): String {
    val request = Request.Builder()
        .url(FEED_URL)
        .addHeader("User-Agent", "Mozilla/5.0")
        .build()
    OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code}: ${response.message}")
        }
        return response.body?.string()
            ?: throw RuntimeException("Empty response from RSS feed")
    }
}

// --- Template ---

val template = Configuration(Configuration.VERSION_2_3_34)
    .apply {
        setDirectoryForTemplateLoading(File("."))
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        fallbackOnNullLoopVariable = false
        objectWrapper = Java8ObjectWrapper(incompatibleImprovements)
    }.getTemplate("template.adoc")

// --- Main ---

val posts = parsePosts(fetchFeed())

File("README.adoc").writer().use { writer ->
    template.process(mapOf("posts" to posts), writer)
}

#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.freemarker:freemarker:2.3.34")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.12.0")
@file:DependsOn("no.api.freemarker:freemarker-java8:2.1.0")


import freemarker.template.*
import no.api.freemarker.java8.Java8ObjectWrapper
import okhttp3.*
import org.w3c.dom.NodeList
import java.io.*
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

val client = OkHttpClient()

fun <T> execute(builder: Request.Builder, extractor: (String?) -> T): T {
    client.newCall(builder.build()).execute().use { response ->
        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code}: ${response.message}")
        }
        val body = response.body?.string()
        return extractor(body)
    }
}

val template = Configuration(Configuration.VERSION_2_3_34)
    .apply {
        setDirectoryForTemplateLoading(File("."))
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        fallbackOnNullLoopVariable = false
        objectWrapper = Java8ObjectWrapper(this.incompatibleImprovements)
    }.getTemplate("template.adoc")

val MAX_POSTS = 6

val posts: List<Post> by lazy {
    fun String.toLocalDate(): LocalDate {
        val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        return ZonedDateTime.parse(this.trim(), formatter).toLocalDate()
    }

    fun org.w3c.dom.Node.toPost(): Post {
        val xpath = XPathFactory.newInstance().newXPath()
        val pubDate = xpath.evaluate("pubDate", this).trim().toLocalDate()
        val title = xpath.evaluate("title", this).trim()
        val link = xpath.evaluate("link", this).trim()
        return Post(pubDate, title, link)
    }

    val extractPosts = { body: String? ->
        val bytes = body?.toByteArray(Charsets.UTF_8)
            ?: throw RuntimeException("Empty response from RSS feed")
        val document = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))
        val xpath = XPathFactory.newInstance().newXPath()
        val nodes = xpath.evaluate("/rss/channel/item", document, XPathConstants.NODESET) as NodeList
        val count = minOf(nodes.length, MAX_POSTS)
        (0 until count).map { nodes.item(it).toPost() }
    }

    val request = Request.Builder()
        .url("https://blog.kotov.lv/feed.xml")
        .addHeader("User-Agent", "Mozilla/5.0")

    execute(request, extractPosts)
}

val root = mapOf(
    "posts" to posts
)

FileWriter("README.adoc").use { writer ->
    template.process(root, writer)
}

data class Post(val published: LocalDate, val title: String, val link: String)

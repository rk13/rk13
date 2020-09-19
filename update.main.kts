#!/usr/bin/env kotlin

@file:Repository("https://jcenter.bintray.com")
@file:DependsOn("org.freemarker:freemarker:2.3.30")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.8.0")
@file:DependsOn("no.api.freemarker:freemarker-java8:2.0.0")
@file:DependsOn("org.yaml:snakeyaml:1.26")
@file:DependsOn("org.apache.commons:commons-text:1.9")
@file:DependsOn("org.json:json:20200518")
@file:DependsOn("org.jsoup:jsoup:1.13.1")


import freemarker.template.*
import no.api.freemarker.java8.Java8ObjectWrapper
import okhttp3.*
import org.apache.commons.text.StringEscapeUtils
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.json.JSONObject
import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.yaml.snakeyaml.Yaml
import java.io.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

val client = OkHttpClient()

fun <T> execute(builder: Request.Builder, extractor: (String?) -> T): T {
    val body = client.newCall(builder.build())
        .execute()
        .body
        ?.string()
    return extractor(body)
}

val template = Configuration(Configuration.VERSION_2_3_29)
    .apply {
        setDirectoryForTemplateLoading(File("."))
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        fallbackOnNullLoopVariable = false
        objectWrapper = Java8ObjectWrapper(this.incompatibleImprovements)
    }.getTemplate("template.adoc")

val posts: List<Post> by lazy {
    fun String.toLocalDate(): LocalDate {
        val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        return LocalDate.parse(this, formatter)
    }

    fun NodeList.toPost() = Post(
        item(5).textContent.toLocalDate(),
        item(1).textContent,
        item(7).textContent
    )

    val extractPosts = { body: String? ->
        val document = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(body?.toByteArray(Charsets.UTF_8)))
        val xpath = XPathFactory.newInstance().newXPath()
        val nodes = xpath.evaluate("/rss/channel/item", document, XPathConstants.NODESET) as NodeList
        (0..2).map { nodes.item(it) }
            .map { (it as Element).childNodes }
            .map { it.toPost() }
    }

    val request = Request.Builder()
        .url("https://blog.kotov.lv/feed.xml")
        .addHeader("User-Agent", "Mozilla/5.0")

    execute(request, extractPosts)
}

val root = mapOf(
    "posts" to posts,
    "timestamp" to System.getenv("TIMESTAMP")
)

template.process(root, FileWriter("README.adoc"))

data class Post(val published: LocalDate, val title: String, val link: String)

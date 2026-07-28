/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.code.review

import kotlinx.coroutines.future.asDeferred
import org.jetbrains.kotlin.code.review.GitCLI.parseDiffText
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

suspend fun main(args: Array<String>) {
    check(args.size <= 3) {
        "Too many arguments. Expected <output> <repoRoot> <baseRev>"
    }

    val output = File(args[0])
    val repoRoot = File(args[1])
    val baseRefString = args[2]

    val gitTree = GitWorkingTree(repoRoot, GitCLI)
    val agent = LocalClaudeAgent.create(gitTree.project)

    val headSha1 = gitTree.findHead()
    val repository = "JetBrains/kotlin"

    val diff = fetchDiffFromGitHub(repository, GitRevision(baseRefString), headSha1)
    val reviewResult = runReview(gitTree.project, diff, agent)
    val text = with(GitHubRenderingContext(repository, headSha1)) {
        render(reviewResult)
    }
    output.writeText(text)

    val outputUrl = output.toURI().toURL()

    reviewResult.firstException?.let { exception ->
        throw Exception(
            "Review (partially) failed. See more details in the generated report:\n$outputUrl",
            exception
        )
    }

    println("Review generated:")
    println(outputUrl)
}

suspend fun fetchDiffFromGitHub(repository: String, from: GitRevision, to: GitSHA1): GitDiff {
    val origin = GitDiff.Origin.GitHub(repository, from, to)
    val text = fetchDiffTextFromGitHub(origin)
    return GitDiff(parseDiffText(text), origin)
}

private suspend fun fetchDiffTextFromGitHub(origin: GitDiff.Origin.GitHub): String {
    val client = HttpClient.newHttpClient()

    val request = HttpRequest.newBuilder()
        .uri(URI.create(origin.rawDiffUrl))
        .GET()
        .build()

    // Send the request synchronously (or use .sendAsync)
    val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).asDeferred().await()
    if (response.statusCode() !in 200..299) {
        throw Exception("Failed to fetch diff from GitHub: ${response.statusCode()} ${response.body()}")
    }

    return response.body()
}

private class GitHubRenderingContext(val repository: String, val sha1: GitSHA1) : RenderingContext {
    override fun codeLink(path: ProjectFilePath, line: Int): String {
        return "[${path.fileName}:$line](https://github.com/$repository/blob/${sha1.sha1}/$path?plain=1#L$line)"
    }

    override fun markdownLink(path: ProjectFilePath, title: String): String {
        return "[$title](https://github.com/$repository/blob/${sha1.sha1}/$path#${slugifyMarkdownTitle(title)})"
    }

    override fun localLink(text: String, title: String): String? {
        // When posting Markdown as a GitHub comment, it is tricky to have a link to a title in the same comment.
        // Let's keep it unsupported for now.
        return null
    }

    override fun describeDiff(origin: GitDiff.Origin): String = when (origin) {
        is GitDiff.Origin.Local ->
            GitDiff.Origin.GitHub(repository, origin.from, sha1).compareMarkdownLink
        is GitDiff.Origin.GitHub ->
            origin.compareMarkdownLink
    }
}

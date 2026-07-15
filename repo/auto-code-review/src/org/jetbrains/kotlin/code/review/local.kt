/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.code.review

import java.io.File

suspend fun main(args: Array<String>) {
    check(args.size <= 3) {
        "Too many arguments. Expected <output> <repoRoot> [<baseRev>]"
    }

    val output = File(args[0])
    val repoRoot = File(args[1])
    val baseRefString = args.getOrNull(2) ?: "cf4e556a02d9c1cb67d19c2422fdae02c743c499" // FIXME

    val gitTree = GitWorkingTree(repoRoot, GitCLI)
    val agent = LocalClaudeAgent.create(gitTree.project)

    val reviewResult = runReview(gitTree, GitRevision(baseRefString), agent)

    val headSha1 = GitCLI.revParse(gitTree, GitRevision("HEAD"))

    val repository = "JetBrains/kotlin" // FIXME
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

private class GitHubRenderingContext(val repository: String, val sha1: GitSHA1) : RenderingContext {
    override fun codeLink(path: ProjectFilePath, line: Int): String {
        return "[${path.fileName}:$line](https://github.com/$repository/blob/${sha1.sha1}/$path?plain=1#L$line)"
    }

    override fun markdownLink(path: ProjectFilePath, title: String): String {
        return "[$title](https://github.com/$repository/blob/${sha1.sha1}/$path#${slugifyMarkdownTitle(title)})"
    }
//
//    override fun localLink(text: String, title: String): String? {
//        // When posting Markdown as a GitHub comment, it is tricky to have a link to a title in the same comment.
//        // Let's keep it unsupported for now.
//        return null
//    }

    override fun describeDiff(origin: GitDiff.Origin): String = when (origin) {
        is GitDiff.Origin.Local ->
            "[${origin.from.sha1}...${sha1.sha1}](https://github.com/$repository/compare/${origin.from.sha1}...${sha1.sha1})"
    }
}

private class LocalRenderingContext(output: File, project: LocalProject) : RenderingContext {
    private val repoRootRelative = project.root.relativeTo(output.parentFile)

    private val ProjectFilePath.pathRelativeToOutput: String
        get() = repoRootRelative.resolve(this.pathFromProjectRoot).invariantSeparatorsPath

    override fun codeLink(path: ProjectFilePath, line: Int): String {
        val url = path.pathRelativeToOutput
        val fileName = path.fileName
        return "[$fileName]($url):$line"
    }

    override fun markdownLink(path: ProjectFilePath, title: String): String {
        val fileUrl = path.pathRelativeToOutput
        val anchor = slugifyMarkdownTitle(title)
        return "[$title]($fileUrl#$anchor)"
    }

    override fun describeDiff(origin: GitDiff.Origin): String {
        return when (origin) {
            is GitDiff.Origin.Local -> "`git diff ${origin.from.sha1}` at `${origin.to.root}`"
        }
    }
}

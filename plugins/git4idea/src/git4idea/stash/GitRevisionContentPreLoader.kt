// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.CharsetToolkit.UTF8_CHARSET
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber.HEAD
import git4idea.GitVcs
import git4idea.changes.GitChangeUtils
import git4idea.commands.GitBinaryHandler
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils.addTextConvParameters
import org.apache.commons.lang.ArrayUtils


private val LOG = logger<GitRevisionContentPreLoader>()
private const val RECORD_SEPARATOR = "\u0001\u0002\u0003"
private const val ITEM_SEPARATOR = "\u0002\u0003\u0001"

class GitRevisionContentPreLoader(val project: Project) {

  fun preload(root: VirtualFile, changes: Collection<Change>) {
    val toPreload = mutableListOf<Info>()
    val head = GitChangeUtils.resolveReference(project, root, "HEAD")
    for (change in changes) {
      val beforeRevision = change.beforeRevision
      if (beforeRevision !is GitContentRevision || beforeRevision.getRevisionNumber() != head) {
        LOG.info("Skipping change $change because of ${beforeRevision?.revisionNumber?.asString()}")
        continue
      }

      toPreload.add(Info(beforeRevision.getFile(), change))
    }
    if (toPreload.isEmpty()) {
      return
    }

    val hashesAndPaths = calcBlobHashesWithPaths(root, toPreload) ?: return

    val h = GitBinaryHandler(project, root, GitCommand.CAT_FILE)
    h.setSilent(true)
    addTextConvParameters(project, h, false)
    h.addParameters("--batch=$RECORD_SEPARATOR%(objectname)$ITEM_SEPARATOR%(objectsize)")
    h.endOptions()
    h.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(
      // we need to pass '<hash> <path>', otherwise --filters parameter doesn't work
      hashesAndPaths.map { "${it.hash} ${it.path}" },
      UTF8_CHARSET))

    val output: ByteArray
    try {
      output = h.run() 
    }
    catch (e: Exception) {
      LOG.error("Couldn't get git cat-file for $hashesAndPaths", e)
      return
    }

    val split = splitOutput(output, hashesAndPaths.map { it.hash }) ?: return

    toPreload.forEachIndexed { index, info ->
      val oldBeforeRevision = info.change.beforeRevision as GitContentRevision
      val content = split[index]
      val cache = ProjectLevelVcsManager.getInstance(project).contentRevisionCache
      cache.putIntoConstantCache(oldBeforeRevision.file, oldBeforeRevision.revisionNumber, GitVcs.getKey(), content)
    }
  }

  private fun calcBlobHashesWithPaths(root: VirtualFile, toPreload: List<Info>): List<HashAndPath>? {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root)!!
    val trees: List<GitIndexUtil.StagedFileOrDirectory>
    trees = GitIndexUtil.listTree(repository, toPreload.map { it.filePath }, HEAD)
    if (trees.size != toPreload.size) {
      LOG.warn("Incorrect number of trees ${trees.size} != ${toPreload.size}")
      return emptyList()
    }

    return trees.map { tree ->
      if (tree !is GitIndexUtil.StagedFile) {
        LOG.warn("Unexpected tree: $tree");
        return null
      }

      val relativePath = VcsFileUtil.relativePath(root, tree.path)
      if (relativePath == null) {
        LOG.error("Unexpected ls-tree output", Attachment("trees.txt", trees.joinToString()))
        return null
      }

      HashAndPath(tree.blobHash, relativePath)
    }
  }

  private fun splitOutput(output: ByteArray,
                          hashes: List<String>): List<ByteArray>? {
    val result = mutableListOf<ByteArray>()
    var currentPosition = 0
    for (hash in hashes) {
      val separatorBytes = "$RECORD_SEPARATOR${hash}$ITEM_SEPARATOR".toByteArray()
      if (!ArrayUtil.startsWith(output, currentPosition, separatorBytes)) {
        LOG.error("Unexpected output for hash $hash at position $currentPosition", Attachment("catfile.txt", String(output)))
        return null
      }

      val eol = '\n'.toByte()
      val eolIndex = ArrayUtils.indexOf(output, eol, currentPosition + separatorBytes.size)
      if (eolIndex < 0) {
        LOG.error("Unexpected output for hash $hash at position $currentPosition", Attachment("catfile.txt", String(output)))
        return null
      }

      val sizeBytes = output.copyOfRange(currentPosition + separatorBytes.size, eolIndex)
      val size: Int
      try {
        size = Integer.parseInt(String(sizeBytes))
      }
      catch (e: NumberFormatException) {
        LOG.error("Couldn't parse size from ${sizeBytes.contentToString()}")
        return null
      }

      val startIndex = eolIndex + 1
      val endIndex = startIndex + size + 1
      if (endIndex > output.size) {
        LOG.error("Unexpected output for hash $hash at position $currentPosition", Attachment("catfile.txt", String(output)))
        return null
      }

      val content = output.copyOfRange(startIndex, endIndex - 1) // -1 because the content is followed by a newline
      result.add(content)
      currentPosition = endIndex
    }

    if (result.size != hashes.size) {
      LOG.error("Invalid git cat-file output for $hashes", Attachment("catfile.txt", String(output)))
      return null
    }
    return result
  }

  private data class HashAndPath(val hash: String, val path: String)

  private data class Info(val filePath: FilePath, val change: Change)

}
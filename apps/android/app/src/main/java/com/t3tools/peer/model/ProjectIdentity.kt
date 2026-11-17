package com.t3tools.peer.model

/**
 * A project's mark, the way the desktop app draws it: a monogram of the project's
 * name and a colour of its own, so two projects never look alike.
 */
data class ProjectIdentity(val monogram: String, val colorIndex: Int) {
  companion object {
    /** How many colours a project can wear. */
    const val COLOR_COUNT = 18

    /**
     * The mark of a project, taken from its name.
     *
     * The monogram is the first letter of the project's first word, followed by the
     * first digit or the last letter of the name. The colour follows from the name
     * too, so the same project always wears the same one.
     */
    fun of(projectName: String): ProjectIdentity {
      val name = projectName.trim()
      return ProjectIdentity(monogram(name), color(name))
    }

    private fun monogram(projectName: String): String {
      val words = projectName.split(WORD_SEPARATORS).filter { it.isNotEmpty() }
      val firstWord = words.firstOrNull() ?: return "PR"
      val glyphs = firstWord.map { it.toString() }
      val first = glyphs.firstOrNull() ?: "P"
      val second =
        glyphs.drop(1).firstOrNull { glyph -> glyph.any { it in '0'..'9' } }
          ?: (if (words.size > 1) words.lastOrNull()?.firstOrNull()?.toString() else null)
          ?: glyphs.lastOrNull()
          ?: first
      return (first + second).uppercase().take(2)
    }

    private fun color(projectName: String): Int {
      val seed = projectName.trim().lowercase().ifEmpty { "project" }
      var index = 0
      for (glyph in seed) {
        index = (index * 31 + glyph.code) % COLOR_COUNT
      }
      return index
    }

    private val WORD_SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
  }
}

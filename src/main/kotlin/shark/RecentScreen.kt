package shark

import java.time.Instant

class RecentScreen(val screen: Screen) {
  private val createdAt = Instant.now()

  override fun hashCode(): Int {
    return screen.title.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    return other is RecentScreen && screen.title == other.screen.title
  }

  fun timeAgo() = createdAt.toTimeAgo()
}

fun Screen.toRecent() = RecentScreen(this)
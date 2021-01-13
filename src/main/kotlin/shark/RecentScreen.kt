package shark

import java.time.Instant

class RecentScreen<T>(val screen: T) {
  private val createdAt = Instant.now()

  fun timeAgo() = createdAt.toTimeAgo()
}
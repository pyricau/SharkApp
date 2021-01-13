package shark

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScreenNavigatorTest {

  @Test fun `navigator starts on first screen`() {
    val navigator = ScreenNavigator("Home", { a, b -> a == b })

    assertThat(navigator.currentScreen).isEqualTo("Home")
  }
}
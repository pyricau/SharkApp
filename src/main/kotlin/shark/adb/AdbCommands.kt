package shark.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import shark.adb.AdbDevice.Offline
import shark.adb.AdbDevice.Online
import java.io.File
import java.util.concurrent.TimeUnit.SECONDS

private val workingDirectory by lazy {
  File(System.getProperty("user.dir"))
}

private val SPACE_PATTERN = Regex("\\s+")

sealed class AdbDevice {
  abstract val serialNumber: String

  data class Online(
    override val serialNumber: String,
    val product: String,
    val model: String,
    val device: String
  ) : AdbDevice()

  data class Offline(override val serialNumber: String) : AdbDevice()
}

suspend fun listDevices(): CommandResult<List<AdbDevice>> {
  println("Calling listDevices")
  return withContext(Dispatchers.IO) {
    with(workingDirectory) {
      when (val output = runCommand("adb", "devices", "-l").apply { println("output of command: $this") }) {
        is CommandResult.Success -> {
          val results = output.value.lines()
            .drop(1)
            .filter { it.isNotBlank() }.map { line ->
              val tokens = SPACE_PATTERN.split(line)
              if (tokens.size < 2) {
                CommandResult.Error(line)
              } else {
                val serialNumber = tokens[0]
                when (tokens[1]) {
                  "offline" -> {
                    CommandResult.Success(Offline(serialNumber))
                  }
                  "device" -> {
                    val properties = tokens.drop(2)
                      .map { token -> token.substringBefore(":") to token.substringAfter(":") }
                      .toMap()
                    CommandResult.Success(
                      Online(
                        serialNumber,
                        product = properties.getValue("product"),
                        model = properties.getValue("model"),
                        device = properties.getValue("device")
                      )
                    )
                  }
                  else -> {
                    CommandResult.Error(line)
                  }
                }
              }
            }
          if (results.isEmpty()) {
            CommandResult.Success(emptyList())
          } else if (results.any { it is CommandResult.Success }) {
            val devices = results.filterIsInstance(CommandResult.Success::class.java)
              .map { it.value as AdbDevice }
            CommandResult.Success(devices)
          } else {
            CommandResult.Error(results.joinToString("\n") { (it as CommandResult.Error).errorMessage })
          }
        }
        is CommandResult.Error -> CommandResult.Error(output.errorMessage)
      }
    }.apply { println("Result: $this") }
  }
}

sealed class CommandResult<T> {
  data class Success<T>(val value: T) : CommandResult<T>()

  data class Error<T>(val errorMessage: String) : CommandResult<T>()
}

fun File.runCommand(
  vararg arguments: String,
): CommandResult<String> {
  val process = ProcessBuilder(*arguments)
    .directory(this)
    .start()
    .also { it.waitFor(10, SECONDS) }

  // See https://github.com/square/leakcanary/issues/1711
  // On Windows, the process doesn't always exit; calling to readText() makes it finish, so
  // we're reading the output before checking for the exit value
  val output = process.inputStream.bufferedReader().readText()
  if (process.exitValue() != 0) {
    val command = arguments.joinToString(" ")
    val errorOutput = process.errorStream.bufferedReader()
      .readText()
    return CommandResult.Error("Failed command: '$command', error output:\n---\n$errorOutput---")
  }
  return CommandResult.Success(output)
}
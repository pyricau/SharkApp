package shark.adb

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import shark.adb.AdbDevice.Offline
import shark.adb.AdbDevice.Online
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

class AndroidProcess(val user: String, val pid: String, val name: String)

suspend fun listDevices(): CommandResult<List<AdbDevice>> {
  return withContext(Dispatchers.IO) {
    with(workingDirectory) {
      when (val output = runCommand("adb", "devices", "-l")) {
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
          when {
            results.isEmpty() -> {
              CommandResult.Success(emptyList())
            }
            results.any { it is CommandResult.Success } -> {
              val devices = results.filterIsInstance(CommandResult.Success::class.java)
                .map { it.value as AdbDevice }
              CommandResult.Success(devices)
            }
            else -> {
              CommandResult.Error(results.joinToString("\n") { (it as CommandResult.Error).errorMessage })
            }
          }
        }
        is CommandResult.Error -> CommandResult.Error(output.errorMessage)
      }
    }
  }
}

suspend fun AdbDevice.listProcesses(): CommandResult<List<AndroidProcess>> {
  return withContext(Dispatchers.IO) {
    with(workingDirectory) {
      when (val output = runCommand("adb", "-s", serialNumber, "shell", "ps")) {
        is CommandResult.Success -> {
          val matchingProcesses = output.value.lines()
            .drop(1)
            .map {
              SPACE_PATTERN.split(it)
            }.filter { columns ->
              // E.g. u0_a14
              columns.size >= 9 && columns[0].startsWith("u") && '_' in columns[0]
            }.map { columns ->
              AndroidProcess(
                user = columns[0],
                pid = columns[1],
                name = columns[8]
              )
            }
          CommandResult.Success(matchingProcesses)
        }
        is CommandResult.Error -> CommandResult.Error(output.errorMessage)
      }
    }
  }
}

suspend fun AdbDevice.dumpHeap(process: AndroidProcess, progress: MutableState<String>): CommandResult<File> {
  return withContext(Dispatchers.IO) {
    with(workingDirectory) {
      val heapDumpFileName =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS'-${process.name}.hprof'", Locale.US).format(
          Date()
        )

      val heapDumpDevicePath = "/data/local/tmp/$heapDumpFileName"

      progress.value = "Dumping heap to $heapDumpDevicePath"
      val dumpResult = runCommand(
         "adb", "-s", serialNumber, "shell", "am", "dumpheap", process.pid,
        heapDumpDevicePath
      )
      if (dumpResult is CommandResult.Error) {
        CommandResult.Error(dumpResult.errorMessage)
      } else {
        // Dump heap takes time but adb returns immediately.
        delay(3000)

        progress.value = "Pulling $heapDumpDevicePath to $this"
        val pullResult =
          runCommand("adb", "-s", serialNumber, "pull", heapDumpDevicePath)

        progress.value = "Deleting $heapDumpDevicePath on device"

        runCommand("adb", "-s", serialNumber, "shell", "rm", heapDumpDevicePath)
        if (pullResult is CommandResult.Error) {
          CommandResult.Error(pullResult.errorMessage)
        } else {
          val heapDumpFile = File(workingDirectory, heapDumpFileName)
          CommandResult.Success(heapDumpFile)
        }
      }
    }
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
    return CommandResult.Error("Failed command:\n\n$ $command\n\nOutput:\n\n---\n$errorOutput---")
  }
  return CommandResult.Success(output)
}
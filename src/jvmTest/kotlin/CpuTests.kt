import com.sorrykaputt.eightyeighty.Cpu
import com.sorrykaputt.eightyeighty.FnReadPort
import com.sorrykaputt.eightyeighty.FnWritePort
import com.sorrykaputt.eightyeighty.Memory
import java.util.zip.GZIPInputStream
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class CpuTests {

    @Test
    fun test8080PRE() {
        runTest(
            "roms/cpu_tests/8080PRE.COM"
            /* roms/cpu_tests/8080PRE.TRACE" */
        )
    }

    @Test
    fun testTST8080() {
        runTest(
            "roms/cpu_tests/TST8080.COM"
            /* "roms/cpu_tests/TST8080.TRACE" */
        )
    }

    @Test
    fun testCPUTEST() {
        runTest(
            "roms/cpu_tests/CPUTEST.COM"
            /* "roms/cpu_tests/CPUTEST.TRACE" */
        )
    }

    @Test
    fun test8080EXM() {
        runTest(
            "roms/cpu_tests/8080EXM.COM"
            /* "roms/cpu_tests/8080EXM.TRACE.gz" */
        )
    }

    private fun runTest(testRomPath: String, testTracePath: String? = null) {
        val logTrace = false

        val binaryPath = Path(testRomPath)
        check(binaryPath.exists())

        val expectedTraceLog =
            testTracePath?.let { path ->
                if (path.endsWith(".gz")) {
                    GZIPInputStream(Path(path).inputStream()).bufferedReader()
                } else {
                    Path(path).bufferedReader()
                }
            }

        val mem = Memory()
        mem.load(binaryPath.readBytes(), offset = 0x100)

        var testCompleted = false

        val writePort: FnWritePort = { cpu, port, value ->
            if (port == 0) {
                testCompleted = true
            } else if (port == 1 && cpu.C == 9) {
                // BDOS function 9 (C_WRITESTR) - Output string
                // https://www.seasip.info/Cpm/bdos.html
                val address = cpu.DE
                var i = 0
                while (true) {
                    val char = mem.readByte((address + i))
                    if (char == '$'.code) {
                        break
                    }

                    print(char.toChar())
                    i++
                }
            } else if (port == 1 && cpu.C == 2) {
                print(cpu.E.toChar())
            } else {
                error("Unimplemented write port $port, operation ${cpu.C}")
            }
        }

        val readPort: FnReadPort = { cpu, port -> error("Unimplemented read port $port") }

        val cpu = Cpu(writePort = writePort, readPort = readPort)

        // set program entry point for cp/m programs
        cpu.PC = 0x100

        // https://github.com/superzazu/8080/blob/274ffd700b81baabea99b0963bc1260b67132185/i8080_tests.c#L91
        mem.writeByte(0x0000, 0xD3)
        mem.writeByte(0x0001, 0x00)

        // https://old.reddit.com/r/EmuDev/comments/11y7cht/comment/jd9rjtl
        mem.writeByte(0x0005, 0xD3)
        mem.writeByte(0x0006, 0x01)
        mem.writeByte(0x0007, 0xC9)

        val traceBuffer = mutableListOf<String>()

        fun addTrace(trace: String) {
            if (traceBuffer.size == 5) {
                traceBuffer.removeFirst()
            }
            traceBuffer.add(trace)
        }

        try {
            while (!testCompleted) {
                cpu.cycle(mem)
                val actualTrace = cpu.trace!!
                addTrace(actualTrace)

                if (expectedTraceLog != null) {
                    var expectedTrace: String? = null

                    while (true) {
                        val line = expectedTraceLog.readLine()
                        if (line == null) {
                            error("End of expected trace log")
                        }

                        val startIndex = line.indexOf("PC:")

                        if (startIndex >= 0) {
                            expectedTrace = line.substring(startIndex)
                            break
                        }
                    }

                    val actualTraceSegment = actualTrace.split(" ###")[0]

                    if (actualTraceSegment == expectedTrace) {
                        if (logTrace) {
                            println(actualTrace)
                        }
                    } else {
                        println("**************** TRACE ****************")
                        traceBuffer.forEach { println(it) }

                        println("**************** FAIL ****************")
                        println("FAIL:          $actualTrace")
                        println("EXPECTED:      $expectedTrace")
                    }

                    assertEquals(expectedTrace, actualTraceSegment)
                } else {
                    if (logTrace) {
                        println(actualTrace)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.sorrykaputt.eightyeighty

typealias FnWritePort = (Cpu, Int, Int) -> Unit

typealias FnReadPort = (Cpu, Int) -> Int

@OptIn(ExperimentalStdlibApi::class)
private val REGISTER_HEX_FORMAT = HexFormat {
    upperCase = true
    number {
        removeLeadingZeros = true
        minLength = 4
    }
}

@OptIn(ExperimentalStdlibApi::class)
private val OPCODE_HEX_FORMAT = HexFormat {
    upperCase = true
    number {
        removeLeadingZeros = true
        minLength = 2
    }
}

@OptIn(ExperimentalStdlibApi::class)
class Cpu(private val writePort: FnWritePort, private val readPort: FnReadPort) {
    var trace: String? = null
        private set

    var PC: Int = 0
        set(value) {
            field = value and 0xFFFF
        }
        get() {
            return field
        }

    private var SP: Int = 0
        set(value) {
            field = value and 0xFFFF
        }
        get() = field

    var interruptsEnabled = false

    // region Registers

    var A: Int = 0
        set(value) {
            field = value
        }

    var BC: Int = 0
    var DE: Int = 0
    var HL: Int = 0

    var B: Int
        get() = (BC.and(0xFF00) shr 8)
        set(v) {
            BC = ((BC.and(0x00FF)) or (v and 0xFF shl 8))
        }

    var C: Int
        get() = BC.and(0xFF)
        set(v) {
            BC = ((BC.and(0xFF00)) or (v and 0xFF))
        }

    var D: Int
        get() = (DE.and(0xFF00) shr 8)
        set(v) {
            DE = (((DE.and(0x00FF)) or (v and 0xFF shl 8)))
        }

    var E: Int
        get() = (DE.and(0xFF))
        set(v) {
            DE = ((DE.and(0xFF00)) or (v and 0xFF))
        }

    var H: Int
        get() = (HL.and(0xFF00) shr 8)
        set(v) {
            HL = (((HL.and(0x00FF)) or (v and 0xFF shl 8)))
        }

    var L: Int
        get() = HL.and(0xFF)
        set(v) {
            HL = ((HL.and(0xFF00)) or (v and 0xFF))
        }

    private val AF: Int
        get() = (A shl 8 or PSW)

    private val PSW: Int
        get() =
            (this.FLAG_C or
                0b10 or
                (FLAG_P shl 2) or
                (this.FLAG_AC shl 4) or
                (this.FLAG_Z shl 6) or
                (this.FLAG_S shl 7))

    // endregion

    // region State

    var numCycles: Long = 0 // FixMe: prevent overflow
        private set
    val interrupts = ArrayDeque<Int>()

    // endregion

    // region Flags

    private var FLAG_Z: Int = 0 // Zero Flag
    private var FLAG_S: Int = 0 // Sign Flag
    private var FLAG_P: Int = 0 // Parity Flag
    private var FLAG_C: Int = 0 // Carry Flag
    private var FLAG_AC: Int = 0 // Auxiliary Carry Flag

    // endregion

    fun interrupt(opcode: Int) {
        if (this.interruptsEnabled) {
            interrupts.add(opcode)
        }
    }

    fun cycle(mem: Memory): Int {
        val opcode =
            if (this.interrupts.isNotEmpty()) {
                interruptsEnabled = false
                this.interrupts.removeFirst()
            } else {
                val o = mem.readByte(this.PC)
                this.trace = traceLog(mem)
                incrementPC()
                o
            }

        val cycles =
            when (opcode) {
                0x00,
                0x10,
                0x20,
                0x30,
                0x08,
                0x18,
                0x28,
                0x38 -> { // NOP
                    4
                }

                // region Data Transfer Group

                0x47,
                in 0x40..0x45,
                0x4F,
                in 0x48..0x4D,
                0x57,
                in 0x50..0x55,
                0x5F,
                in 0x58..0x5D,
                0x67,
                in 0x60..0x65,
                0x6F,
                in 0x68..0x6D,
                0x7F,
                in 0x78..0x7D -> { // MOV r1, r2
                    setDestinationRegister(opcode, readSourceRegister(opcode))
                    5
                }
                0x7E,
                0x46,
                0x4E,
                0x56,
                0x5E,
                0x66,
                0x6E -> { // MOV r, M
                    setDestinationRegister(opcode, mem.readByte(this.HL))
                    7
                }
                0x77,
                in 0x70..0x75 -> { // MOV M, r
                    mem.writeByte(this.HL, readSourceRegister(opcode))
                    7
                }
                0x3E,
                0x06,
                0x0E,
                0x16,
                0x1E,
                0x26,
                0x2E -> { // MVI r, data
                    val value = mem.readByte(this.PC)
                    incrementPC()
                    setDestinationRegister(opcode, value)
                    7
                }
                0x36 -> { // MVI M, data
                    val value = mem.readByte(this.PC)
                    incrementPC()
                    mem.writeByte(this.HL, value)
                    10
                }
                0x01,
                0x11,
                0x21,
                0x31 -> { // LXI rp
                    val value = mem.readWord(this.PC)
                    incrementPC(2)
                    setRegisterPair(opcode, value)
                    10
                }
                0x3A -> { // LDA addr
                    val address = mem.readWord(this.PC)
                    incrementPC(2)
                    this.A = mem.readByte(address)
                    13
                }
                0x32 -> { // STA addr
                    val address = mem.readWord(this.PC)
                    incrementPC(2)
                    mem.writeByte(address, this.A)
                    13
                }
                0x2A -> { // LHLD addr
                    val address = mem.readWord(this.PC)
                    incrementPC(2)
                    this.HL = mem.readWord(address)
                    16
                }
                0x22 -> { // SHLD addr
                    val address = mem.readWord(this.PC)
                    incrementPC(2)
                    mem.writeWord(address, this.HL)
                    16
                }
                0x0A,
                0x1A -> { // LDAX rp (BC or DE allowed)
                    this.A = mem.readByte(readRegisterPair(opcode))
                    7
                }
                0x02,
                0x12 -> { // STAX rp (only BC and DE allowed)
                    mem.writeByte(readRegisterPair(opcode), this.A)
                    7
                }
                0xEB -> { // XCHG
                    val tmp = this.HL
                    this.HL = this.DE
                    this.DE = tmp
                    4
                }

                // endregion

                // region Arithmetic Group

                0x87,
                in 0x80..0x85 -> { // ADD r
                    this.A = add(readSourceRegister(opcode), 0)
                    4
                }
                0x86 -> { // ADD M
                    this.A = add(mem.readByte(this.HL), 0)
                    7
                }
                0xC6 -> { // ADI data
                    val value = mem.readByte(this.PC)
                    this.A = add(value, 0)
                    incrementPC()
                    7
                }
                0x8F,
                in 0x88..0x8D -> { // ADC r
                    this.A = add(readSourceRegister(opcode), this.FLAG_C)
                    4
                }
                0x8E -> { // ADC M
                    val value = mem.readByte(this.HL)
                    this.A = add(value, this.FLAG_C)
                    7
                }
                0xCE -> { // ACI data
                    val value = mem.readByte(this.PC)
                    this.A = add(value, this.FLAG_C)
                    incrementPC()
                    7
                }
                0x97,
                in 0x90..0x95 -> { // SUB r
                    this.A = subtract(readSourceRegister(opcode), 0)
                    4
                }
                0x96 -> { // SUB M
                    this.A = subtract(mem.readByte(this.HL), 0)
                    7
                }
                0xD6 -> { // SUI data
                    val value = mem.readByte(this.PC)
                    this.A = subtract(value, 0)
                    incrementPC()
                    7
                }
                0x9F,
                in 0x98..0x9D -> { // SBB r
                    this.A = subtract(readSourceRegister(opcode), this.FLAG_C)
                    4
                }
                0x9E -> { // SBB M
                    val value = mem.readByte(this.HL)
                    this.A = subtract(value, this.FLAG_C)
                    7
                }
                0xDE -> { // SBI data
                    val value = mem.readByte(this.PC)
                    this.A = subtract(value, this.FLAG_C)
                    incrementPC()
                    7
                }
                0x3C,
                0x04,
                0x0C,
                0x14,
                0x1C,
                0x24,
                0x2C -> { // INR r
                    val value = readDestinationRegister(opcode)
                    val newValue = value + 1

                    setDestinationRegister(opcode, newValue)
                    calculateZSPFlags(newValue)
                    this.FLAG_AC = calculateHalfCarryForAddition(value, 1, 0)
                    5
                }
                0x34 -> { // INR M
                    val value = mem.readByte(this.HL)
                    val newValue = value + 1
                    mem.writeByte(this.HL, newValue)
                    calculateZSPFlags(newValue)
                    this.FLAG_AC = calculateHalfCarryForAddition(value, 1, 0)
                    10
                }
                0x03,
                0x13,
                0x23,
                0x33 -> { // INX rp
                    setRegisterPair(opcode, (readRegisterPair(opcode) + 1))
                    5
                }
                0x3D,
                0x05,
                0x0D,
                0x15,
                0x1D,
                0x25,
                0x2D -> { // DCR r
                    val value = readDestinationRegister(opcode)
                    val newValue = value - 1

                    setDestinationRegister(opcode, newValue)
                    calculateZSPFlags(newValue)
                    this.FLAG_AC = calculateHalfCarryForSubtraction(value, 1, 0)
                    5
                }
                0x35 -> { // DCR M
                    val value = mem.readByte(this.HL)

                    val newValue = value - 1
                    mem.writeByte(this.HL, (newValue and 0xFF))
                    calculateZSPFlags(newValue)
                    this.FLAG_AC = calculateHalfCarryForSubtraction(value, 1, 0)

                    10
                }
                0x0B,
                0x1B,
                0x2B,
                0x3B -> { // DCX rp
                    setRegisterPair(opcode, (readRegisterPair(opcode) - 1))
                    5
                }
                0x09,
                0x19,
                0x29,
                0x39 -> { // DAD rp
                    val rp = readRegisterPair(opcode)
                    val result = this.HL + rp

                    this.FLAG_C = if (result > 0xFFFF) 1 else 0
                    this.HL = (result and 0xFFFF)
                    10
                }
                0x27 -> { // DAA
                    val lsb = this.A and 0x0F
                    var correction = 0

                    if (lsb > 9 || this.FLAG_AC > 0) {
                        correction += 0x06
                    }

                    val msb = this.A and 0xF0
                    var c = FLAG_C

                    if (msb > 0x90 || FLAG_C > 0 || (msb >= 0x90 && lsb > 9)) {
                        correction += 0x60
                        c = 1
                    }

                    this.A = add(correction, 0)
                    this.FLAG_C = c
                    4
                }

                // endregion

                // region Branch Group

                0xC3 -> { // JMP
                    val dst = mem.readWord(this.PC)
                    incrementPC(2)
                    this.PC = dst
                    10
                }
                0xC2,
                0xCA,
                0xD2,
                0xDA,
                0xE2,
                0xEA,
                0xF2,
                0xFA -> { // Jcondition
                    val dst = mem.readWord(this.PC)
                    incrementPC(2)

                    if (isConditionMet(opcode)) {
                        this.PC = dst
                    }

                    10
                }
                0xC4,
                0xCC,
                0xD4,
                0xDC,
                0xE4,
                0xEC,
                0xF4,
                0xFC -> { // Ccondition
                    val address = mem.readWord(this.PC)
                    incrementPC(2)

                    if (isConditionMet(opcode)) {
                        decrementSP(2)
                        mem.writeWord(this.SP, this.PC)
                        this.PC = address
                        17
                    } else {
                        11
                    }
                }
                0xC7,
                0xCF,
                0xD7,
                0xDF,
                0xE7,
                0xEF,
                0xF7,
                0xFF -> { // RST n
                    decrementSP(2)
                    mem.writeWord(this.SP, this.PC)
                    val n = (opcode and 0b00111000)
                    this.PC = n

                    11
                }
                0xCD -> { // CALL
                    val address = mem.readWord(this.PC)
                    incrementPC(2)

                    // Push return address to stack
                    decrementSP(2)
                    mem.writeWord(this.SP, this.PC)

                    this.PC = address

                    17
                }
                0xC9 -> { // RET
                    this.PC = mem.readWord(this.SP)
                    incrementSP(2)
                    10
                }

                // Rcondition

                0xC0,
                0xC8,
                0xD0,
                0xD8,
                0xE0,
                0xE8,
                0xF0,
                0xF8 -> {
                    if (isConditionMet(opcode)) {
                        this.PC = mem.readWord(this.SP)
                        incrementSP(2)
                        11
                    } else {
                        5
                    }
                }
                0xE9 -> { // PCHL
                    this.PC = this.HL
                    5
                }
                // endregion

                // region Logical Group

                0xA7,
                in 0xA0..0xA5 -> { // ANA r
                    val value = readSourceRegister(opcode)
                    this.FLAG_AC = (this.A or (value) and 0x08) shr 3

                    this.A = this.A and value
                    calculateZSPFlags(this.A)
                    this.FLAG_C = 0
                    4
                }
                0xA6 -> { // ANA M
                    val value = mem.readByte(this.HL)
                    this.FLAG_AC = (this.A or (value) and 0x08) shr 3

                    this.A = this.A and value
                    calculateZSPFlags(this.A)
                    this.FLAG_C = 0
                    7
                }
                0xE6 -> { // ANI data
                    val value = mem.readByte(this.PC)
                    incrementPC()

                    /*
                        The 8080/8085 Assembly Language Programming Manual says on page 1-12 (PDF page 22):
                        The auxiliary carry flag is affected by all add, subtract, increment, decrement, compare, and all logical AND, OR, and exclusive OR instructions. (See the descriptions of these instructions in Chapter 3.) There is some difference in the handling of the auxiliary carry flag by the logical AND instructions in the 8080 processor and the 8085 processor. The 8085 logical AND instructions always set the auxiliary flag ON. The 8080 logical AND instructions set the flag to reflect the logical OR of bit 3 of the values involved in the AND operation.
                        http://bitsavers.org/components/intel/MCS80/9800301D_8080_8085_Assembly_Language_Programming_Manual_May81.pdf
                    */
                    this.FLAG_AC = (this.A or (value) and 0x08) shr 3

                    this.A = this.A and value

                    calculateZSPFlags(this.A)
                    this.FLAG_C = 0

                    7
                }
                0xAF,
                in 0xA8..0xAD -> { // XRA r
                    val value = this.A xor readSourceRegister(opcode)
                    calculateZSPFlags(value)
                    this.A = value
                    this.FLAG_C = 0
                    this.FLAG_AC = 0
                    4
                }
                0xAE -> { // XRA M
                    val value = this.A xor mem.readByte(this.HL)
                    calculateZSPFlags(value)
                    this.A = value
                    this.FLAG_C = 0
                    this.FLAG_AC = 0
                    7
                }
                0xEE -> { // XRI data
                    val value = mem.readByte(this.PC)
                    incrementPC()

                    this.A = this.A xor value

                    calculateZSPFlags(this.A)
                    this.FLAG_C = 0
                    this.FLAG_AC = 0

                    7
                }
                0xB7,
                in 0xB0..0xB5 -> { // ORA r
                    val value = this.A or readSourceRegister(opcode)
                    calculateZSPFlags(value)
                    this.A = value
                    this.FLAG_C = 0
                    this.FLAG_AC = 0
                    4
                }
                0xB6 -> { // ORA M
                    val value = this.A or mem.readByte(this.HL)
                    calculateZSPFlags(value)
                    this.A = value
                    this.FLAG_C = 0
                    this.FLAG_AC = 0
                    7
                }
                0xF6 -> { // ORI data
                    val value = mem.readByte(this.PC)
                    incrementPC()

                    this.A = this.A or value

                    calculateZSPFlags(this.A)
                    this.FLAG_C = 0
                    this.FLAG_AC = 0

                    7
                }
                0xBF,
                in 0xB8..0xBD -> { // CMP r
                    subtract(readSourceRegister(opcode), 0)
                    4
                }
                0xBE -> { // CMP M
                    subtract(mem.readByte(this.HL), 0)
                    7
                }
                0xFE -> { // CPI data
                    val value = mem.readByte(this.PC)
                    incrementPC()
                    subtract(value, 0)
                    7
                }
                0x07 -> { // RLC
                    this.FLAG_C = (this.A and 0b10000000) shr 7
                    this.A = (((this.A shl 1) or this.FLAG_C) and 0xFF)
                    4
                }
                0x0F -> { // RRC
                    this.FLAG_C = this.A and 1
                    this.A = (((this.A shr 1) or (this.FLAG_C shl 7)) and 0xFF)
                    4
                }
                0x17 -> { // RAL
                    val c = this.FLAG_C
                    this.FLAG_C = this.A shr 7
                    this.A = (((this.A shl 1) or c) and 0xFF)
                    4
                }
                0x1F -> { // RAR
                    val newC = this.A and 1
                    this.A = (((this.A shr 1) or (this.FLAG_C shl 7)) and 0xFF)
                    this.FLAG_C = newC
                    4
                }
                0x2F -> { // CMA
                    this.A = this.A.inv()
                    4
                }
                0x3F -> { // CMC
                    this.FLAG_C = this.FLAG_C xor 1
                    4
                }
                0x37 -> { // STC
                    this.FLAG_C = 1
                    4
                }

                // endregion

                // region Stack, I/0, and Machine Control Group

                0xC5,
                0xD5,
                0xE5 -> { // PUSH rp
                    decrementSP(2)
                    mem.writeWord(this.SP, readRegisterPair(opcode))
                    11
                }
                0xF5 -> { // PUSH PSW
                    decrementSP()
                    mem.writeByte(this.SP, this.A)

                    val psw = PSW

                    decrementSP()
                    mem.writeByte(this.SP, psw)

                    11
                }
                0xC1,
                0xD1,
                0xE1 -> { // POP rp
                    setRegisterPair(opcode, mem.readWord(this.SP))
                    incrementSP(2)
                    10
                }
                0xDB -> { // IN port
                    val port = mem.readByte(this.PC)
                    this.A = readPort(this, port)
                    incrementPC()
                    10
                }
                0xD3 -> { // OUT port
                    val port = mem.readByte(this.PC)
                    this.writePort(this, port, this.A)
                    incrementPC()
                    10
                }
                0xFB -> { // EI
                    interruptsEnabled = true
                    4
                }
                0xF3 -> { // DI
                    interruptsEnabled = false
                    4
                }
                0xF1 -> { // POP PSW
                    val psw = mem.readByte(this.SP)
                    this.FLAG_C = if ((psw and 1) > 0) 1 else 0
                    this.FLAG_P = if ((psw and (1 shl 2)) > 0) 1 else 0
                    this.FLAG_AC = if ((psw and (1 shl 4)) > 0) 1 else 0
                    this.FLAG_Z = if ((psw and (1 shl 6)) > 0) 1 else 0
                    this.FLAG_S = if ((psw and (1 shl 7)) > 0) 1 else 0

                    incrementSP()
                    this.A = mem.readByte(this.SP)

                    incrementSP()
                    10
                }
                0xE3 -> { // XTHL
                    val sp = mem.readWord(this.SP)
                    mem.writeWord(this.SP, this.HL)
                    this.HL = sp
                    18
                }
                0xF9 -> { // SPHL
                    this.SP = this.HL
                    5
                }
                0x76 -> { // HLT
                    7
                }

                // endregion

                else ->
                    error(
                        "Unsupported opcode: ${opcode.toHexString()} - ${opcode.toString(radix = 2)} ${DISASSEMBLE_TABLE[opcode]}"
                    )
            }

        if (numCycles + cycles < 0) {
            // overflow, simulate wrap around
            numCycles = (Long.MAX_VALUE - numCycles) + cycles
        } else {
            numCycles += cycles
        }

        return cycles
    }

    // region State Updates

    private fun incrementPC(amount: Int = 1) {
        this.PC = (this.PC + amount)
    }

    private fun incrementSP(amount: Int = 1) {
        this.SP = (this.SP + amount)
    }

    private fun decrementSP(amount: Int = 1) {
        this.SP = (this.SP - amount)
    }

    private fun setRegisterPair(encodedRegisterPair: Int, value: Int) {
        when ((encodedRegisterPair and 0b00110000) shr 4) {
            0b00 -> this.BC = value
            0b01 -> this.DE = value
            0b10 -> this.HL = value
            0b11 -> this.SP = value
            else ->
                error(
                    "Unable to decode register pair from byte value: ${encodedRegisterPair.toHexString()} - ${
                        encodedRegisterPair.toString(
                            radix = 2
                        )
                    }"
                )
        }
    }

    private fun readRegisterPair(encodedRegisterPair: Int) =
        when ((encodedRegisterPair and 0b00110000) shr 4) {
            0b00 -> this.BC
            0b01 -> this.DE
            0b10 -> this.HL
            0b11 -> this.SP
            else ->
                error(
                    "Unable to decode register pair from byte value: ${encodedRegisterPair.toHexString()} - ${
                        encodedRegisterPair.toString(
                            radix = 2
                        )
                    }"
                )
        }

    private fun readDestinationRegister(encodedDestinationRegister: Int): Int =
        when (encodedDestinationRegister and 0b111000) {
            0b111000 -> this.A
            0b000000 -> this.B
            0b001000 -> this.C
            0b010000 -> this.D
            0b011000 -> this.E
            0b100000 -> this.H
            0b101000 -> this.L
            else ->
                error(
                    "Unable to decode destination register from byte value: ${encodedDestinationRegister.toHexString()} - ${
                        encodedDestinationRegister.toString(
                            radix = 2
                        )
                    }"
                )
        }

    private fun setDestinationRegister(encodedDestinationRegister: Int, value: Int) {
        when (encodedDestinationRegister and 0b00111000) {
            0b00111000 -> this.A = value
            0b00000000 -> this.B = value
            0b00001000 -> this.C = value
            0b00010000 -> this.D = value
            0b00011000 -> this.E = value
            0b00100000 -> this.H = value
            0b00101000 -> this.L = value
            else ->
                error(
                    "Unable to decode destination register from byte value: ${encodedDestinationRegister.toHexString()} - ${
                        encodedDestinationRegister.toString(
                            radix = 2
                        )
                    }"
                )
        }
    }

    private fun readSourceRegister(encodedSourceRegister: Int): Int =
        when (encodedSourceRegister and 0b111) {
            0b111 -> this.A
            0b000 -> this.B
            0b001 -> this.C
            0b010 -> this.D
            0b011 -> this.E
            0b100 -> this.H
            0b101 -> this.L
            else ->
                error(
                    "Unable to decode source register from byte value: ${encodedSourceRegister.toHexString()} - ${
                        encodedSourceRegister.toString(
                            radix = 2
                        )
                    }"
                )
        }

    private fun isConditionMet(value: Int) =
        when (value and 0b111000) {
            0b000000 -> this.FLAG_Z == 0
            0b001000 -> this.FLAG_Z == 1
            0b010000 -> this.FLAG_C == 0
            0b011000 -> this.FLAG_C == 1
            0b100000 -> this.FLAG_P == 0
            0b101000 -> this.FLAG_P == 1
            0b110000 -> this.FLAG_S == 0
            0b111000 -> this.FLAG_S == 1
            else ->
                error(
                    "Unable to decode destination condition name from byte value: ${value.toHexString()} - ${
                        value.toString(
                            radix = 2
                        )
                    }"
                )
        }

    private fun add(value: Int, c: Int): Int {
        val result = this.A + value + c

        calculateZSPFlags(result)
        this.FLAG_C = if (result > 0xFF) 1 else 0
        this.FLAG_AC = calculateHalfCarryForAddition(this.A, value, c)

        return (result and 0xFF)
    }

    private fun subtract(value: Int, c: Int): Int {
        val result = this.A - value - c

        calculateZSPFlags(result)
        this.FLAG_C = if (this.A < (value + c)) 1 else 0
        this.FLAG_AC = calculateHalfCarryForSubtraction(this.A, value, c)

        return (result and 0xFF)
    }

    private fun calculateZSPFlags(result: Int) {
        this.FLAG_Z = if ((result and 0xFF) == 0) 1 else 0
        this.FLAG_S = if ((result and 0x80) > 0) 1 else 0
        this.FLAG_P = calculateParityFlag(result)
    }

    private fun calculateParityFlag(number: Int): Int {
        var parity = 0

        for (i in 0..7) {
            if (((number and 0xFF) and (1 shl i) > 0)) {
                parity += 1
            }
        }

        return if (parity % 2 == 0) 1 else 0
    }

    private fun calculateHalfCarryForAddition(a: Int, b: Int, c: Int): Int {
        val lowerNibbleSum = (a and 0x0F) + (b and 0x0F) + (c and 0x0F)
        return if (lowerNibbleSum > 0x0F) 1 else 0
    }

    private fun calculateHalfCarryForSubtraction(a: Int, b: Int, c: Int): Int {
        val result = (a and 0x0F) - (b and 0x0F) - c
        return if (result < 0) 0 else 1
    }

    // endregion

    // region Debug Methods

    private fun traceLog(mem: Memory): String = buildString {
        append("PC: ${PC.toHexString(REGISTER_HEX_FORMAT)}, ")
        append("AF: ${AF.toHexString(REGISTER_HEX_FORMAT)}, ")
        append("BC: ${BC.toHexString(REGISTER_HEX_FORMAT)}, ")
        append("DE: ${DE.toHexString(REGISTER_HEX_FORMAT)}, ")
        append("HL: ${HL.toHexString(REGISTER_HEX_FORMAT)}, ")
        append("SP: ${SP.toHexString(REGISTER_HEX_FORMAT)}, ")
        append("CYC: ${numCycles}\t")
        append("(")
        append(mem.readBytes(PC, 4).map { it.toHexString(OPCODE_HEX_FORMAT) }.joinToString(" "))
        append(")")
        append(" ### ${DISASSEMBLE_TABLE[mem.readByte(PC)]}")
    }

    // endregion
}

private val DISASSEMBLE_TABLE =
    arrayOf(
        "nop",
        "lxi b,#",
        "stax b",
        "inx b",
        "inr b",
        "dcr b",
        "mvi b,#",
        "rlc",
        "ill",
        "dad b",
        "ldax b",
        "dcx b",
        "inr c",
        "dcr c",
        "mvi c,#",
        "rrc",
        "ill",
        "lxi d,#",
        "stax d",
        "inx d",
        "inr d",
        "dcr d",
        "mvi d,#",
        "ral",
        "ill",
        "dad d",
        "ldax d",
        "dcx d",
        "inr e",
        "dcr e",
        "mvi e,#",
        "rar",
        "ill",
        "lxi h,#",
        "shld",
        "inx h",
        "inr h",
        "dcr h",
        "mvi h,#",
        "daa",
        "ill",
        "dad h",
        "lhld",
        "dcx h",
        "inr l",
        "dcr l",
        "mvi l,#",
        "cma",
        "ill",
        "lxi sp,#",
        "sta $",
        "inx sp",
        "inr M",
        "dcr M",
        "mvi M,#",
        "stc",
        "ill",
        "dad sp",
        "lda $",
        "dcx sp",
        "inr a",
        "dcr a",
        "mvi a,#",
        "cmc",
        "mov b,b",
        "mov b,c",
        "mov b,d",
        "mov b,e",
        "mov b,h",
        "mov b,l",
        "mov b,M",
        "mov b,a",
        "mov c,b",
        "mov c,c",
        "mov c,d",
        "mov c,e",
        "mov c,h",
        "mov c,l",
        "mov c,M",
        "mov c,a",
        "mov d,b",
        "mov d,c",
        "mov d,d",
        "mov d,e",
        "mov d,h",
        "mov d,l",
        "mov d,M",
        "mov d,a",
        "mov e,b",
        "mov e,c",
        "mov e,d",
        "mov e,e",
        "mov e,h",
        "mov e,l",
        "mov e,M",
        "mov e,a",
        "mov h,b",
        "mov h,c",
        "mov h,d",
        "mov h,e",
        "mov h,h",
        "mov h,l",
        "mov h,M",
        "mov h,a",
        "mov l,b",
        "mov l,c",
        "mov l,d",
        "mov l,e",
        "mov l,h",
        "mov l,l",
        "mov l,M",
        "mov l,a",
        "mov M,b",
        "mov M,c",
        "mov M,d",
        "mov M,e",
        "mov M,h",
        "mov M,l",
        "hlt",
        "mov M,a",
        "mov a,b",
        "mov a,c",
        "mov a,d",
        "mov a,e",
        "mov a,h",
        "mov a,l",
        "mov a,M",
        "mov a,a",
        "add b",
        "add c",
        "add d",
        "add e",
        "add h",
        "add l",
        "add M",
        "add a",
        "adc b",
        "adc c",
        "adc d",
        "adc e",
        "adc h",
        "adc l",
        "adc M",
        "adc a",
        "sub b",
        "sub c",
        "sub d",
        "sub e",
        "sub h",
        "sub l",
        "sub M",
        "sub a",
        "sbb b",
        "sbb c",
        "sbb d",
        "sbb e",
        "sbb h",
        "sbb l",
        "sbb M",
        "sbb a",
        "ana b",
        "ana c",
        "ana d",
        "ana e",
        "ana h",
        "ana l",
        "ana M",
        "ana a",
        "xra b",
        "xra c",
        "xra d",
        "xra e",
        "xra h",
        "xra l",
        "xra M",
        "xra a",
        "ora b",
        "ora c",
        "ora d",
        "ora e",
        "ora h",
        "ora l",
        "ora M",
        "ora a",
        "cmp b",
        "cmp c",
        "cmp d",
        "cmp e",
        "cmp h",
        "cmp l",
        "cmp M",
        "cmp a",
        "rnz",
        "pop b",
        "jnz $",
        "jmp $",
        "cnz $",
        "push b",
        "adi #",
        "rst 0",
        "rz",
        "ret",
        "jz $",
        "ill",
        "cz $",
        "call $",
        "aci #",
        "rst 1",
        "rnc",
        "pop d",
        "jnc $",
        "out p",
        "cnc $",
        "",
        "sui #",
        "rst 2",
        "rc",
        "ill",
        "jc $",
        "in p",
        "cc $",
        "ill",
        "sbi #",
        "rst 3",
        "rpo",
        "pop h",
        "jpo $",
        "xthl",
        "cpo $",
        "push h",
        "ani #",
        "rst 4",
        "rpe",
        "pchl",
        "jpe $",
        "xchg",
        "cpe $",
        "ill",
        "xri #",
        "rst 5",
        "rp",
        "pop psw",
        "jp $",
        "di",
        "cp $",
        "push psw",
        "ori #",
        "rst 6",
        "rm",
        "sphl",
        "jm $",
        "ei",
        "cm $",
        "ill",
        "cpi #",
        "rst 7"
    )

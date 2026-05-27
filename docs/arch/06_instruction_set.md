# Instruction Set Architecture

## Instruction Format

All instructions are 16 bits wide (fixed length).

### Encoding

```
Bit:  15  14  13  12  11  10   9   8   7   6   5   4   3   2   1   0
     [ opcode  ][    dst     ][    src1    ][    src2    ]  (RR type)
     [ opcode  ][    dst     ][    src     ][   imm8    ]  (RI type)
     [ opcode  ][            imm12              ][ pred ]  (I type)
```

### Instruction Types
- **RR (Register-Register)**: opcode(6) | dst(3) | src1(3) | src2(4)
  - dst, src1: r0-r7 (3-bit)
  - src2: r0-r15 (4-bit) or special
- **RI (Register-Immediate)**: opcode(6) | dst(3) | src(3) | imm8(4)
  - imm8: 4-bit immediate (sign-extended or zero-extended depending on opcode)
- **I (Immediate)**: opcode(6) | imm12(6) | pred(4)
  - imm12: general immediate
  - pred: predicate register select (pred0-pred15)

### Predication
Every instruction can be predicated (I-type) or unpredicated:
- Unpredicated: Always execute
- Predicated: Execute only if predicate register != 0
- Reserved predicate value (0xF) means "always execute" in I-type

## Arithmetic Instructions

### Posit Arithmetic
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| FADD | 000001 | RR | dst = src1 + src2 | 2 |
| FSUB | 000010 | RR | dst = src1 - src2 | 2 |
| FMUL | 000011 | RR | dst = src1 × src2 | 2 |
| FMA | 000100 | RR | dst = src1 × src2 + dst | 4 |
| FMOV | 000101 | RR | dst = src1 | 1 |

### Compare
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| FCMP_EQ | 001000 | RR | dst = (src1 == src2) ? 1.0 : 0.0 | 1 |
| FCMP_NE | 001001 | RR | dst = (src1 != src2) ? 1.0 : 0.0 | 1 |
| FCMP_LT | 001010 | RR | dst = (src1 < src2) ? 1.0 : 0.0 | 1 |
| FCMP_LE | 001011 | RR | dst = (src1 <= src2) ? 1.0 : 0.0 | 1 |
| FCMP_GT | 001100 | RR | dst = (src1 > src2) ? 1.0 : 0.0 | 1 |
| FCMP_GE | 001101 | RR | dst = (src1 >= src2) ? 1.0 : 0.0 | 1 |

### Quire
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| QCLR | 010001 | RR | Clear quire | 1 |
| QACC | 010010 | RR | quire += src1 × src2 | 3 |
| QRND | 010011 | RR | dst = round(quire) | 2 |
| QLD | 010100 | RI | quire = src (load quire from reg) | 1 |

### Conversion
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| I2F | 010101 | RR | Int(16) to Posit(16,1) | 1 |
| F2I | 010110 | RR | Posit(16,1) to Int(16) | 1 |
| F2B | 010111 | RR | Posit(16,1) to bit pattern | 1 |
| B2F | 011000 | RR | Bit pattern to Posit(16,1) | 1 |

### Integer (for address computation)
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| IADD | 011001 | RR | Integer add | 1 |
| ISUB | 011010 | RR | Integer sub | 1 |
| IMUL | 011011 | RR | Integer multiply (low 16 bits) | 1 |
| AND | 011100 | RR | Bitwise AND | 1 |
| OR | 011101 | RR | Bitwise OR | 1 |
| XOR | 011110 | RR | Bitwise XOR | 1 |
| SHL | 011111 | RR | Shift left | 1 |
| SHR | 100000 | RR | Shift right (logical) | 1 |

## Memory Instructions

### Global Memory
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| GLOAD | 100001 | RR | dst = global[src1 + src2] | variable (50-100) |
| GSTORE | 100010 | RR | global[src1 + src2] = dst | variable (50-100) |

### Shared Memory
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| SLOAD | 100011 | RR | dst = shared[src1 + src2] | 1+ (bank conflict) |
| SSTORE | 100100 | RR | shared[src1 + src2] = dst | 1+ (bank conflict) |

### Special Memory
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| LDS | 100101 | RI | dst = special_register[imm] | 1 |

Special registers include: `laneID`, `localInvocationID.x/y/z`, `globalInvocationID.x/y/z`, `workgroupID.x/y/z`, `subgroupSize`, `numSubgroups`

## Control Flow Instructions

| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| BR | 101000 | I | Unconditional branch to PC + imm12 | 2 |
| BRZ | 101001 | I | Branch if predicate == 0 | 2 |
| BRNZ | 101010 | I | Branch if predicate != 0 | 2 |
| CALL | 101011 | I | Subroutine call | 2 |
| RET | 101100 | I | Subroutine return | 2 |
| BARRIER | 101101 | I | Workgroup barrier | 4 |

### Divergence Support
- Stack-based reconvergence: compiler marks branch with reconvergence PC
- BR/RET manage divergence stack in hardware

## Synchronization Instructions

| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| BARRIER | 101101 | I | Workgroup barrier | 4 |
| MEMBAR | 101110 | I | Memory fence (global/shared/all) | 2 |
| ATOMIC_ADD | 101111 | RR | global[src1] += src2 | variable |

## Subgroup Instructions

| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| SUB_BRD | 110000 | RI | dst = subgroupBroadcast(src, laneID) | 2 |
| SUB_SHUFL | 110001 | RI | dst = subgroupShuffle(src, laneID) | 2 |
| SUB_SHFUP | 110010 | RI | dst = subgroupShuffleUp(src, delta) | 2 |
| SUB_SHFDN | 110011 | RI | dst = subgroupShuffleDown(src, delta) | 2 |
| SUB_XOR | 110100 | RI | dst = subgroupShuffleXor(src, xorMask) | 2 |
| SUB_ADD | 110101 | RI | dst = subgroupReduceAdd(src) | 4 |
| SUB_PROD | 110110 | RI | dst = subgroupReduceMul(src) | 4 |
| SUB_MIN | 110111 | RI | dst = subgroupReduceMin(src) | 4 |
| SUB_MAX | 111000 | RI | dst = subgroupReduceMax(src) | 4 |

## Instruction Encoding Summary

| Opcode range | Category |
|---|---|
| 000001-000101 | Posit arithmetic (FADD, FSUB, FMUL, FMA, FMOV) |
| 001000-001101 | Posit compare (FCMP_*) |
| 010001-010111 | Quire & conversion |
| 011001-100000 | Integer/logic |
| 100001-100101 | Memory (global, shared, special) |
| 101000-101101 | Control flow (BR, BRZ, BRNZ, CALL, RET, BARRIER) |
| 101110-101111 | Sync |
| 110000-111000 | Subgroup operations |

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
  - imm8: 4-bit immediate (sign-extended or zero-extended)
- **I (Immediate)**: opcode(6) | imm12(6) | pred(4)
  - imm12: general immediate
  - pred: predicate register select

- **NBR (Neighbor)**: opcode(6) | dst(3) | src(3) | dx(2) | dy(2)
  - dx, dy: signed 2-bit offset (-1, 0, 1), excluding (0,0)
  - Encoded: 00=-1, 01=0, 10=1 (dx=dy=01 → reserved = self)

### Predication
Every instruction can be predicated:
- Unpredicated: Always execute
- Predicated: Execute only if predicate register != 0

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

## Neighbor Instructions

| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| NLOAD | 001110 | NBR | dst = neighbor(dx,dy).reg[src] | 1 |
| NADD | 001111 | NBR | dst = dst + neighbor(dx,dy).reg[src] | 3 |

- `NLOAD`: Read neighbor's register directly via dedicated wire
- `NADD`: Accumulate neighbor value into local register (neighbor + quire combination)
- `(dx, dy)` encoding: 00=-1, 01=0, 10=1; (0,0) is reserved

### Multi-Hop Subgroup Operations

Compiler generates NLOAD sequences for subgroup operations:
```
// subgroupShuffleDown(src, 2)
// Lane (x,y) loads from Lane (x, (y+2)%8) via two hops:
NLOAD tmp, src, 0, 1     // get from south
NLOAD dst, tmp, 0, 1     // get from south again
// Total: 2 cycles (much faster than global memory)
```

Compiler determines optimal multi-hop path given the 8×8 torus topology.

## Memory Instructions

### Global Memory (via Global Bus)
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| GLOAD | 100001 | RR | dst = global[src1 + src2] | variable (50-100) |
| GSTORE | 100010 | RR | global[src1 + src2] = dst | variable (50-100) |

### Shared Memory (via Global Bus)
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| SLOAD | 100011 | RR | dst = shared[src1 + src2] | 10-20 |
| SSTORE | 100100 | RR | shared[src1 + src2] = dst | 10-20 |

### Special Memory
| Mnemonic | Opcode | Type | Description | Cycles |
|---|---|---|---|---|
| LDS | 100101 | RI | dst = special_register[imm] | 1 |

Special registers include: `laneX`, `laneY`, `laneID`, `localInvocationID.x/y/z`, `globalInvocationID.x/y/z`, `workgroupID.x/y/z`, `subgroupSize`, `numSubgroups`

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

## Subgroup Instructions (Multi-Hop)

| Mnemonic | Opcode | Type | Description | Cycles (max) |
|---|---|---|---|---|
| SUB_BRD | 110000 | RI | dst = subgroupBroadcast(src, laneID) | 14 |
| SUB_SHUFL | 110001 | RI | dst = subgroupShuffle(src, laneID) | 14 |
| SUB_SHFUP | 110010 | RI | dst = subgroupShuffleUp(src, delta) | 7 |
| SUB_SHFDN | 110011 | RI | dst = subgroupShuffleDown(src, delta) | 7 |
| SUB_XOR | 110100 | RI | dst = subgroupShuffleXor(src, xorMask) | varies |
| SUB_ADD | 110101 | RI | dst = subgroupReduceAdd(src) | 7 |
| SUB_PROD | 110110 | RI | dst = subgroupReduceMul(src) | 7 |
| SUB_MIN | 110111 | RI | dst = subgroupReduceMin(src) | 7 |
| SUB_MAX | 111000 | RI | dst = subgroupReduceMax(src) | 7 |

These are implemented in microcode as multi-hop NLOAD sequences. Hardware provides a fast-path router per lane; the above latencies assume optimal Manhattan routing.

## Instruction Encoding Summary

| Opcode range | Category |
|---|---|
| 000001-000101 | Posit arithmetic (FADD, FSUB, FMUL, FMA, FMOV) |
| 001000-001101 | Posit compare (FCMP_*) |
| 001110-001111 | **Neighbor (NLOAD, NADD)** |
| 010001-010111 | Quire & conversion |
| 011001-100000 | Integer/logic |
| 100001-100101 | Memory (global, shared, special) |
| 101000-101101 | Control flow (BR, BRZ, BRNZ, CALL, RET, BARRIER) |
| 101110-101111 | Sync |
| 110000-111000 | Subgroup operations |

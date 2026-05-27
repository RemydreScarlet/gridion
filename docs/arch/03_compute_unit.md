# Compute Unit Architecture

## Warp Scheduler

The warp scheduler is responsible for selecting which warp to execute each cycle.

### Design: Round-Robin with Scoreboarding
- Maintains a list of ready warps (not waiting on memory/barrier)
- Round-robin arbitration for fairness
- Scoreboard tracks outstanding memory operations per warp
- On stall (memory latency), switches to next ready warp
- Minimum 4 warps per CU to hide memory latency

### Warp State
```
WarpState {
  pc: UInt              // program counter
  activeMask: UInt(64)  // which lanes are active
  pendingMemOps: UInt   // count of outstanding memory ops
  barrierWaiting: Bool  // waiting at barrier
  killed: Bool          // warp completed/finished
}
```

## SIMT Control Unit

Manages divergent control flow within a warp.

### Convergence Tracking
- **Stack-based reconvergence**: Push PC + mask on branch; pop on reconvergence
- **Immediate post-dominator (IPDOM)**: Hardware computes reconvergence point from branch instruction metadata
- **Selective execution**: Lanes with false predicate are masked off; execution continues

### Divergence Handling
- When a branch condition diverges within a warp:
  1. Push (reconvergencePC, fallthroughMask) onto stack
  2. Execute taken path with takenMask
  3. Execute fallthrough path with fallthroughMask
  4. Pop stack, reconverge at reconvergencePC
- Maximum stack depth: 8 (empirical limit for compute shaders)

## Lane Datapath

Each lane is a complete Posit ALU with private register file.

### Register File
- 16 × Posit(16,1) registers (16 bits each)
- 3 read ports, 1 write port
- Direct mapped: `r0-r15`
- Special registers: `laneID`, `globalInvocationID`, `localInvocationID`, `subgroupID`

### Posit ALU Pipeline
```
Fetch -> Decode -> [Mul | Add | CMP | Conv] -> Writeback
  F1      D1          E1-E3                    WB1
```

#### Cycle-by-cycle:
| Cycle | Stage | Activity |
|---|---|---|
| 1 | F1 | Instruction fetch from microcode memory |
| 2 | D1 | Decode, register file read, operand forwarding |
| 3-4 | E1-E2 | Posit multiply or add (2-cycle) |
| 3 | E1 | Posit compare (1-cycle) |
| 3-5 | E1-E3 | Quire accumulate (3-cycle) |
| 5 | WB1 | Writeback to register file |

### Operand Forwarding
- Bypass network: ALU output → ALU input (1 cycle)
- Memory load → ALU input (stalls if RAW within same instruction)

### Posit ALU Operations
| Operation | Cycles | Description |
|---|---|---|
| FADD | 2 | Posit addition |
| FSUB | 2 | Posit subtraction |
| FMUL | 2 | Posit multiplication |
| FMA | 4 | Posit fused multiply-add (fmul then fadd) |
| FCMP | 1 | Compare (produce Posit 1.0 or 0.0) |
| QACC | 3 | Quire accumulate: q += a × b |
| QRND | 2 | Quire round: convert q to Posit |
| QCLR | 1 | Clear quire register |
| MOV | 1 | Register-to-register move |
| LDI | 1 | Load immediate |

## Shared Memory

- 16 KB shared memory per CU
- 32 banks (2 bytes/bank for Posit(16,1))
- Bank conflict resolution: serialization on conflict
- 1 cycle access (no conflict) or N cycles (N-way conflict)
- Supports: load, store, atomic add (quire-based)

## Performance Model

| Metric | Value |
|---|---|
| Clock frequency | 200 MHz (FPGA target) |
| Warps per CU | 4 (min), configurable up to 16 |
| Peak Posit FLOPS (1 CU) | 64 lanes × 200 MHz × 2 ops/cycle = 25.6 GFLOPS |
| Memory bandwidth | 64 lanes × 16 bits × 200 MHz = 25.6 GB/s (per CU) |
| Shared memory | 16 KB per CU |

### Latency Hiding
- 4 warps × 64 lanes = 256 in-flight threads
- Context switch: 1 cycle (registers are lane-local)
- Memory latency tolerance: up to 64 cycles (4 warps × 16 cycles/warp)

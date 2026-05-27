# Compute Unit Architecture

## 8×8 2D Lane Grid

The CU's core is a 2D mesh of 64 lanes arranged in an 8×8 grid. Each lane has hardware links to its 8 Moore neighbors.

### Grid Coordinates
```
       (-1,-1)  (0,-1)  (1,-1)
         NW       N       NE
       (-1,0)  [LANE]   (1,0)
         W       self     E
       (-1,1)   (0,1)   (1,1)
         SW       S       SE
```

### Physical Implementation
- Each lane has **8 dedicated input wires** from neighbors + self
- Each lane has **8 dedicated output wires** to neighbors
- Total wiring per CU: 64 lanes × 9 inputs × 16 bits = 9,216 wires (entirely local, no global routing)
- Wire delay: < 1 cycle at 200 MHz (local interconnect only)
- No arbitration: neighbor reads are conflict-free by design

### Boundary Handling
Hardware applies boundary mode during neighbor read:
```
fn get_neighbor(x, y, dx, dy, mode) -> value:
    nx = x + dx
    ny = y + dy
    match mode:
        FIXED: if out_of_bounds(nx, ny) return 0
        WRAP:  return grid[nx mod 8][ny mod 8]
        CLAMP: return grid[clamp(nx,0,7)][clamp(ny,0,7)]
```

## Neighbor Read Instruction

The critical hardware primitive: **NLOAD** loads a neighbor's register into the current lane's ALU in 1 cycle.

```
NLOAD dst, src_reg, dx, dy
```
- `dst`: destination register (current lane)
- `src_reg`: source register index (in neighbor lane)
- `(dx, dy)`: neighbor offset (-1, 0, or 1, excluding 0,0)

### Hardware Datapath for NLOAD
```
Cycle 1: Lane(i,j) reads regfile[src_reg] from Lane(i+dx, j+dy)
         → value arrives via dedicated wire
Cycle 2: Value available in Lane(i,j) ALU input
```

No arbitration, no routing — purely combinatorial muxing from neighbor register outputs.

## Warp Scheduler

### Design: Round-Robin with Scoreboarding
- Maintains a list of ready warps (not waiting on memory/barrier)
- Round-robin arbitration for fairness
- Scoreboard tracks outstanding global memory operations per warp
- On stall (global memory latency), switches to next ready warp
- Neighbor loads never stall (1 cycle, deterministic)
- Minimum 4 warps per CU to hide global memory latency

### Warp State
```
WarpState {
  pc: UInt              // program counter
  activeMask: UInt(64)  // which lanes are active
  pendingMemOps: UInt   // count of outstanding global memory ops
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

Each lane is a complete Posit ALU with private register file and neighbor router.

### Register File
- 16 × Posit(16,1) registers (16 bits each)
- 3 read ports, 1 write port
- Direct mapped: `r0-r15`
- Special registers: `laneX`, `laneY`, `laneID`, `globalInvocationID`, `localInvocationID`, `subgroupID`

### Posit ALU Pipeline
```
Fetch -> Decode -> [Nbr | Mul | Add | CMP | Conv] -> Writeback
  F1      D1          E1-E3                          WB1
```

#### Cycle-by-cycle:
| Cycle | Stage | Activity |
|---|---|---|
| 1 | F1 | Instruction fetch from microcode memory |
| 2 | D1 | Decode, register file read, operand forwarding |
| 3 | E1 | **Neighbor read** (NLOAD: 1 cycle, wired directly) |
| 3-4 | E1-E2 | Posit multiply or add (2-cycle) |
| 3 | E1 | Posit compare (1-cycle) |
| 3-5 | E1-E3 | Quire accumulate (3-cycle) |
| 5 | WB1 | Writeback to register file |

### Operand Forwarding
- Bypass network: ALU output → ALU input (1 cycle)
- Neighbor read → ALU input (0 cycle, data arrives in E1)
- Global memory load → ALU input (stalls until data arrives)

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

### Neighbor Operations
| Operation | Cycles | Description |
|---|---|---|
| NLOAD | 1 | dst = neighbor(dx,dy).reg[src] |
| NMOV | 1 | dst = neighbor(dx,dy).reg[dst] (routing) |

## Subgroup Operations via Multi-Hop Neighbor Routing

Since the grid is 2D, subgroup shuffle/broadcast are implemented as multi-hop neighbor traversals.

### Routing Table (Manhattan distance)
| Operation | Max hops | Typical latency |
|---|---|---|
| Shuffle(arbitrary lane) | 14 (7+7) | 14 cycles |
| ShuffleUp(delta) | 7 | 7 cycles |
| ShuffleDown(delta) | 7 | 7 cycles |
| ShuffleXor(mask) | varies | varies |
| Broadcast(lane ID) | 14 | 14 cycles |
| Reduce (tree) | 7 | 7 cycles |

### Hardware Router per Lane
- Each lane has a small routing table for multi-hop packets
- Packet: `{dst_x, dst_y, src_reg, ttl}`
- At each hop, router checks if `(x, y) == (dst_x, dst_y)`:
  - If match: capture value
  - If no match: forward to neighbor in direction of destination

## Performance Model

| Metric | Value |
|---|---|
| Clock frequency | 200 MHz (FPGA target, limited by global bus, not neighbor mesh) |
| Neighbor bandwidth | 64 lanes × 16 bits × 200 MHz = 25.6 GB/s (per CU, local) |
| Global bus bandwidth | 64 bits × 200 MHz = 1.6 GB/s (shared, per CU) |
| Warps per CU | 4 (min), configurable up to 16 |
| Peak Posit FLOPS (1 CU) | 64 lanes × 200 MHz × 2 ops/cycle = 25.6 GFLOPS |
| Shared memory | 16 KB per CU (accessed via global bus) |

### Latency Hiding
- 4 warps × 64 lanes = 256 in-flight threads
- Context switch: 1 cycle (registers are lane-local)
- Global memory latency tolerance: up to 64 cycles (4 warps × 16 cycles/warp)
- Neighbor operations: no hiding needed (1 cycle, deterministic)

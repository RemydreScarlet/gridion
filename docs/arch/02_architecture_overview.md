# Gridion GPU Architecture Overview

## Core Concept: Dual-Network Architecture

Gridion separates data movement into two physically distinct networks:

| Network | Scope | Latency | Width | Purpose |
|---|---|---|---|---|
| **Neighbor Mesh** | Moore 8-neighbor | 1 cycle | 16 bits/lane | Local data exchange (CA-style) |
| **Global Bus** | All lanes ↔ memory | 50-100 cycles | 64 bits shared | Global load/store, shared memory |

**Key insight**: Most compute workloads (CA, stencil, convolution, PDE solvers) involve only local neighbor interactions. By keeping the fast path purely local, the neighbor mesh runs at minimal wire delay while the global bus is used sparingly.

## Top-Level Block Diagram

```
+----------------------------------------------------------+
|                      Gridion GPU                           |
|                                                            |
|  +------------------+     +---------------------------+   |
|  | Command Processor |---->| Workgroup Distributor     |   |
|  +------------------+     +---------------------------+   |
|                                      |                    |
|                         +------------+------------+       |
|                         |            |            |       |
|                     +--------+  +--------+  +--------+   |
|                     |  CU 0  |  |  CU 1  |  | CU(N-1)|   |
|                     +--------+  +--------+  +--------+   |
|                         |            |            |       |
|                     +--------------------------------+    |
|                     |      Global Bus (shared AXI4)  |    |
|                     +--------------------------------+    |
|                          |                               |
|                    +----------+                          |
|                    | Global   |                          |
|                    | Memory   |                          |
|                    | (DDR/HBM)|                          |
|                    +----------+                          |
+----------------------------------------------------------+
```

**Prototype configuration**: 1 CU, 8×8 = 64 lanes in 2D grid, Posit(16,1)

## Compute Unit (CU): 8×8 2D Grid Topology

```
+--------------------------------------------------+
|              Compute Unit (CU)                    |
|                                                   |
|  +----------------+    +----------------------+   |
|  | Warp Scheduler  |--->|  SIMT Control Unit   |   |
|  +----------------+    +----------------------+   |
|                              |                    |
|  +---------------------------+                   |
|  |                                                  |
|  |       8 × 8 2D Lane Grid                        |
|  |  (each lane connected to 8 Moore neighbors)      |
|  |                                                  |
|  |   (0,0) --- (1,0) --- (2,0) --- ... --- (7,0)  |
|  |     |    \    |    \    |    \    |    \    |   |
|  |   (0,1) --- (1,1) --- (2,1) --- ... --- (7,1)  |
|  |     |    \    |    \    |    \    |    \    |   |
|  |   (0,2) --- (1,2) --- (2,2) --- ... --- (7,2)  |
|  |     |    \    |    \    |    \    |    \    |   |
|  |   ...                                            |
|  |     |    \    |    \    |    \    |    \    |   |
|  |   (0,7) --- (1,7) --- (2,7) --- ... --- (7,7)  |
|  |                                                  |
|  +---------------------------+----------------------+
|                              |                      |
|                    +---------+---------+            |
|                    | Global Bus I/F    |            |
|                    | (load/store unit) |            |
|                    +-------------------+            |
+--------------------------------------------------+
```

### Lane Coordinate System
- Each lane has a fixed `(x, y)` coordinate in the 8×8 grid
- Lane ID = `y * 8 + x`
- Neighbor offsets: `(dx, dy)` where `dx, dy ∈ {-1, 0, 1}`, excluding `(0,0)`
- 8 Moore neighbors: N, NE, E, SE, S, SW, W, NW

### Boundary Modes
| Mode | Edge behavior | Use case |
|---|---|---|
| FIXED | Out-of-bounds = 0 | Isolated grids |
| WRAP | Toroidal topology | Periodic CA |
| CLAMP | Duplicate edge value | Neumann BC |

## Lane (PE) Architecture

Each lane contains:
| Component | Description |
|---|---|
| Register File | 16 × Posit(16,1) registers (Vulkan private memory) |
| Posit Decode | Decodes Posit bitstring to internal format |
| Posit Encode | Encodes internal format to Posit bitstring |
| Posit Mul | 2-cycle multiplier |
| Posit Add | 2-cycle adder |
| Posit CMP | Comparator (EQ, NE, LT, LE, GT, GE) |
| Quire | 128-bit fixed-point accumulator |
| Predicate Flag | 1-bit condition flag for predicated execution |
| Neighbor Router | 8 input ports from Moore neighbors |

## Vulkan Mapping

| Vulkan Concept | Gridion Implementation |
|---|---|
| Subgroup (64 invocations) | 1 warp = 8×8 lane grid |
| Workgroup | Multiple warps on a CU |
| Global invocation ID | Computed from dispatch parameters + (x, y) |
| Shared memory | Via global bus (not neighbor mesh) |
| Private memory | Lane register file |
| Barriers | Warp-level barrier (no-op), workgroup barrier via global bus |
| Subgroup operations | Implemented via multi-hop neighbor routing |

## Pipeline Stages

1. **Fetch**: Instruction fetch from microcode memory
2. **Decode**: Instruction decode, register read
3. **Neighbor**: Read neighbor registers (1 cycle, dedicated wires)
4. **Execute**: Posit ALU operation (1-3 cycles)
5. **Memory**: Global load/store (when applicable, via global bus)
6. **Writeback**: Result write to register file

## Key Design Decisions

1. **2D 8×8 grid** — Each lane has fixed spatial position; neighbor communication is free (1 cycle)
2. **Dual network** — Neighbor mesh (fast local) vs global bus (slow long-range); compiler maximizes local traffic
3. **Posit(16,1) for prototyping** — Fits on single FPGA (~400K LUT for CU)
4. **1 CU prototype** — Minimal viable GPU; parameterized for multi-CU
5. **Offline SPIR-V compilation** — Shaders compiled to microcode on host; compiler optimizes for neighbor locality
6. **No texture units** — Compute-only; no graphics pipeline
7. **Subgroup operations via multi-hop** — Shuffle/broadcast use neighbor-to-neighbor routing (max 7 hops for 8×8)

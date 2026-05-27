# Gridion GPU Architecture Overview

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
|                     |      Memory Crossbar            |    |
|                     +--------------------------------+    |
|                          |          |                    |
|                    +----------+ +----------+              |
|                    | Global   | | Shared   |              |
|                    | Memory   | | Memory   |              |
|                    +----------+ +----------+              |
+----------------------------------------------------------+
```

**Prototype configuration**: 1 CU, 64 lanes/CU, Posit(16,1)

## Compute Unit (CU) Block Diagram

```
+--------------------------------------------------+
|              Compute Unit (CU)                    |
|                                                   |
|  +----------------+    +----------------------+   |
|  | Warp Scheduler  |--->|  SIMT Control Unit   |   |
|  | (round-robin)   |    | (PC, active mask)    |   |
|  +----------------+    +----------------------+   |
|                              |                    |
|  +---------------------------+----------------+   |
|  |                           |                |   |
|  v                           v                v   |
| +--------+  +--------+  +--------+  +--------+   |
| | Lane 0 |  | Lane 1 |  | Lane 2 | ...| Lane 63|  |
| | Posit  |  | Posit  |  | Posit  |    | Posit  |  |
| | ALU    |  | ALU    |  | ALU    |    | ALU    |  |
| +--------+  +--------+  +--------+  +--------+   |
|                                                   |
|  +------------------------------------------+    |
|  |  Shared Memory / Local Data Share         |    |
|  +------------------------------------------+    |
|                                                   |
|  +------------------------------------------+    |
|  |  Load/Store Unit (Global Memory Access)   |    |
|  +------------------------------------------+    |
+--------------------------------------------------+
```

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

## Vulkan Mapping

| Vulkan Concept | Gridion Implementation |
|---|---|
| Subgroup (64 invocations) | 1 warp = 64 lanes |
| Workgroup | Multiple warps on a CU |
| Global invocation ID | Computed from dispatch parameters + lane ID |
| Shared memory | CU-local SRAM (16 KB per CU) |
| Private memory | Lane register file |
| Barriers | Warp-level barrier (no-op), workgroup barrier via shared memory |
| Subgroup operations | Shuffle, broadcast via lane crossbar |

## Pipeline Stages

1. **Fetch**: Instruction fetch from microcode memory
2. **Decode**: Instruction decode, register read
3. **Execute**: Posit ALU operation (1-3 cycles depending on op)
4. **Memory**: Load/store access (when applicable)
5. **Writeback**: Result write to register file

## Key Design Decisions

1. **Posit(16,1) for prototyping**: Fits on single FPGA (~470K LUT for CU)
2. **64-lane warp**: Matches Vulkan max subgroup size
3. **1 CU prototype**: Minimal viable GPU; parameterized for multi-CU
4. **Offline SPIR-V compilation**: Shaders compiled to microcode on host; reduces hardware complexity
5. **No texture units**: Compute-only; no graphics pipeline
6. **Scalable interconnect**: Memory crossbar allows adding CUs

# Memory Subsystem Architecture

## Hierarchy Overview

```
Global Memory (off-chip DDR/HBM)
         |
    Memory Controller
         |
    Crossbar Switch
      /    |    \
    CU 0  CU 1  CU N
     |      |      |
  Shared  Shared  Shared
  Memory  Memory  Memory
     |      |      |
   Lanes   Lanes   Lanes
```

## Global Memory

### Interface
- **AXI4**: 64-bit data bus, 32-bit address
- **Capacity**: 4 GB (FPGA DDR4 typical)
- **Latency**: ~50-100 cycles (off-chip)
- **Coherency**: None (GPU-style, explicit sync)

### Addressing
- **Flat address space**: 32-bit byte address
- **Alignment**: 2-byte aligned (Posit(16,1) = 16 bits)
- **Memory-mapped I/O**: High addresses for command/status registers

### Global Load/Store
- **Vector load**: 64 lanes × 16 bits = 128 bytes per warp load
  - Split into burst transactions
  - Coalescing: consecutive addresses merged into single burst
- **Scatter/gather**: Non-consecutive addresses → multiple transactions
- **Atomic operations**: quire-accumulate, compare-and-swap

### Memory Types (Vulkan mapping)
| Vulkan Type | Gridion Implementation |
|---|---|
| Storage buffer | Global memory, linear address |
| Uniform buffer | Global memory, cached read-only |
| Push constants | Command processor register space |

## Shared Memory

### Architecture
- **16 KB per CU**, partitioned into 16 banks of 8 bytes each
- **Address interleaving**: consecutive 2-byte words in consecutive banks
- **Access modes**:
  - Normal: load/store per-lane
  - Broadcast: same address from all lanes (1 cycle)
  - Shuffle: permute data between lanes (1 cycle)

### Bank Conflicts
- N lanes accessing same bank → N-way serialization
- Conflict-free patterns: all different banks, same address (broadcast)
- Hardware conflict detection (N-input XOR tree)

### Shared Memory Operations
| Operation | Latency | Notes |
|---|---|---|
| Load (no conflict) | 1 cycle | Read from shared memory |
| Store (no conflict) | 1 cycle | Write to shared memory |
| Load (N-way conflict) | N cycles | Serialized access |
| Atomic add | 3 cycles | Quire-based accumulation |

## Private Memory (Registers)

### Per-Lane Register File
- **16 registers × 16 bits** = 32 bytes per lane
- **Total**: 64 lanes × 32 bytes = 2 KB per CU (register file)
- **3R1W ports**: 3 reads (two src, one predicate) + 1 write

### Spill to Shared Memory
- When register pressure exceeds 16, compiler spills to shared memory
- Spill area: top portion of shared memory (configurable, default 256 bytes)

## Memory Model (Vulkan Compliance)

### Vulkan Memory Model Features Supported
- **Non-private**: Storage buffers, uniform buffers
- **Workgroup scope**: Shared memory
- **Subgroup scope**: Shuffle operations
- **Queue family scope**: Global memory

### Memory Ordering
- **Relaxed**: Default (no ordering guarantees)
- **Acquire/AcquireRelease**: Barrier instruction + fence
- **Sequentially consistent**: Full memory barrier

### Synchronization
| Instruction | Effect |
|---|---|
| BARRIER | Workgroup barrier: all lanes in CU must reach BARRIER |
| MEMBAR_GLOBAL | Global memory fence |
| MEMBAR_SHARED | Shared memory fence |
| MEMBAR_ALL | Full fence |

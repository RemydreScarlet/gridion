# Memory Subsystem Architecture

## Dual-Network Philosophy

Gridion has two physically separated data movement networks:

```
+----------------------------------------------------+
|                  CU Interior                        |
|                                                     |
|  +---------------------------------------------+   |
|  |        Neighbor Mesh (8×8 grid)              |   |
|  |  - 1 cycle, dedicated wires per lane          |   |
|  |  - 8 neighbors, 16 bits each                 |   |
|  |  - No arbitration, no routing (single-hop)    |   |
|  +---------------------------------------------+   |
|                                                     |
|  +---------------------------------------------+   |
|  |        Global Bus (shared, AXI4-like)        |   |
|  |  - 50-100 cycles, 64-bit data bus             |   |
|  |  - Shared across all lanes in CU              |   |
|  |  - Memory-mapped: DDR, shared mem, MMIO       |   |
|  +---------------------------------------------+   |
|                                                     |
+----------------------------------------------------+
```

**Key rule**: Neighbor mesh never touches global memory. Global bus never directly connects lanes to each other. These are separate physical networks.

## Global Bus

A single shared bus connecting all lanes in a CU to the memory system.

### Interface
- **AXI4-lite**: 64-bit data bus, 32-bit address
- **Capacity**: 4 GB (FPGA DDR4 typical)
- **Latency**: ~50-100 cycles (off-chip DDR)
- **Coherency**: None (GPU-style, explicit sync)
- **Arbitration**: Round-robin between warps (not individual lanes)

### Addressing
- **Flat address space**: 32-bit byte address
- **Alignment**: 2-byte aligned (Posit(16,1) = 16 bits)
- **Memory-mapped I/O**: High addresses for command/status registers

### Global Load/Store
- **Vector load**: 64 lanes × 16 bits = 128 bytes per warp load
  - Sequentially serialized on 64-bit bus: 16 beats per warp load
  - Coalescing: consecutive lane addresses merged into fewer beats
- **Scatter/gather**: Non-consecutive addresses → multiple transactions (slow)
- **Atomic operations**: quire-accumulate, compare-and-swap

### Memory Types (Vulkan mapping)
| Vulkan Type | Gridion Implementation |
|---|---|
| Storage buffer | Global memory (DDR), accessed via global bus |
| Uniform buffer | Global memory, cached read-only, via global bus |
| Push constants | Command processor register space |

## Shared Memory

- **16 KB per CU**, single-ported SRAM
- Attached to the global bus, NOT to the neighbor mesh
- **Access cost**: 10-20 cycles (on-CU SRAM + bus arbitration)
- Contrast with neighbor mesh: 1 cycle, free

### When to use shared memory vs neighbor mesh
| Pattern | Recommended | Reason |
|---|---|---|
| Adjacent data exchange | Neighbor mesh (NLOAD) | 1 cycle |
| Random access within workgroup | Shared memory | 10-20 cycles |
| Reduction across all lanes | Multi-hop neighbor tree | ~7 cycles |
| Scatter to distant lane | Shared memory | Required |

### Bank Organization
- 16 banks × 8 bytes each (match global bus width)
- Not optimized for lane-parallel access (lanes serialize through bus)

## Address Space Layout

```
0x0000_0000 - 0x3FFF_FFFF: Global memory (DDR, ~1 GB)
0x4000_0000 - 0x4000_3FFF: Shared memory (16 KB per CU)
0x4000_4000 - 0x4000_4FFF: Instruction memory (microcode)
0xFFFF_0000 - 0xFFFF_FFFF: Memory-mapped control registers
```

## Memory Model (Vulkan Compliance)

### Vulkan Memory Model Features Supported
- **Non-private**: Storage buffers, uniform buffers
- **Workgroup scope**: Shared memory (via global bus)
- **Subgroup scope**: Neighbor mesh (fast), shuffle via multi-hop
- **Queue family scope**: Global memory

### Memory Ordering
- **Relaxed**: Default (no ordering guarantees)
- **Acquire/AcquireRelease**: Barrier instruction + fence
- **Sequentially consistent**: Full memory barrier

### Synchronization
| Instruction | Effect |
|---|---|
| BARRIER | Workgroup barrier: all warps in CU must reach BARRIER |
| MEMBAR_GLOBAL | Global memory fence |
| MEMBAR_SHARED | Shared memory fence |
| MEMBAR_ALL | Full fence |

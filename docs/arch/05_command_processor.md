# Command Processor & Workgroup Dispatch

## Host Interface

### Register Map (Memory-Mapped I/O)
| Address | Register | Width | Description |
|---|---|---|---|
| 0x0000 | STATUS | 32 | GPU status (idle/busy/error) |
| 0x0004 | CONTROL | 32 | Control register (start/stop/reset) |
| 0x0008 | KERNEL_ADDR | 32 | Microcode kernel base address |
| 0x000C | WG_X | 16 | Workgroup count X |
| 0x0010 | WG_Y | 16 | Workgroup count Y |
| 0x0014 | WG_Z | 16 | Workgroup count Z |
| 0x0018 | LOCAL_X | 16 | Workgroup size X |
| 0x001C | LOCAL_Y | 16 | Workgroup size Y |
| 0x0020 | LOCAL_Z | 16 | Workgroup size Z |
| 0x0024 | PUSH_CONST_0 | 32 | Push constant 0 |
| 0x0028 | PUSH_CONST_1 | 32 | Push constant 1 |
| 0x002C | PUSH_CONST_2 | 32 | Push constant 2 |
| 0x0030 | PUSH_CONST_3 | 32 | Push constant 3 |
| 0x0034 | DISPATCH | 32 | Write 1 to trigger dispatch |

### Dispatch Sequence
1. Host writes kernel address, workgroup dimensions, push constants
2. Host writes 1 to DISPATCH register
3. Command processor:
   a. Copies kernel microcode to CU instruction memory
   b. Initializes workgroup counter
   c. Sends workgroup descriptors to available CU(s)
4. CU executes workgroups until all complete
5. STATUS register returns to idle

## Workgroup Distributor

### Distribution Policy
- **Static partitioning**: (workgroupCount / CUNum) per CU (when CUs available)
- **Dynamic dispatch**: CUs request next workgroup on completion (load balancing)
- **Prototype (1 CU)**: No distribution needed; serial workgroup execution

### Workgroup Descriptor
```
WorkgroupDescriptor {
  groupID:        (UInt(16), UInt(16), UInt(16))
  globalOffset:   (UInt(16), UInt(16), UInt(16))
  localSize:      (UInt(16), UInt(16), UInt(16))
  kernelAddr:     UInt(32)
  pushConstants:  [UInt(32) × 4]
}
```

### Local/Global ID Computation
Hardware computes for each warp:
- `localInvocationID.x = laneID % localSize.x`
- `localInvocationID.y = (laneID / localSize.x) % localSize.y`
- `localInvocationID.z = laneID / (localSize.x * localSize.y)`
- `globalInvocationID = groupID * localSize + localInvocationID`

## Microcode Memory

### Organization
- **Per-CU instruction memory**: 4 KB (2048 × 16-bit instructions)
- **Instruction format**: 16-bit fixed width (simple ISA)
- **Load at dispatch**: Command processor loads kernel from global memory

## Synchronization Primitives

### Workgroup Completion
- CU signals "workgroup done" to distributor
- Distributor sends next descriptor or sets GPU idle

### Barrier Support
- **Workgroup barrier**: All warps in CU must reach BARRIER before proceeding
- Hardware counter: tracks warps arrived at barrier
- Release when all warps (or all active lanes) have arrived

## Status Reporting

| STATUS field | Value | Meaning |
|---|---|---|
| IDLE | 0 | GPU ready for dispatch |
| RUNNING | 1 | Kernel executing |
| DONE | 2 | Kernel complete (set after last workgroup) |
| ERROR | 3 | Unrecoverable error occurred |

### Interrupt (optional)
- STATUS_DONE can generate interrupt via IRQ line
- Host polls or waits for interrupt

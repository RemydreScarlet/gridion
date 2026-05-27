# Background: Vulkan Compute & Posit Numbers

## Vulkan Compute Model

Vulkan Compute provides a programming model for GPU-accelerated computation.

### Dispatch Hierarchy
- **Dispatch**: A single compute shader launch (`vkCmdDispatch`)
- **Workgroup**: A 3D grid of workgroups, each identified by `(groupID_x, groupID_y, groupID_z)`
- **Local Invocation**: Individual threads within a workgroup, identified by `(localInvocationID_x, localInvocationID_y, localInvocationID_z)`
- **Global Invocation**: `globalInvocationID = groupID * workgroupSize + localInvocationID`
- **Subgroup**: A set of invocations that execute together (typically 32 or 64). Vulkan guarantees subgroup support via `VK_KHR_shader_subgroup`.

### Memory Model
- **Global Memory**: Accessible by all invocations, large capacity, high latency
- **Shared Memory**: Local to a workgroup, low latency, explicit management
- **Private Memory**: Per-invocation registers
- **Constant Memory**: Read-only, cached

### Execution Model
- **SIMT (Single Instruction, Multiple Threads)**: All invocations in a subgroup execute the same instruction on different data
- **Convergence**: Control flow can diverge within a subgroup; hardware tracks active mask
- **Barrier**: `barrier()` synchronizes all invocations within a workgroup

## Posit Number Format

Posit is a tapered floating-point format invented by John L. Gustafson (2017).

### Posit(n, es) Encoding
```
sign | regime | exponent | fraction
  1      variable   es bits    remaining bits
```

- **Regime**: A variable-length run-length encoded field that determines scale
  - Sequence of `k` identical bits terminated by opposite bit (or end of field)
  - `useed = 2^(2^es)`
  - Scale factor: `regime_value = -k` (if bits start with 0) or `k-1` (if bits start with 1)
- **Exponent**: `es` bits of exponent (unsigned)
- **Fraction**: remaining bits (with hidden 1 bit)
- **Total value**: `(-1)^sign × useed^regime_value × 2^exponent × 1.fraction`

### Posit(16,1) Parameters
| Parameter | Value |
|---|---|
| Total bits (N) | 16 |
| Exponent bits (ES) | 1 |
| useed | 4 |
| Max regime value | 15 (0000000000000001...) |
| Min regime value| -15 (1111111111111110...) |
| Max exponent | 1 |
| Max fraction bits | 12 |
| Min positive | 2^-30 |
| Max positive | 2^30 |
| Posit(32,2) | 32 bits, ES=2, useed=16 |

### Special Values
- **0**: All bits zero
- **NaR (Not-a-Real)**: Sign=1, all other bits zero (replaces NaN and Infinity)
- No positive/negative zero distinction
- No denormalized numbers (tapered precision handles this naturally)

### Quire
A complete-precision fixed-point accumulator for exact dot products:
- Posit(16,1) quire: 128 bits (16 × 8 = 128)
- Posit(32,2) quire: 512 bits (32 × 16 = 512)
- Eliminates all intermediate rounding in sums of products
- Single rounding step at the end

## Why Posit for GPU Computing?

| Aspect | IEEE 754 float | Posit |
|---|---|---|
| Precision taper | Uniform mantissa | Tapered (more bits near 1.0) |
| Special values | ±Inf, ±0, NaN | Single NaR |
| Dot product | Multiple rounding errors | Quire eliminates intermediate rounding |
| Hardware cost | Well-optimized | Simpler (no subnormals, no IEEE rounding modes) |
| Dynamic range | Fixed by exponent width | Variable (regime + exponent) |

Posit's quire is particularly valuable for GPU workloads like matrix multiplication, convolution, and reduction operations where dot products dominate.

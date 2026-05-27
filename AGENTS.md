# Gridion — Vulkan Compute GPU with Posit Arithmetic

## Project Overview
Gridion is a Vulkan Compute-compatible GPU prototype using Posit number format (Unum Type III). Designed in Chisel. Target: FPGA prototyping (Alveo U280 / Kintex-7).

## Architecture Decisions
- **Posit(16,1)** — primary target for FPGA fit (64 lanes × ~5.5K LUT ≈ 470K LUT total)
- **SIMT execution model** — 64 lanes/warp matches Vulkan subgroup size
- **SPIR-V offline compiler** — shaders compiled to internal microcode on host
- **Dedicated Posit arithmetic units** (add/mul/compare) — not LUT-based
- **Per-lane quire** — complete precision for dot-product accumulation
- **1 compute unit (CU) prototype** — scalable to multi-CU design

## Directory Structure
| Path | Purpose |
|---|---|
| `docs/arch/*.md` | Architecture documentation (read before coding) |
| `src/main/scala/gridion/posit/` | Posit arithmetic modules (decode/encode/add/mul) |
| `src/main/scala/gridion/gpu/simt/` | SIMT core: warp scheduler, lane datapath |
| `src/main/scala/gridion/gpu/memory/` | Memory hierarchy (global/local/private) |
| `src/main/scala/gridion/gpu/command/` | Command processor & workgroup dispatch |
| `src/main/scala/gridion/gpu/` | Top-level GPU module |
| `src/test/scala/gridion/` | Chisel tests |
| `spirv/` | SPIR-V to microcode compiler (software) |

## Build & Test
```bash
sbt compile          # build all Chisel sources
sbt test             # run all tests
sbt "testOnly gridion.posit.PositAdderTest"  # single test
```

## Conventions
- No comments unless logic is non-trivial
- Posit params: N (total bits), ES (exponent size)
- Module prefix: `Posit*` for arithmetic, `SIMT_*` for SIMT, `CU_*` for compute unit, `Mem_*` for memory
- Tests: use `assert` + `expect` in chiseltest

## First Steps (after arch doc review)
1. `src/main/scala/gridion/posit/PositDecode.scala`
2. `src/main/scala/gridion/posit/PositEncode.scala`
3. `src/main/scala/gridion/posit/PositMul.scala`
4. `src/main/scala/gridion/posit/PositAdd.scala`
5. `src/main/scala/gridion/gpu/simt/SIMTLane.scala`
6. `src/main/scala/gridion/gpu/simt/WarpScheduler.scala`
7. `src/main/scala/gridion/gpu/ComputeUnit.scala`
8. `src/main/scala/gridion/gpu/GridionGPU.scala`

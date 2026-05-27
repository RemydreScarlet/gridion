# Gridion — Vulkan Compute GPU with Posit Arithmetic

## Overview
Gridion is a Vulkan Compute-compatible GPU prototype using Posit number format (Unum Type III).
Designed in Chisel (Scala). Target: FPGA prototyping (Alveo U280 / Kintex-7).

## Project Structure
```
gridion/
├── AGENTS.md         # opencode config / first steps
├── CLAUDE.md         # This file
├── GEMINI.md         # Gemini Code Assist config
├── docs/arch/        # Architecture design docs (7 files)
├── src/
│   ├── main/scala/gridion/
│   │   ├── posit/    # Posit arithmetic (decode/encode/add/mul)
│   │   ├── gpu/
│   │   │   ├── simt/     # SIMT core: warp scheduler, lane
│   │   │   ├── memory/   # Memory hierarchy
│   │   │   └── command/  # Command processor & dispatch
│   │   └── gpu.scala     # Top-level GPU module
│   └── test/scala/gridion/
│       ├── posit/
│       └── gpu/
├── spirv/            # SPIR-V to microcode compiler
└── fpga/             # FPGA synthesis scripts
```

## Design Decisions
- **Posit(16,1)** primary target for FPGA fit (64 lanes × ~5.5K LUT ≈ 470K LUT)
- **SIMT execution** — 64 lanes/warp = Vulkan subgroup size
- **Dedicated Posit ALUs** — not LUT-based
- **Per-lane quire** — complete precision for dot-product accumulation
- **Offline SPIR-V compiler** — shaders → internal microcode on host
- **1 CU prototype** — scalable to multi-CU

## Build Commands
```bash
sbt compile                              # build all Chisel sources
sbt test                                 # run all tests
sbt "testOnly gridion.posit.PositAdderTest"  # single test
sbt "runMain gridion.Generator --target-dir fpga/build"  # Verilog
```

## Code Conventions
- Chisel 6.x, Scala 2.13
- No comments unless logic is non-trivial
- Posit params: N (total width), ES (exponent bits)
- Module prefix: `Posit*` arithmetic, `SIMT_*` SIMT, `CU_*` compute unit, `Mem_*` memory
- Test style: chiseltest `assert` + `expect`
- Use `Bundle` for grouped IO, `withClockAndReset` for sequential logic

## First Implementation Steps
1. `src/main/scala/gridion/posit/PositDecode.scala`
2. `src/main/scala/gridion/posit/PositEncode.scala`
3. `src/main/scala/gridion/posit/PositMul.scala`
4. `src/main/scala/gridion/posit/PositAdd.scala`
5. `src/main/scala/gridion/gpu/simt/SIMTLane.scala`
6. `src/main/scala/gridion/gpu/simt/WarpScheduler.scala`
7. `src/main/scala/gridion/gpu/ComputeUnit.scala`
8. `src/main/scala/gridion/gpu/GridionGPU.scala`

## References
- docs/arch/01_background.md — Vulkan Compute & Posit background
- docs/arch/03_compute_unit.md — SIMT core details
- SoftPosit: https://gitlab.com/cerlane/SoftPosit

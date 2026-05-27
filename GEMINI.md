# Gridion — Vulkan Compute GPU with Posit Arithmetic

## Architecture
Posit数形式を採用したVulkan Compute互換GPUのプロトタイプ。
SIMT実行モデル、64 lanes/warp、専用Posit演算器 + クワイア。

## Key Specs
- Posit(16,1) primary target, parameterized for Posit(32,2)
- 1 Compute Unit × 64 lanes (scalable)
- 16 KB shared memory per CU
- Warp scheduler (round-robin + scoreboarding)
- 16 registers/lane, per-lane quire
- SPIR-V → microcode offline compiler

## Commands
| Task | Command |
|---|---|
| Build | `sbt compile` |
| Test | `sbt test` |
| Single test | `sbt "testOnly gridion.posit.*"` |
| Verilog gen | `sbt "runMain gridion.Generator"` |

## Code Style
- Chisel 6.x, Scala 2.13+
- Bundle for grouped IO
- `withClockAndReset` for sequential logic
- Posit: `val positWidth: Int` and `val exponentSize: Int` params
- Test: chiseltest `fork`+`join` pattern

## Directories
- `src/main/scala/gridion/posit/` — Posit arithmetic units
- `src/main/scala/gridion/gpu/simt/` — SIMT core lanes & scheduler
- `src/main/scala/gridion/gpu/memory/` — Memory hierarchy
- `src/main/scala/gridion/gpu/command/` — Command processor
- `src/main/scala/gridion/gpu/` — Top-level GPU module
- `spirv/` — SPIR-V to microcode compiler

## References
- docs/arch/01_background.md — Vulkan Compute & Posit survey
- docs/arch/03_compute_unit.md — SIMT / lane details
- docs/arch/06_instruction_set.md — GPU ISA

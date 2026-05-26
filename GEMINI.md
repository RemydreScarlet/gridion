# Gridion — Posit-Based CA Accelerator

## Architecture
専用PEアレイアクセラレータ。各PEはPosit演算器(加算/乗算/比較)+Quireを持ち、Moore近傍(8方向)で結合。8×8=64PE。遷移関数はマイクロコード制御の専用演算器方式。

## Key Specs
- Posit: `(16,1)` and `(32,2)` parameterized
- PE array: 8×8 mesh
- Quire: per-PE complete precision accumulator (128/512bit)
- State memory: per-PE register file
- Host I/F: memory-mapped (RISC-V or PCIe)

## Commands
| Task | Command |
|---|---|
| Build | `sbt compile` |
| Test | `sbt test` |
| Single test | `sbt "testOnly gridion.pe.*"` |
| Verilog gen | `sbt "runMain gridion.Generator"` |

## Code Style
- Chisel 6.x, Scala 2.13+
- Bundle for grouped IO
- `withClockAndReset` for sequential logic
- Posit uses `val positWidth: Int` and `val exponentSize: Int` params
- Test: chiseltest `fork`+`join` pattern

## Directories (planned)
- `src/main/scala/gridion/posit/` — Posit arithmetic units
- `src/main/scala/gridion/pe/` — PE pipeline stages
- `src/main/scala/gridion/array/` — Array topology & routing
- `src/main/scala/gridion/ctrl/` — Global sequencer

## References
- docs/arch/01_background.md — 全サーベイ
- docs/arch/03_pe_architecture.md — PE詳細
- SoftPosit: https://gitlab.com/cerlane/SoftPosit
- SPADE multi-precision Posit SIMD MAC

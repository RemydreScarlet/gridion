# Gridion — Project Guide for opencode

## Project Overview
Gridion is a cellular automaton accelerator using Posit number format.
Designed in Chisel. Target: FPGA prototyping.

## Architecture Decisions
- **Posit(16,1) and Posit(32,2)** — both under evaluation
- **8×8 PE array** (64 PEs) — small enough for single FPGA
- **Moore 8-neighbor** — fixed topology, design extensible
- **Dedicated arithmetic units** (add/mul/compare) — not LUT-based
- **Per-PE quire** — complete precision for weighted neighborhood sums

## Key Files
| File | Purpose |
|---|---|
| `docs/arch/*.md` | Full architecture documentation (read before coding) |
| `src/main/scala/gridion/posit/` | Posit arithmetic modules |
| `src/main/scala/gridion/pe/` | PE pipeline (decode/mul/accum/trans/encode) |
| `src/main/scala/gridion/array/` | 8×8 array with Moore connectivity |
| `src/main/scala/gridion/ctrl/` | Global sequencer & microcode ROM |
| `src/test/scala/gridion/` | Chisel tests |

## Build & Test
```bash
sbt compile          # build all Chisel sources
sbt test             # run all tests
sbt "testOnly gridion.posit.PositAdderTest"  # single test
```

## Conventions
- No comments unless logic is non-trivial
- Describe architecture rationale before implementing
- Posit params: N (total bits), ES (exponent size)
- Module prefix: `Posit*` for arithmetic, `PE_*` for PE, `Array_*` for array
- Tests: use `assert` + `expect` in chiseltest

## First Steps (after arch doc review)
1. `src/main/scala/gridion/posit/PositDecode.scala`
2. `src/main/scala/gridion/posit/PositEncode.scala`
3. `src/main/scala/gridion/posit/PositMul.scala`
4. `src/main/scala/gridion/posit/PositAdd.scala`
5. `src/main/scala/gridion/pe/PE.scala`
6. `src/main/scala/gridion/array/Array8x8.scala`

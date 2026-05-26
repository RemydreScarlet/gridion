# Gridion Project Guide

## Overview
GridionはPosit数形式を用いたセル・オートマトン専用アクセラレータのHW設計プロジェクト。
8×8のPEアレイ、Moore近傍、専用演算器(加算/乗算/比較)方式、Chisel HDLでRTL設計。

## Project Structure
```
gridion/
├── CLAUDE.md         # This file
├── GEMINI.md         # Gemini Code Assist config
├── AGENTS.md         # opencode config
├── docs/
│   └── arch/         # Architecture design documents
│       ├── 01_background.md
│       ├── 02_posit_ca_rationale.md
│       ├── 03_pe_architecture.md
│       ├── 04_array_topology.md
│       ├── 05_instruction_set.md
│       └── 06_evaluation_plan.md
├── src/
│   ├── main/scala/  # Chisel source
│   │   ├── pe/      # Processing Element
│   │   ├── array/   # PE Array & Topology
│   │   ├── posit/   # Posit arithmetic units
│   │   └── ctrl/    # Global controller
│   └── test/scala/  # Chisel tests
├── fpga/            # FPGA synthesis scripts
├── software/        # Reference soft Posit CA simulator
└── scripts/         # Build/util scripts
```

## Design Decisions
- **Posit format**: Posit(16,1) and Posit(32,2) both under consideration
- **PE array**: 8×8 (64 PEs) mesh
- **Neighborhood**: Moore (8-neighbor), fixed with extensible design
- **Transition function**: Dedicated arithmetic units (add/mul/compare), not LUT-based
- **Quire**: Per-PE complete precision accumulator for weighted sums
- **Implementation target**: FPGA (Xilinx Alveo U280 / Kintex-7)

## Build Commands
```bash
# Compile Chisel
sbt compile

# Run all tests
sbt test

# Run specific test
sbt "testOnly gridion.pe.*"

# Generate Verilog
sbt "runMain gridion.Generator --target-dir fpga/build"

# FPGA synth (Vivado)
cd fpga && vivado -mode batch -source synth.tcl
```

## Code Conventions
- Scala/Chisel: Use `//` comments only for tricky logic, prefer descriptive names
- Wire naming: `io_*` for module I/O, `w_*` for wires, `r_*` for registers
- Posit params: `N` for total width, `ES` for exponent bits
- Modules: `Posit*` for arithmetic, `PE_*` for PE, `Array_*` for array-level
- Test style: chiseltest with `expect` assertions
- Avoid hardcoding array size; use parameter `val nPes = 8`
- SoftPosit library for software reference (C/Python)
- Chisel version: 6.x (with chiseltest)

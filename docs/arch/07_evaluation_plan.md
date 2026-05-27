# Evaluation Plan

## Prototype Targets

| Target | Device | LUTs | FFs | BRAM | DSP |
|---|---|---|---|---|---|
| Primary | Xilinx Alveo U280 | 1.3M | 2.6M | 902 | 9,024 |
| Secondary | Xilinx Kintex-7 (XC7K325T) | 326K | 407K | 445 | 840 |

### Expected Resource Usage (Posit(16,1), 1 CU)

| Module | LUTs | FFs | BRAM |
|---|---|---|---|
| Posit Decode (×1) | ~200 | ~50 | 0 |
| Posit Encode (×1) | ~150 | ~50 | 0 |
| Posit Add (×1) | ~800 | ~400 | 0 |
| Posit Mul (×1) | ~1,200 | ~600 | 0 |
| Lane total (×1) | ~5,500 | ~3,000 | 0 |
| CU (64 lanes + scheduler + mem) | ~380,000 | ~210,000 | 32 |
| Total (1 CU) | ~400,000 | ~230,000 | 32 |

## Phase 1: Chisel RTL Implementation

### Posit Arithmetic (Weeks 1-3)
| Module | Test | Target |
|---|---|---|
| PositDecode | Round-trip decode/encode | All posit values |
| PositEncode | Round-trip with decode | All posit values |
| PositAdd | Random values + edge cases | Max error < 1 ulp |
| PositMul | Random values + edge cases | Max error < 1 ulp |
| PositCMP | All compare operations | All 6 comparisons |

### SIMT Core (Weeks 4-6)
| Module | Test | Target |
|---|---|---|
| Lane datapath | Single lane, all instructions | Functional correctness |
| Warp scheduler | Round-robin, scoreboarding | No deadlock |
| Divergence stack | Divergent branches | Correct convergence |

### Compute Unit (Weeks 7-8)
| Module | Test | Target |
|---|---|---|
| CU top-level | Single workgroup dispatch | End-to-end correctness |
| Shared memory | Bank conflict patterns | Correct arbitration |
| Load/Store | Coalesced/scattered access | Correct addressing |

### Full GPU (Weeks 9-10)
| Module | Test | Target |
|---|---|---|
| Command processor | Dispatch sequence | Correct register R/W |
| GPU top-level | Complete kernel execution | End-to-end |

## Phase 2: FPGA Prototyping

### Steps
1. **Synthesis** (Vivado) — resource utilization check
2. **Timing closure** — meet 200 MHz target
3. **Bitstream generation** — program Alveo U280
4. **Hardware validation** — on-chip test via PCIe

### Host Interface
- RISC-V soft-core (VexRiscv) for initial testing
- PCIe endpoint (XDMA) for host communication

## Phase 3: SPIR-V Compiler

### Components
| Component | Implementation |
|---|---|
| SPIR-V parser | Custom Rust/OCaml frontend |
| Control flow analysis | Divergence detection, reconvergence |
| Register allocation | Graph coloring (16 registers/lane) |
| Code generation | Binary microcode output |

### Validation
- Compile Vulkan compute shaders → microcode
- Run on Chisel simulator → compare results with GPU reference

## Phase 4: Benchmarking

### Compute Benchmarks
| Benchmark | Type | Posit advantage |
|---|---|---|
| Matrix multiply (FPGA) | Dense linear algebra | Quire accuracy |
| Convolution | Image/signal processing | Quire accuracy |
| N-body simulation | Particle simulation | Quire accumulation |
| Mandelbrot | Fractal computation | Tapered precision |
| Reduction | Parallel reduction | Quire precision |

### Accuracy Comparison
- Compare: Binary64, Binary32, Posit(32,2), Posit(16,1)
- Metrics: ULPs error, bits correct, convergence rate
- Target: Posit(16,1) accuracy comparable to Binary32 for compute workloads

### Performance Metrics
| Metric | Definition | Target (1 CU @ 200 MHz) |
|---|---|---|
| Peak GFLOPS | Posit ops / s | 25.6 GFLOPS |
| Memory bandwidth | GB/s (global) | 25.6 GB/s |
| Workgroups/sec | Workgroups / s | Depends on kernel |
| Energy/op | pJ / posit op | TBD |
| LUT utilization | Used / total | < 80% |

### Comparison Targets
- Software: CPU (AVX-512), GPU (Vulkan float16)
- Hardware: Xilinx FP16 implementation on same FPGA
- Metrics: Performance/area/energy vs accuracy

## Deliverables

1. **RTL**: Complete Gridion GPU Chisel source
2. **Tests**: Comprehensive Chisel test suite
3. **Bitstream**: FPGA bitstream for Alveo U280
4. **Compiler**: SPIR-V to microcode compiler
5. **Benchmarks**: Benchmark suite with accuracy analysis
6. **Documentation**: Architecture, API, results

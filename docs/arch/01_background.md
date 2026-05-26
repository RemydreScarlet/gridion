# 01: Background & Related Work Survey

## 1.1 Cellular Automata on GPU

### 1.1.1 CAT: Cellular Automata on Tensor Cores

**論文**: *CAT: Cellular Automata on Tensor cores* (arXiv:2406.17284, 2024)

CATはNVIDIA GPUのTensor Coreを利用してセル・オートマトンを高速シミュレーションする手法。
Tensor Coreが提供するMMA (Matrix Multiply-Accumulate) 演算をCAの近傍重み付き総和にマッピングする。

**主要な特徴**:
- 半径 r に依存しない O(1) の計算コスト (r ≤ 16 の範囲)
- 16×16 fragment あたり6回のMMAで任意半径rのCAを実行
- 遷移関数が近傍の重み付き総和に基づくCAに適用可能
- コストモデル (拡張PRAM) による理論解析と実測が一致
- r ≥ 3 で既存GPU手法を最大14倍上回る性能
- 省エネ性能も r ≥ 5 で最も優位

**実装**: https://github.com/temporal-hpc/CAT (CUDA C/C++)

CATの手法は、CAの計算パターンが本質的に行列演算に似ていることを利用しており、
本プロジェクトのハードウェア設計においても重要な参考事例となる。

### 1.1.2 汎用GPU上のCA実装

| 手法 | フレームワーク | 特徴 |
|---|---|---|
| WebGPU Compute Shaders | WebGPU | 各セルに1スレッド。ping-pongバッファ。連続状態CA(SmoothLife/Primordia)対応 |
| Barracuda | CUDA C/C++ | Turing完全なGPU VM。動的パラメータ変更対応。Rule 110 CAで検証 |
| gpca | Rust + wgpu | 非同期ハイパーグラフCA。WebGPUバックエンド。レイトレーシング対応可 |
| マルチGPU CA | CUDA + MPI | 領域分割＋ハロー交換。V100 4GPUクラスタでスケーラビリティ実証 |

### 1.1.3 セル・オートマトンモデルのGPU実装における課題

1. **メモリアクセスパターン**: CAの近傍参照はストライドアクセスになりがち。共有メモリ/テクスチャメモリの活用が重要
2. **分岐**: 複雑な遷移関数はwarp divergenceを引き起こす
3. **大半径**: 半径rが大きくなるとメモリフットプリントがO(r^2)で増加。CATはこの問題をTensor Coreで解決
4. **精度**: 連続状態CA (例: SmoothLife, Cyclic CA) では浮動小数点精度が動作に影響

---

## 1.2 Posit Number System

### 1.2.1 概要

Posit (Unum Type III) は John L. Gustafson によって2017年に提案された浮動小数点数の代替形式。
形式: `Posit(n, es)` — nビット総長、esビット固定指数部

**ビットフィールド**:
```
| S | regime (可変長) | exponent (es bits) | fraction (残り) |
```

- **regime**: 可変長。連続する0または1の個数で`useed^k` を表現 (`useed = 2^2^es`)
- **exponent**: 通常の2進指数 (固定esビット)
- **fraction**: 残りのビット。数値が1に近いほど長い仮数部を得られる (tapered precision)

**IEEE 754 との比較**:

| 特性 | IEEE binary32 | Posit(32,2) |
|---|---|---|
| 最大精度付近 | 1.0 | 1.0 |
| 仮数ビット (1付近) | 23 bits | 26 bits |
| 最大値 | ~3.4×10^38 | ~2.4×10^36 |
| 最小正規化数 | ~1.2×10^-38 | ~1.4×10^-38 |
| NaN | あり | なし (代わりにNaR) |
| 無限大 | あり | なし |
| 演算精度 | binary32比基準 | 約0.5-1.0桁高い |

### 1.2.2 ハードウェア実装状況

**SoftPosit** (Cリファレンス): https://gitlab.com/cerlane/SoftPosit
- POSIT標準ソフトウェアライブラリ
- GPU (CUDA/OpenCL) に移植済み (Nakasato et al., 2024)
- 32/64ビット整数命令のみで実装可能 → GPUでも実行可能

**cuPosit** (CUDA Posit GEMM): https://github.com/zeroby0/cuPosit
- PyTorch用バッチドストライドPosit行列乗算
- Posit(16,2) で QAT (Quantization-Aware Training) 対応
- 4-28bit, es=2対応
- 4 TOPS (FP32比1/8〜1/10の速度)

**SPADE** (SIMD Posit MAC):
- マルチプレシジョンPosit MAC (8/16/32bit)
- FPGA実証済み (Xilinx Virtex-7)
- Posit(8,0): LUT 45.13%削減, Slice 80%削減
- ASIC: 1.38 GHz @ 6.1 mW (28nm)
- レジーム認識・レーン融合型SIMDデータパス

**PVU** (RISC-V Posit Vector Unit):
- Chisel設計。ベクトル加減乗除・ドット積対応
- RISC-V V拡張命令セットをカスタム
- 65,407 LUTs, 108 Muxes

**PERCIVAL / Big-PERCIVAL**:
- RISC-VコアにPosit FPUを統合。32/64bit対応
- 1024bit quire (完全精度積算レジスタ) 搭載
- 64bit PAU: FPU比2.5xリソース。quireによりHWコスト増

**Pacogen**: パラメタ化可能なPosit演算器ジェネレータ (SystemVerilog)
**PHAc**: Posit加算器/乗算器の省面積FPGA実装

### 1.2.3 Quire (完全精度積算レジスタ)

Positの特長的な機能。`quire` は固定小数点完全精度アキュムレータ。
- Posit(32,2) では最大 16×32 = 512 bits相当の完全精度
- ドット積 `Σ(a_i × b_i)` の中間丸め誤差を完全排除
- Catalyst: CAの重み付き総和 `Σ(w_i × s_i)` に直接適用可能

---

## 1.3 既存CAアクセラレータ/専用ハードウェア

### 1.3.1 専用CAチップ

- **CAACC** (Cellular Automata Accelerator): FPGAベースのCA専用機。2D/3D Game of Life高速化
- **Moore's Law アクセラレータ**: CAM (Content-Addressable Memory) ベースのCAルール適用
- 大半は固定小数点かIEEE 754浮動小数点を使用。Posit採用例は無し

### 1.3.2 CA × Posit の未開拓領域

現時点で、**CA専用ハードウェアにPosit数形式を採用した研究・実装は存在しない**。
以下の理由から、本プロジェクト (Gridion) は新規性の高いテーマとなる:

1. **重み付き総和へのQuire適用**: CA遷移関数の近傍重み付き総和に quire を適用する例は無い
2. **連続状態CAの精度**: SmoothLife など連続状態CAでのPosit精度評価は未実施
3. **CA的PEアレイ × Posit演算器**: 格子状PEアレイ各演算器にPositを採用した設計は無い
4. **テーパード精度のCAへの効果**: CAシミュレーションにおけるPositの tapered precision の有効性検証は未実施

### 1.3.3 CAとTensor Core / Systolic Arrayの類似性

| 要素 | Tensor Core / Systolic Array | CAアクセラレータ |
|---|---|---|
| 基本演算 | MMA (行列積和) | 近傍重み付き総和 |
| PE結合 | 2D固定配線 | 2D近傍結合 |
| データフロー |  systolic | ステンシル |
| 精度要件 | 低〜中 (DNN) | 中〜高 (科学計算) |
| 使用数形式 | FP16/BF16/INT8 | FP32/FP64 (現状) → Posit (提案) |

---

## 1.4 まとめ

本サーベイから以下の知見を得た:

1. **CAT**はTensor CoreをCAに転用する先駆的研究。CA特有の計算パターン（重み付き総和）がハードウェアアクセラレーションに適することを示した
2. **Posit**はIEEE 754に代わる有望な数形式。特に `quire` による完全精度積算はCAの近傍総和計算に極めて適合的
3. **既存のCA専用HWにPosit採用例は無く、本研究領域は未開拓**
4. **SPADE/PVU**のSIMD Positデータパス設計は、CA PEアレイの各演算器設計に直接応用可能
5. **cuPosit**は既存GPUでPosit演算が実行可能であることを実証

以上の背景を踏まえ、Gridionは「Posit数形式を用いたCA専用アクセラレータ」という新規性のあるアーキテクチャを提案する。

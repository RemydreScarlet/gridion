# 06: Evaluation Plan

## 6.1 評価の全体像

Gridionアーキテクチャの評価は以下の4軸で行う:

| 軸 | 指標 | 比較対象 |
|---|---|---|
| 精度 | シミュレーション誤差、長期安定性 | binary32, binary64, Posit(32,2)ソフトウェア |
| 性能 | step/秒, セル更新/秒, スループット | GPU実装 (CAT, CUDA baseline), CPU実装 |
| 面積 | LUT, FF, DSP, BRAM使用率 | 理論見積り, 近似FPU |
| 電力 | 動的電力, エネルギー/step | FPGA計測, GPU (TDP比) |

## 6.2 フェーズ別評価計画

### Phase 1: ソフトウェアシミュレーション (Verilog/C++ Modeling)

**目的**: アーキテクチャの機能検証と精度評価

**項目**:
1. **Posit CAシミュレータ (C++/Python)**
   - SoftPosit ライブラリを使用したCAシミュレータ
   - 同一CAモデルを binary32, binary64, Posit(32,2), Posit(16,1) で実行
   - 比較指標: 長期誤差 (Nステップ後のbinary64との差)

2. **Gridionビヘイビアモデル (SystemC/Verilog)**
   - PEの動作をサイクル精度でモデル化
   - ISAの機能検証
   - 各CAルールの実行サイクル数測定

**ベンチマークCA**:
- Conway's Game of Life (離散状態, 2値)
- Cyclic CA (離散状態, 多値)
- SmoothLife / Primordia (連続状態, 浮動小数点)
- Gray-Scott 拡散反応モデル (連続状態, 偏微分方程式系)
- Larger Than Life (大半径CA)

### Phase 2: RTL設計とFPGAプロトタイピング

**目的**: 面積・周波数・電力の実測

**項目**:
1. **PE単体RTL設計 (SystemVerilog/Chisel)**
   - Posit Decode/Encode (16/32bit)
   - Posit乗算器 (8並列)
   - Quire (128/512bit)
   - 遷移関数演算器 (加算/乗算/比較)
   - マイクロシーケンサ

2. **8×8アレイ統合**
   - 近傍配線の自動生成
   - グローバル制御ユニット
   - 境界処理
   - ホストインタフェース

3. **FPGA合成・実装**
   - ターゲット: Xilinx Alveo U280 / Kintex-7 / Artix-7 (小規模検証)
   - 指標: LUT/FF/DSP/BRAM使用率, 最大動作周波数, 動的電力

**合成見積り目標**:

| コンフィグ | ターゲットFPGA | 目標周波数 | 目標LUT使用率 |
|---|---|---|---|
| Posit(16,1), 8×8 | Alveo U280 | 200 MHz | < 50% |
| Posit(32,2), 4×4 | Alveo U280 | 150 MHz | < 60% |
| Posit(16,1), 4×4 | Kintex-7 | 150 MHz | < 50% |

### Phase 3: 性能評価

**目的**: 実効性能の測定と既存手法との比較

**指標**:
- **Mcell/s** (Million cell updates per second): `(64 × f) / cycle_per_step`
  - 8×8 = 64 cells
  - f = 動作周波数
  - cycle_per_step = ISAマイクロコード実行サイクル数

**予測性能**:

| コンフィグ | 周波数 | Game of Life | SmoothLife | Gray-Scott |
|---|---|---|---|---|
| Posit(16,1), 8×8 | 200 MHz | 853 Mcell/s | 512 Mcell/s | 365 Mcell/s |
| Posit(32,2), 4×4 | 150 MHz | 160 Mcell/s | 96 Mcell/s | 68 Mcell/s |

**比較対象**:
- CPU (Intel i9-13900K): 単一コアで ~50 Mcell/s (GoL推定)
- GPU (NVIDIA RTX 4090): ~10,000 Mcell/s (GoL, 単純CUDA実装)
- CAT (RTX 4090): 大半径r≥8で特に高速
- **Gridionの優位性**: 小規模/省電力/確定的レイテンシ/高精度

### Phase 4: 精度比較実験

**プロトコル**:
1. Gold standard: binary64 CPUシミュレーション (1024ステップ)
2. 比較対象: binary32, Posit(32,2), Posit(16,1) 各200ステップ
3. 誤差測定: 各ステップの状態のRMS誤差 (Gold standard比)
4. 長期安定性: 500+ステップでの誤差発散の有無

**予測結果**:

| CAモデル | FP32 RMS誤差 (200step) | P32,2 RMS誤差 | P16,1 RMS誤差 |
|---|---|---|---|
| Game of Life (2値) | 0% | 0% | 0% |
| Cyclic CA (多値) | 0.01% | 0.005% | 0.1% |
| SmoothLife | 0.5% | 0.1% | 2.0% |
| Gray-Scott | 1.0% | 0.3% | 5.0% |
| CML (カオス) | 発散 | 5x長く安定 | 発散早い |

*注意: カオス的なCMLでは初期の小さな誤差が指数的に増幅。Posit(32,2)の精度向上は安定時間の延長に寄与。*

## 6.3 比較評価フレームワーク

### 6.3.1 共通ベンチマークセット

```python
benchmarks = {
    "gol":      {"type": "binary",   "states": 2,  "radius": 1},
    "highlife": {"type": "binary",   "states": 2,  "radius": 1},
    "seeds":    {"type": "binary",   "states": 2,  "radius": 1},
    "cyclic":   {"type": "discrete", "states": 8,  "radius": 1},
    "smooth":   {"type": "continuous", "states": inf, "radius": 8},
    "grayscott": {"type": "reaction-diffusion", "fields": 2, "radius": 1},
    "cml":      {"type": "coupled-map", "states": inf, "radius": 1},
    "ltl_majority": {"type": "LTL", "states": 2, "radius": 5},
}
```

### 6.3.2 測定項目

**精度**:
```
RMS_error(t) = sqrt( mean( (s_gold(t) - s_test(t))^2 ) )
Max_error(t) = max |s_gold(t) - s_test(t)|
```

**性能**:
```
Mcell_s = (num_cells × num_steps) / execution_time
Energy_per_step = power(W) / (steps/sec)
```

## 6.4 リスク評価と対策

| リスク | 確率 | 影響 | 対策 |
|---|---|---|---|
| Posit(32,2) PE面積过大 → FPGAに収まらない | 中 | 高 | Posit(16,1)優先。または4×4サブアレイ |
| Posit演算器の動作周波数が目標に届かない | 中 | 中 | パイプライン段数増加、レジスタ挿入 |
| Quireの面積オーバーヘッドが想定以上 | 高 | 中 | 共有quire、時分割、またはquire省略モード |
| CA遷移関数の専用演算器で表現力不足 | 低 | 高 | 命令追加、またはLUTモード併用 |
| 近傍配線のルーティング混雑 | 低 | 中 | H-tree配線、パイプライン段挿入 |

## 6.5 マイルストーン

| # | マイルストーン | 時期 (予定) | 成果物 |
|---|---|---|---|
| M1 | 設計ドキュメント完了 | Week 1-2 | docs/arch/*.md |
| M2 | ソフトウェアPosit CAシミュレータ | Week 3-4 | C++/Pythonシミュレータ + 精度評価結果 |
| M3 | PE単体RTL設計完了 | Week 5-8 | SystemVerilog PEコア + 単体テスト |
| M4 | 8×8アレイRTL設計完了 | Week 9-10 | 統合RTL + シミュレーション検証 |
| M5 | FPGA合成・実装 | Week 11-12 | ビットストリーム + 面積/電力レポート |
| M6 | 性能評価・論文執筆 | Week 13-16 | 評価結果 + テクニカルレポート/論文 |

## 6.6 評価環境

**ソフトウェア**:
- SoftPosit (Posit演算リファレンス)
- Verilator / Vivado Simulator (RTLシミュレーション)
- Python + NumPy (精度解析)
- CUDA (GPU比較ベンチマーク)

**ハードウェア**:
- FPGA: Xilinx Alveo U280 または Kintex-7 KC705
- GPU: NVIDIA RTX 3090/4090 (比較用)
- CPU: Intel i9-13900K (比較用)

**ツール**:
- Vivado / Vitis (合成・実装)
- QuestaSim / Verilator (RTLシミュレーション)
- Chisel / FIRRTL (ハードウェア生成、必要に応じて)

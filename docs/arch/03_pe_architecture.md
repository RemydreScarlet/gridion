# 03: Processing Element (PE) Architecture

## 3.1 Overview

各PEはセル・オートマトンの1セルに相当。以下の機能を備える:

1. 状態保持 (Positレジスタ)
2. 近傍値の受信 (Moore 8近傍)
3. 重み付き総和の計算 (Posit MAC + quire)
4. 遷移関数の適用 (専用演算器: 加算/乗算/比較)
5. 次状態の出力

## 3.2 PE Block Diagram

```
+----------------------------------------------------------+
|  PE (x, y)                                                |
|                                                            |
|  近傍入力 (8方向 + 自セル)                                 |
|  +--+--+--+                                               |
|  |N |NW|W |  ... → Posit Decode (8並列)                   |
|  +--+--+--+        ↓                                       |
|  |NE|S |SE|  → 重み ROM (8×Posit値)                       |
|  +--+--+--+        ↓                                       |
|  |E |SW|自 |  →  Posit Multiplier Array (8並列)           |
|  +--+--+--+        ↓                                       |
|                  →  Quire Accumulator (完全精度総和)        |
|                     ↓                                       |
|                  →  Posit Rounding (quire → Posit)         |
|                     ↓                                       |
|  +------------------------------------------------------+  |
|  |  Transition Function Datapath                        |  |
|  |  +----------+  +----------+  +----------+           |  |
|  |  | Posit    |  | Posit    |  | Posit    |           |  |
|  |  | Adder    |  | Multiplier|  | Comparator|          |  |
|  |  +----------+  +----------+  +----------+           |  |
|  |       ↓              ↓              ↓               |  |
|  |  +--------------------------------------------+    |  |
|  |  |  Crossbar / MUX (演算器の組合せ制御)       |    |  |
|  |  +--------------------------------------------+    |  |
|  +------------------------------------------------------+  |
|                     ↓                                       |
|  +------------------------------------------------------+  |
|  |  State Register File (Posit形式、現状態+次状態)     |  |
|  +------------------------------------------------------+  |
|                     ↓                                       |
|  近傍出力 (8方向 + 自セル) → 隣接PEへ送信                  |
+----------------------------------------------------------+
```

## 3.3 Posit Decode / Encode

### 3.3.1 Decode (Posit → 内部表現)

入力 Posit ビット列から sign, regime, exponent, fraction を抽出:

```
Algorithm:
1. S = bit[n-1]
2. if S=1: two's complement invert (絶対値化)
3. LZD (Leading Zero Detection) で regime 長を検出
   - regime値 k = (先頭連続0の個数) (regimeビットが0で始まる場合)
   - regime値 k = -(先頭連続1の個数) (regimeビットが1で始まる場合)
4. exponent = 残りビットから es ビット抽出
5. fraction = 残り全ビット (暗黙の1含む)
6. 内部表現 (sign, exp, mantissa) に変換
```

**HW構成**:
- LZD (Leading Zero Detector): 32/16ビット優先度エンコーダ
- Barrel Shifter: regime抽出後のビットシフト
- 2's complement回路: 負数処理

### 3.3.2 Encode (内部表現 → Posit)

```
Algorithm:
1. regime値から regimeビット列を生成
2. exponent を es ビットで付加
3. fraction を残りにパック
4. 丸め処理 (round-to-nearest-even)
5. sign ビットを付加
```

**HW構成**:
- Barrel Shifter: fraction正規化
- 丸め加算器
- 2's complement回路 (負数時)

### 3.3.3 Posit(16,1) と Posit(32,2) の両対応

両フォーマットに対応するため、Decode/Encode はパラメタ化:

```
module posit_decode #(
    parameter N = 32,  // 総ビット数 (16 or 32)
    parameter ES = 2   // 指数ビット数 (1 or 2)
) (
    input  [N-1:0] posit_in,
    output        sign,
    output [5:0]  exp,      // 統合指数 (regime + exponent)
    output [N-1:0] mantissa // 仮数 (leading bit含む)
);
```

レジーム長の最大値は N-1 なので、LZDとシフタのビット幅は N に依存。
32ビットと16ビットでは回路規模が約2倍異なる。

## 3.4 Posit Multiplier Array (8並列)

Moore 8近傍 + 自セルの計9個の値と重みの乗算を実行。

| 要素 | 入力1 | 入力2 | 出力 |
|---|---|---|---|
| MUL0 | s(x-1,y-1) | w_NW | p0 |
| MUL1 | s(x,  y-1) | w_N  | p1 |
| MUL2 | s(x+1,y-1) | w_NE | p2 |
| MUL3 | s(x-1,y)   | w_W  | p3 |
| MUL4 | s(x,  y)   | w_self | p4 |
| MUL5 | s(x+1,y)   | w_E  | p5 |
| MUL6 | s(x-1,y+1) | w_SW | p6 |
| MUL7 | s(x,  y+1) | w_S  | p7 |
| MUL8 | s(x+1,y+1) | w_SE | p8 |

各MULは Posit乗算器。内部演算は固定小数点で行い、結果は完全精度 (2N bits) を保持。
(Posit乗算は仮数同士の乗算なので、2倍のビット幅が必要)

**重みROM**: 9エントリ × Posit幅。遷移関数ごとに書換可能。

## 3.5 Quire Accumulator

9個の乗算結果を完全精度で加算:

```
quire = Σ p_i  (i = 0..8)
```

### 3.5.1 Quire ビット幅 (Posit(32,2)の場合)

- 各乗算結果: 最大 64 bits (2N)
- 9個の総和: 64 + log2(9) ≈ 68 bits
- ただし、Big-PERCIVALの設計に従い安全マージン含め 512 bits

**Posit(16,1)の場合**:
- 各乗算結果: 32 bits
- 総和: 32 + log2(9) ≈ 36 bits
- 安全マージン含め 128 bits

### 3.5.2 Quire 操作

| 操作 | 説明 |
|---|---|
| `clear` | quireをゼロにリセット |
| `accum(p)` | 乗算結果pを加算 |
| `round()` | quire値をPositに丸め出力 |
| `load(val)` | 外部からquireにロード |

### 3.5.3 Quire共用の設計判断

各PEに個別のquireを持つ構成が基本。
ただし、面積制約が厳しい場合:
- 4PEで1つのquireを時分割共有
- 総和 → 丸め → 遷移関数 のパイプラインでquire使用率を向上

## 3.6 Transition Function Datapath

**選択: 専用演算器方式** (加算器/乗算器/比較器)

### 3.6.1 演算器セット

| 演算器 | レイテンシ | 説明 |
|---|---|---|
| Posit Add/Sub | 3-5 cycle | FP加算/減算。Posit decode→仮数加算→encode |
| Posit Mul | 3-5 cycle | FP乗算。decode→仮数乗算→encode |
| Posit Compare | 1-2 cycle | 大小比較。特殊値(NaR)検出 |
| Posit FMA | 5-7 cycle | 融合積和 (a×b+c) |
| MUX/Crossbar | 1 cycle | 演算器間のデータパス切替 |

### 3.6.2 遷移関数の具体例

**例1: Game of Life**
```
sum = Σ近傍(8方向)
next = (sum == 3) || (state == 1 && sum == 2)
```
HW: quire総和 → Posit Compare (2,3と比較) → Logic → MUX

**例2: 拡散反応CA (連続状態)**
```
laplacian = Σ w_i·s_i - s_self
next = s_self + D·laplacian + R(s_self)
```
HW: quire総和 → Posit Sub → Posit Mul (D乗算) → Posit Add (R関数適用)

**例3: 結合写像格子 (CML)**
```
next = (1-ε)·f(s_self) + ε/N·Σ f(s_neighbor)
```
HW: quire総和 → Posit Mul → Posit Add → Posit FMA

### 3.6.3 マイクロプログラム制御

遷移関数の種類に応じて、演算器の接続と実行順を制御するマイクロプログラム:

```
# Game of Life 遷移関数のマイクロコード例
# sum = quire_total (neighbors only, no self)

INST  OP       DST   SRC1   SRC2   备注
0     QU_ACC   Q     -      -       Load neighbor sum into quire
1     QU_RND   S0    Q      -       Round quire to Posit → S0
2     CMP_EQ   C1    S0     3       S0 == 3?
3     CMP_EQ   C2    STATE  1       STATE == 1?
4     CMP_EQ   C3    S0     2       S0 == 2?
5     AND      C4    C2     C3      (STATE==1 && S0==2)
6     OR       C5    C1     C4      final condition
7     MUX      NEXT  STATE  S0      C5 ? STATE : ... wait
8     MUX      NEXT  STATE  C5      活着/死亡
9     ST       NEXT  -      -       Store next state
```

## 3.7 状態レジスタファイル

| レジスタ | 幅 | 説明 |
|---|---|---|
| `STATE` | Posit幅 | 現在のセル状態 |
| `NEXT` | Posit幅 | 計算中の次状態 |
| `QUIRE` | quire幅 | 完全精度積算器 |
| `TMP0-3` | Posit幅 | 作業用テンポラリ |
| `WEIGHT[0:8]` | Posit幅×9 | 重みテーブル (書換可能) |
| `THRESH` | Posit幅 | 閾値レジスタ |
| `MODE` | 4bit | 遷移関数モード選択 |

## 3.8 PE パイプライン

| ステージ | サイクル | 内容 |
|---|---|---|
| S1 (Fetch) | 1 | 近傍値を受信、Posit decode |
| S2 (Mul) | 1-2 | 重み乗算 (9並列) |
| S3 (Accum) | 1-2 | Quire加算 |
| S4 (Round) | 1 | Quire → Posit丸め |
| S5 (Trans) | 1-5 | 遷移関数 (演算器使用) |
| S6 (Write) | 1 | 次状態レジスタ書込み |

合計: 6-12 cycle/step (遷移関数の複雑さに依存)
パイプライン深層化によりスループット 1 step/cycle を目標。

## 3.9 面積見積り (予備)

### Posit(32,2) PE

| コンポーネント | LUT概算 | FF概算 |
|---|---|---|
| Posit Decode ×9 | 4500 | 900 |
| Posit Mul ×9 | 5400 | 2700 |
| Quire (512bit) | 2000 | 1000 |
| Posit Encode | 500 | 200 |
| 遷移関数演算器群 | 3000 | 1500 |
| レジスタファイル | 500 | 1000 |
| 制御 | 500 | 500 |
| **合計 (1PE)** | **~16400** | **~7800** |

### Posit(16,1) PE

| コンポーネント | LUT概算 | FF概算 |
|---|---|---|
| Posit Decode ×9 | 1500 | 400 |
| Posit Mul ×9 | 1800 | 900 |
| Quire (128bit) | 500 | 300 |
| Posit Encode | 200 | 100 |
| 遷移関数演算器群 | 1000 | 500 |
| レジスタファイル | 200 | 500 |
| 制御 | 300 | 300 |
| **合計 (1PE)** | **~5500** | **~3000** |

### 8×8 アレイ合計 (Posit(32,2))

| 項目 | 見積り |
|---|---|
| PE 64個 | 1,049,600 LUT / 499,200 FF |
| 配線/ルーティング | +30% |
| グローバル制御 | ~5000 LUT |
| メモリIF | ~10000 LUT |
| **合計** | **~1.4M LUT** |

参考: Xilinx Alveo U280 = ~1.3M LUT。 **FPGA 1枚ではやや厳しい**。

### 8×8 アレイ合計 (Posit(16,1))

| 項目 | 見積り |
|---|---|
| PE 64個 | 352,000 LUT / 192,000 FF |
| 配線/ルーティング | +30% |
| グローバル制御 | ~5000 LUT |
| メモリIF | ~10000 LUT |
| **合計** | **~470K LUT** |

参考: Xilinx Alveo U280 = ~1.3M LUT。 **Posit(16,1)なら十分収まる**。

## 3.10 設計の自由度

Gridion PEアーキテクチャは以下のパラメタを調整可能:

| パラメタ | 候補値 | トレードオフ |
|---|---|---|
| Positビット幅 N | 16, 32 | 精度 vs 面積 |
| 指数ビット ES | 1, 2 | ダイナミックレンジ vs 精度 |
| Quire有無 | on/off | 精度 vs 面積 (最大30%増) |
| 近傍数 | 4~25 | 表現力 vs 面積 |
| 演算器構成 | 最小(FMAのみ)〜最大(加算+乗算+比較) | 柔軟性 vs 面積 |
| パイプライン段数 | 4~10 | 周波数 vs レイテンシ |

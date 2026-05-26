# 05: Instruction Set Architecture

## 5.1 Design Philosophy

GridionのISAは以下の原則に基づく:

1. **CA遷移関数に特化**: 汎用プロセッサではなくCA計算専用
2. **専用演算器を直接制御**: 加算/乗算/比較器をマイクロコードで制御
3. **PE内ローカル**: 命令はPE内でクローズ。アレイ全体の制御は別
4. **簡潔性**: 命令数は最小限 (〜20命令)

## 5.2 命令フォーマット

```
Opcode (6) | Dst (4) | Src1 (4) | Src2 (4) | Imm (N)
```

- Opcode: 6 bits (最大64命令)
- Dst: 4 bits (16個の内部レジスタ)
- Src1, Src2: 4 bits each
- Imm: N bits (Posit幅、即値用)

### 5.2.1 レジスタマップ

| ID | 名前 | 幅 | 説明 |
|---|---|---|---|
| 0 | `R0` | Posit | 汎用テンポラリ |
| 1 | `R1` | Posit | 汎用テンポラリ |
| 2 | `R2` | Posit | 汎用テンポラリ |
| 3 | `R3` | Posit | 汎用テンポラリ |
| 4 | `STATE` | Posit | 現状態 (読出し専用) |
| 5 | `NEXT` | Posit | 次状態 (書込み専用) |
| 6 | `NEIGH_SUM` | Posit | 近傍総和 (quire→round) |
| 7 | `QUIRE` | quire幅 | 完全精度積算器 |
| 8 | `WT[0]` | Posit | 重み0 |
| 9 | `WT[1]` | Posit | 重み1 |
| 10 | `WT[2]` | Posit | 重み2 |
| 11 | `WT[3]` | Posit | 重み3 |
| 12 | `WT[4]` | Posit | 重み4 |
| 13 | `WT[5]` | Posit | 重み5 |
| 14 | `WT[6]` | Posit | 重み6 |
| 15 | `WT[7]` | Posit | 重み7 |

## 5.3 命令一覧

### 5.3.1 近傍総和命令

| 命令 | Opcode | 書式 | 説明 |
|---|---|---|---|
| `NEIGH_LOAD` | 0x01 | `NEIGH_LOAD Q` | Moore 8近傍の値をquireにロード (重み乗算＋総和) |
| `QU_ACC` | 0x02 | `QU_ACC Q` | quireに追加加算 (複数セル用) |
| `QU_RND` | 0x03 | `QU_RND Rd` | quire値をPositに丸めてRdに格納 |
| `QU_CLR` | 0x04 | `QU_CLR` | quireをゼロクリア |
| `QU_LD` | 0x05 | `QU_LD Q, Rs` | Rsの値をquireにロード (スカラー加算用) |

### 5.3.2 算術演算命令

| 命令 | Opcode | 書式 | 説明 |
|---|---|---|---|
| `ADD` | 0x10 | `ADD Rd, Rs1, Rs2` | Posit加算: Rd = Rs1 + Rs2 |
| `SUB` | 0x11 | `SUB Rd, Rs1, Rs2` | Posit減算: Rd = Rs1 - Rs2 |
| `MUL` | 0x12 | `MUL Rd, Rs1, Rs2` | Posit乗算: Rd = Rs1 × Rs2 |
| `FMA` | 0x13 | `FMA Rd, Rs1, Rs2, Rs3` | 融合積和: Rd = Rs1×Rs2 + Rs3 |
| `NEG` | 0x14 | `NEG Rd, Rs` | 符号反転: Rd = -Rs |
| `ABS` | 0x15 | `ABS Rd, Rs` | 絶対値: Rd = \|Rs\| |

### 5.3.3 比較命令

| 命令 | Opcode | 書式 | 説明 |
|---|---|---|---|
| `CMP_EQ` | 0x20 | `CMP_EQ Rd, Rs1, Rs2` | Rd = (Rs1 == Rs2 ? 1.0 : 0.0) |
| `CMP_NE` | 0x21 | `CMP_NE Rd, Rs1, Rs2` | Rd = (Rs1 != Rs2 ? 1.0 : 0.0) |
| `CMP_LT` | 0x22 | `CMP_LT Rd, Rs1, Rs2` | Rd = (Rs1 < Rs2 ? 1.0 : 0.0) |
| `CMP_GT` | 0x23 | `CMP_GT Rd, Rs1, Rs2` | Rd = (Rs1 > Rs2 ? 1.0 : 0.0) |
| `CMP_LE` | 0x24 | `CMP_LE Rd, Rs1, Rs2` | Rd = (Rs1 ≤ Rs2 ? 1.0 : 0.0) |
| `CMP_GE` | 0x25 | `CMP_GE Rd, Rs1, Rs2` | Rd = (Rs1 ≥ Rs2 ? 1.0 : 0.0) |

比較結果は Posit の 1.0 (真) または 0.0 (偽) で表現。

### 5.3.4 論理・選択命令

| 命令 | Opcode | 書式 | 説明 |
|---|---|---|---|
| `AND` | 0x30 | `AND Rd, Rs1, Rs2` | 論理積 (比較結果用) |
| `OR` | 0x31 | `OR Rd, Rs1, Rs2` | 論理和 (比較結果用) |
| `NOT` | 0x32 | `NOT Rd, Rs` | 論理否定 |
| `MUX` | 0x33 | `MUX Rd, Rs_cond, Rs_T, Rs_F` | Rd = (Rs_cond != 0) ? Rs_T : Rs_F |
| `MIN` | 0x34 | `MIN Rd, Rs1, Rs2` | 最小値 |
| `MAX` | 0x35 | `MAX Rd, Rs1, Rs2` | 最大値 |

論理演算は比較結果(1.0/0.0)をPosit値として扱う。

### 5.3.5 データ移動命令

| 命令 | Opcode | 書式 | 説明 |
|---|---|---|---|
| `MOV` | 0x40 | `MOV Rd, Rs` | レジスタ間コピー |
| `LDI` | 0x41 | `LDI Rd, Imm` | 即値ロード |
| `LD_STATE` | 0x42 | `LD_STATE Rd` | STATE → Rd |
| `ST_NEXT` | 0x43 | `ST_NEXT Rs` | Rs → NEXT (次状態出力) |

## 5.4 マイクロプログラム例

### 5.4.1 Game of Life

```
# Conway's Game of Life
# sum = 近傍8セルの総和 (自セルは含まない)
# next = (sum==3) || (state==1 && sum==2)

NEIGH_LOAD Q          # Q = Σ(w_i·s_i), w_self=0
QU_RND    R0          # R0 = 近傍総和 (rounded)
CMP_EQ    R1, R0, #3  # R1 = (R0==3)
LD_STATE  R2          # R2 = current state
CMP_EQ    R3, R2, #1  # R3 = (state==1)
CMP_EQ    R4, R0, #2  # R4 = (R0==2)
AND       R5, R3, R4  # R5 = (state==1 && sum==2)
OR        R6, R1, R5  # R6 = final condition
MUX       R7, R6, #1, #0  # R7 = condition ? 1 : 0
ST_NEXT   R7          # next = R7
```

**命令数: 11 | 推定サイクル数: ~15**

### 5.4.2 SmoothLife (連続状態CA)

```
# SmoothLife 簡略版
# next = s + dt·(growth(s, inner, outer) - decay·s)
# inner = Σ w_inner·s_neighbor (半径r1)
# outer = Σ w_outer·s_neighbor (半径r2)

# Step 1: Calculate inner sum
LDI       R0, #dt
NEIGH_LOAD Q          # デフォルト重みでinner総和
QU_RND    R1          # R1 = inner

# Step 2: Calculate outer sum (別の重みセット)
# 重み切替 (グローバル制御)
NEIGH_LOAD Q
QU_RND    R2          # R2 = outer

# Step 3: Growth function
SUB       R3, R1, R2  # R3 = inner - outer
MUL       R4, R3, R0  # R4 = dt·(inner-outer)

# Step 4: Decay
LDI       R5, #decay
LD_STATE  R6          # R6 = current state
MUL       R7, R5, R6  # R7 = decay·s
SUB       R8, R4, R7  # R8 = dt·(inner-outer) - decay·s

# Step 5: Update
ADD       R9, R6, R8  # R9 = s + Δs
CLAMP     R9, #0, #1  # clamp to [0,1]
ST_NEXT   R9
```

**命令数: 18 | 推定サイクル数: ~25**

### 5.4.3 拡散反応CA (Gray-Scott)

```
# Gray-Scott model
# u_next = u + Du·∇²u - u·v² + F·(1-u)
# v_next = v + Dv·∇²v + u·v² - (k+F)·v

# uとvは2つのSTATEレジスタに保持 (u=R0, v=R1)
# 実際は隣接PEからuとvの両方を受信する必要あり
# 以下は簡略化したuの更新

NEIGH_LOAD Q          # 近傍uの総和
QU_RND    R2          # R2 = Σu_neighbor
SUB       R2, R2, R0  # R2 = ∇²u ≈ Σu_neighbor - 4·u (5-point stencil)
LDI       R3, #Du
MUL       R4, R2, R3  # R4 = Du·∇²u

# ... (v², F, k 項は同様に計算)
```

## 5.5 重み構成

```c
// 各PEの重みテーブル構成 (Moore 8近傍 + 自セル)
struct weight_table {
    posit w_self;  // 自セル重み (通常0 or 1)
    posit w_nw;    // (-1,-1)
    posit w_n;     // ( 0,-1)
    posit w_ne;    // (+1,-1)
    posit w_w;     // (-1, 0)
    posit w_e;     // (+1, 0)
    posit w_sw;    // (-1,+1)
    posit w_s;     // ( 0,+1)
    posit w_se;    // (+1,+1)
};
```

- 重みテーブルはグローバル制御ユニットからブロードキャストで全PE同時更新
- CAルール変更時のみ書換 (シミュレーション中は固定)

## 5.6 CAルールライブラリ (将来)

以下の標準CAルールをマイクロコードのライブラリとして提供:

| CAルール | 命令数 | 推定サイクル |
|---|---|---|
| Game of Life (Conway) | 11 | ~15 |
| HighLife | 12 | ~16 |
| Seeds | 8 | ~12 |
| SmoothLife | 18 | ~25 |
| Cyclic CA | 10 | ~14 |
| 拡散反応 (Gray-Scott) | 25 | ~35 |
| 結合写像格子 (CML) | 15 | ~20 |
| Larger Than Life (LTL) | 14 | ~20 |
| 木村のセルオートマトン | 12 | ~17 |

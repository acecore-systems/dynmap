# Minecraft 26.2 候補

Issue #2 の対象は Paper / Spigot 26.2 の隔離検証用候補です。自動本番反映や一般向けJAR配布は行いません。
R2マーカー修正 (#1) はこの差分に含めず、ビルド基盤 (#3) を前提にします。

## 上流と採否

- 上流PR: https://github.com/webbukkit/dynmap/pull/4271 （取得時は未マージ）
- 取得head: `f87c4dda5b7feea9ff60d68f96475d73d3eafd91`
- 採用元ソース: `46ba070e03267d942ff542371a91c6ca67e37c0e`
- 基点: `93b454efb8802dc7406d6873434f2aeec5c636f4`

新helper、NBT/チャンク読み取り、ブロックモデル・テクスチャのソースを取り込みました。
上流PRの `Plugin/*.jar`、Fabricの新プラットフォーム、Forgeビルドの削除・移動、
作業用指示ファイルは取り込みません。Apache-2.0と既存の著作権・商標表示を維持し、
追加PNGは上流PRのMinecraft用標準テクスチャ資材と同じものです。
ソースの公開とバイナリの一般配布は別に判断します。

Acecoreでは以下を調整しています。

- バージョン判定は26.2に限定し、旧サーバーの既存fallbackを維持。
  `(MC: ...)` がない版表記ではBukkit API版を読み、共有コアが1.0.0と誤認するのを防ぐ。
- dev-bundleの動的指定を `26.2.build.105-stable` に固定。
- Java 25 / Gradle 9.5.1 / Shadow 9.6.0 / paperweight 2.0.0-beta.21 は候補用。
  元のGradle 8.14 / Shadow 8.1.7は既存ビルド用に維持。
- 共通ビルド処理は `gradle/common.gradle` に集約。Java DSLは両Gradleで使える形式へ変更。
- helperだけJava 25、共有コア・既存helperのコンパイルとコアテストはJava 21を使用。
  共有コア/APIのJava 8ターゲットは維持。
- Shadowのgroupだけの指定を明示的な正規表現に直し、Jetty/Servlet等の脱落を検査。
- ブロック名リストで単一状態ブロックを飛ばし、複数状態を重複登録する上流helperの処理を、
  ブロックレジストリIDと名前の対応へ修正。
- スキン応答のエラー時に元の応答本文をログへ出さない。
- NBTの数値・欠損値、配列、チャンクパレット、long境界のビット展開をテスト。
  旧Paper/新版Paper/パッチ版/不明形式の版判定もテスト。
- 銅ゴーレム像の立方体による暫定表示を廃止。4ポーズ・4方向・8種類の酸化/ワックス状態と水没状態に対応。
  公式26.2クライアント（SHA-1 `2dc72797acbc1b63fc16a11c4ac393605f453754`）の
  CopperGolemModel / CopperGolemStatueBlockRendererの形状・UV・変換を照合した数値データを使用。
  64pxのエンティティテクスチャを面ごとに切り出し、アンテナを含めて描画します。
- パッチの可視範囲検査でUの代わりにVを使っていた計算を修正。台形の端点も検査します。
- 像への右クリック後に実際のブロック状態が変わった場合だけ再描画。
  26.2では旧Materialのdata値への変換を使わず、旧helperのID/data比較は維持。

## ビルド

JDK 21と25の両方をインストールし、候補用wrapperはJAVA_HOMEを25にします。
Gradleが21を検出しない場合は `-Porg.gradle.java.installations.paths=<JDK21のパス>` を追加します。

```sh
# Minecraft 26候補: JAVA_HOME=JDK25
bash gradlew-minecraft26 verifySpigotJar --no-daemon
# 既存向け: JAVA_HOME=JDK21
bash gradlew -PdynmapPlatform=spigot verifySpigotJar --no-daemon
```

Windowsではそれぞれ `gradlew-minecraft26.bat` / `gradlew.bat` を使います。
候補出力は `target/Dynmap-3.9-SNAPSHOT-spigot-mc26.jar`、版番号には `-mc26` を付けます。
既存向けの `*-spigot.jar` と区別してください。監査記録は検証のたびに上書きされるため、
複数プロファイルの証拠を保存するときはCIのように別worktree/jobを使います。

## 稼働検証の確認項目

既存設定やR2資格情報を持ち込まず、localhost限定・新規ワールド・ファイル保存で検証します。
Paper 26.2 build 105 / Java 25を主対象とし、旧Paperは既存向けJARの回帰検証対象です。

1. Dynmap有効化とJetty起動。未対応platform、Class/Method欠落、NBT例外がないこと。
2. sulfur/cinnabar全系列、上下/二重slab、階段の向き、wall、sulfur spike、
   golden dandelionと鉢植え、creaking heart各状態、既存の石・ガラス・水・葉を描画。
3. surface/flat、部分更新、ズーム画像、ブラウザ表示を確認。
4. 同じ入力で旧Paper向けビルドの起動・レンダー・マーカーを確認。
5. テストサーバーを正常停止し、ソース版・JAR SHA-256・ログを記録。

銅ゴーレム像は256状態のメッシュを単体検査し、同じ256状態を実サーバーにも配置します。
通常の設置・破壊・ポーズ変更の検証方法は [隔離操作試験](../validation/minecraft26/README.md) を参照してください。
Fabric/Forge 26.2の新規対応は、今回選択したPaper/Spigot用ソースの取り込み範囲に含みません。
既存ローダーへの影響は、元のモジュールを保持した標準ビルドと共有コアのテストで確認します。
本番へ進める際は [運用手順](acecore-maintenance.md) のバックアップと個別承認に従います。

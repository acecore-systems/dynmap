# Acecore fork のビルドと検証

このforkは非公式で、Dynmapチームによるサポート・推奨を受けていません。
不具合の窓口は [Acecore Issues](https://github.com/acecore-systems/dynmap/issues) です。
元コードは [webbukkit/dynmap](https://github.com/webbukkit/dynmap) で、著作権表示、
Apache-2.0 の LICENSE、上流 README のカスタムビルド・商標・配布方針を維持します。
現段階は特定サーバーでの検証用ソースとCI整備です。CIからJARを一般配布しません。
公開配布は上流READMEの条件と対応状況を改めて確認する別の判断です。

## ビルド

Git、JDK 21、Gradle wrapper 8.14 を使います。Windowsでは `bash gradlew` を
`gradlew.bat` に読み替えてください。

```sh
bash gradlew -PdynmapPlatform=core :DynmapCore:test --no-daemon
bash gradlew -PdynmapPlatform=spigot verifySpigotJar --no-daemon
```

プロファイル省略時は上流と同じ全モジュールを構成します。`core` は共有コア/API、
`spigot` はそれらと既存の全Bukkit helper/API/Spigotを構成します。
異なるローダーの初期化を省く仕組みであり、Forge/Fabricの検証成功を意味しません。
共有コアを上流に提案するときは、上流READMEどおり全対象プラットフォームの検証が必要です。
Minecraft 26.2 / Java 25 の候補対応は Issue #2 で別に管理します。

`mavenLocal()` は明示的な `-PuseMavenLocal` のときだけ有効です。CIは使いません。
既存の Maven Central / Minecraft / MikePrimm / Spigot / CodeMC / Sonatype / JitPack
への参照を維持し、不要なリポジトリ巡回を減らすためCentralとMikePrimmを先にします。
既存依存のバージョンは変更しません。SNAPSHOTや動的指定は残っているため、
同じソースでも将来同じ依存が取得できる保証はありません。
`build/reports/acecore/build.json` は実際に解決した依存の座標とSHA-256を記録します。
これは監査記録であり、依存ロックや脆弱性評価の代替ではありません。

版番号は `3.9-SNAPSHOT-acecore-<HEADの12桁>[-dirty]` です。
Git管理ファイルまたは未追跡ソースの変更がある場合はdirtyを付けます。
`BUILD_NUMBER` による上書きはしません。版番号をリソース処理の入力にして、
コミット後の再ビルドでも plugin.yml / core.yml が更新されるようにしています。
アーカイブ内の日時と順序を固定しますが、依存を含む完全な再現性は未保証です。

`verifySpigotJar` はコアテスト、JAR生成、Jetty/Servlet/S3/bStats/ブラウザ資材の存在、
版番号の整合を検査し、SHA256SUMS.txtとbuild.jsonを出力します。
GitHub Actions は固定SHAのアクション、contents:read、認証情報を残さないcheckoutを使い、
テスト結果と監査記録だけを14日保存します。秘密値・実サーバー設定を必要としません。
この構成は自動デプロイ、Release作成、Maven公開を行いません。

## 変更と同期

default branch `v3.0` を安定基点とし、Issueごとの `codex/*` branch/worktreeで作業します。
上流の追従候補はコミットSHAを固定して差分・依存・ライセンスをレビューし、
必要なソースだけを別PRに取り込みます。上流の生成済みJARは取り込みません。
CI成功後もdraft PRで差分と検証範囲を確認し、マージと本番反映を区別します。

| Issue | 独自差分 | 管理方法 |
| --- | --- | --- |
| #1 | R2の境界スナップショットと同一位置マーカーの更新抑制 | 独立PR、回帰テスト・隔離サーバー比較 |
| #2 | Minecraft 26.2候補、helper・モデル・Java/Gradle互換性 | 上流SHAと採否・既知制限を記録する独立PR |
| #3 | ビルド対象選択、CI、版識別、同梱検査、非公式窓口 | この文書とワークフロー |

## 段階的な稼働検証

1. 読み取り専用で稼働版、Java、設定、拡張プラグイン、エラーを確認し検証先を選ぶ。
2. 同じPaper版の隔離サーバーに新規ワールドを作り、既存地図・R2接続を持ち込まず検証する。
3. 起動、初期レンダー、実ブロック更新、ズーム、境界・スポーン・連携マーカー、停止を確認する。
4. 候補のコミット・版・SHA-256と検証証拠を揃え、本番反映の承認を得る。
5. 現行JAR・設定・連携プラグイン・必要データをバックアップし、復旧可能性を確かめる。
   自動更新が候補JARを上書きしないことも実環境で確認する。
6. 選定した1台だけに反映し、予定された安全な停止/起動で有効化する。
   既存タイル・ワールド・R2オブジェクトを削除しない。
7. エラー、地図更新、利用者影響、R2 originの変更を観測する。
   キャッシュされたHTTP応答やLast-ModifiedだけからPUT件数・料金を断定しない。
8. 起動失敗、描画欠落、連携不具合があれば候補を外し、バックアップJAR/設定へ戻す。
   ロールバック時も停止/起動の時機を確認し、地図を一括再生成しない。

ホスト名、内部パス、認証情報、実プレイヤー情報は公開PR・CI artifactに記載しません。

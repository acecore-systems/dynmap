# Jetty HTTP のJava 8互換バックポート

元パッケージはMaven Centralの `org.eclipse.jetty:jetty-http:9.4.58.v20250814`。
生成JARのバージョンは `9.4.58.v20250814-acecore.1`。
原本バイナリ、ソースJAR、対象ソースのSHA-256をビルド時に照合する。

| 固定する原本 | SHA-256 |
| --- | --- |
| 9.4.58バイナリJAR | `8f49b8583fc8dbbfe3a0ba80a04c97df64ebabca2981b56ee403c989aba9d4da` |
| 9.4.58ソースJAR | `d132a5af1abd76736f259c37b725f0d056f4162284425e8d74ebaf1e38ec0377` |
| 元HttpParser.java | `2b5a66ebc03eb372ca31031da9e61e6778fc1a47fe3da88cd69c28bb9139d02c` |

## 修正の由来

- Jetty公式 `ff9eb742492d6dc3191bcd49668b1bce1e620d57` は12.0.33のチャンク拡張文法修正。 `ChunkSizeState` と引用符・エスケープの文字判定をJava 8のswitch文へ変換した。 [公式差分](https://github.com/jetty/jetty.project/commit/ff9eb742492d6dc3191bcd49668b1bce1e620d57)
- Jetty公式 `389ffcbc90f5b92ae179ce276ba0fbf3f4befbf1` は12.0.38のチャンク改行の厳密化。9.4の既存 `_cr` を使い、チャンクサイズ・データ終端・トレーラーの裸LFを拒否する。 [公式差分](https://github.com/jetty/jetty.project/commit/389ffcbc90f5b92ae179ce276ba0fbf3f4befbf1)

9.4のintサイズ上限、例外API、ログAPIを保持した。新しい `HttpTokens.EOL` 型の判別は既存CRフラグに置き換え、任意の裸LF許可モードは追加しない。2332修正の `HttpTokens` 変更はコメントだけ。サーブレットAPIは変更しない。元の `HttpParser` と全ネストクラスを置き換え、生成クラスとのバイト一致とJava 8 bytecodeを検査する。

19203修正の `HttpCompliance` 変更は、新しい `LF_CHUNK_TERMINATION` 違反種別と、RFC2616/LEGACYモードに限る許可設定。9.4にはこの列挙型の仕組みがないため、チャンク処理での裸LF拒否を直接適用する。HTTPヘッダー用の既存compliance設定には変更を加えない。

## セキュリティ範囲

CVE-2026-2332の未完結quoted-stringと、CVE-2026-19203のチャンク拡張・終端の裸LFを対象とする。CVE-2026-19203全体のfixed判定は行わず、HTTPヘッダー全体の互換モードは9.4の既存動作を保持する。HTTP/2 Host一致、Digest認証、URIの差異に関するアドバイザリーは未修正として維持する。公開9.4.58を公式修正版として扱わない。

## ライセンス

原本ソースのEclipse Public License 1.0 / Apache License 2.0の選択ライセンスと著作権表示を保持。参照した12系の修正はEPL 2.0 / Apache License 2.0であり、このバックポートは共通のApache License 2.0条件で使用する。元JARのMETA-INFライセンス・NOTICEも生成JARに保全する。

## 互換性と同梱の検証

`verifyPatchedJettyHttp` は原本とパッチ文脈、生成JAR内の全10パーサークラス、Java 8 bytecodeを検証する。現行Gradleは `--release 8` を使い、Java 9以降のByteBuffer APIへの参照を防ぐ。CIではJava 21生成物の回帰試験をJava 8で実行し、旧ForgeもJava 8 / Gradle 4.10.2で実ビルドする。

5つの回帰試験は、全バッファ境界、1バイトread-only入力、正常な拡張と複数チャンク、不正な引用符と各チャンク終端、reset後のGET、固定長request/responseを扱う。Coreと旧ForgeのShadow入力に未補修HTTP JARが残る場合は失敗する。最終JARには補修した全ネストクラスと由来文書が必要。SpigotはCoreの全パーサークラスとのバイト一致も検査する。

公式9.4.58の `HttpParserTest.java` はSHA-256 `2869df41027c2121b31441c9b564e861bcd40a7d8ee8bb895971eb7f8cb89006`。別添 `upstream-test-fixtures.patch` は公式2332修正と同じ8つの正常fixtureで、空の拡張 `a;` に名前 `ext` を追加する。依存ソース用パッチとは独立している。採用前に、このfixture差分だけを適用した公式114試験をUTF-8でコンパイルし、固定したJava 21生成JARを単一Java 8 JVMで実行した。全114件が成功し、未修正原本に対する空拡張の受理を正常条件として残すことはしない。[公式テスト原本](https://github.com/jetty/jetty.project/blob/jetty-9.4.58.v20250814/jetty-http/src/test/java/org/eclipse/jetty/http/HttpParserTest.java)

## 更新監視と公式版への復帰

元Jetty座標はこのディレクトリとCore/旧Forgeのbuild.gradleでDependabotが監視する。補修manifestを既存53ディレクトリに追加し54対象とする。Java 8互換の公式修正版が公開された場合は、Core/旧Forgeの補修project依存と元jetty-httpの除外を公式版へ戻し、全パーサー回帰試験と最終Shadow成果物の検査を再実施する。原本のSHA-256は自動追従しない。

生成JARは既存のサーバーへの配布を伴わない。補修を含むJARでもGitHubの元座標に対するアドバイザリーは残りうるため、未修正の警告を除外・解決済み扱いにしない。

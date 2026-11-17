# Jetty Server HTTP1 のJava 8互換バックポート

元パッケージはMaven Centralの `org.eclipse.jetty:jetty-server:9.4.58.v20250814`。
補修版は `9.4.58.v20250814-acecore.1`。

| 固定する原本 | SHA-256 |
| --- | --- |
| バイナリJAR | `608dbd3e1c4184374f02c70704e5d1c9daaded45e6a31d07824f40dac6083ba9` |
| ソースJAR | `45815da72c11ba6394f2efc7a6923e81a9c31444d51f7a5a56b25dd421830d42` |
| HttpChannelOverHttp.java | `d578a94470d1c7004a4169c2cbac2f8bc6ee5887563a949ead8b2f4c7e732421` |

## 公式修正と9.4への適応

CVE-2026-6790はHTTP1、HTTP2、HTTP3を対象とする。公式 `cbca3076f7c914a232e7a8b22fa95fbf7e67a6cc` のauthorityMatches条件を、9.4のHTTP1 headerCompleteでonRequestとcustomizerより前に適用する。元metadataと全headerが既に揃うため、public APIと追加stateを必要としない。[公式修正](https://github.com/jetty/jetty.project/commit/cbca3076f7c914a232e7a8b22fa95fbf7e67a6cc)

9.4にないURIUtil.getDefaultPortForSchemeは、同じ公式8scheme表をprivate helperへ適応する。大文字小文字、既定port、CONNECT、Host欠落の元HTTP1.0/1.1方針を保持する。新public compliance modeは追加せず、9.4の全モードで不一致を拒否する。HTTP2/3の実装classはこの配布JARに含まれない。修正範囲を同梱HTTP1経路として記録する。

## URIのnull path互換

HTTPの補修版acecore.3は、authorityだけのURIで9.4元契約のnull pathを保持する。前のacecore.2で12系の空文字へ合わせた結果、既存HTTP1のtestNoPathが400となる退行を本正常suiteで検出し、期待を変更せず復旧した。query-onlyではgetPathQueryがliteral nullを含まず、`?query`を返す。

## 正常・修正試験

LocalConnectorの回帰は、customizer/handler前の不一致拒否、case/既定port、異なるhost/port、origin form、CONNECT、Host欠落、IPv6、全compliance、空path/query-onlyを扱う。外部TCP接続は使用しない。

公式9.4のHttpConnectionTest、HttpChannelEventTest、LocalConnectorTestは108件。無補修原本の原fixtureは108/108、URIのnull互換回復と有効なchunk fixtureだけの比較も108/108、authority補修とCONNECTの意図した期待変更を含む比較も108/108成功。skip/abort/failは各0。

別添upstream-connection-test-fixtures.patchは、公式2332修正と同じ空chunk extensionの削除を7つの正常method内へ適応した22文字列と、CONNECT Host不一致を400期待へ変える差分。CONNECTの同一authority/Hostで200となる肯定assertも追加する。testNoPathの期待には変更がない。

公式ConstraintTestのtestNonFormPostRedirectHttp11は、元の二重Hostからlocalhost行だけを除去し、303とConnection: closeの正常assertをすべて保持する。公式12.0.35の同methodもHost: testの1行だけを送る。別添upstream-constraint-test-fixtures.patchで試験入力の差を記録する。元の二重Hostは独立回帰で原本受理と補修400・customizer/handler未到達を対照する。

固定したHTTP acecore.3、security acecore.1、server acecore.1の同じ3 JARを単一Java8 JVMで実行し、688件中686件成功、URI原本と同じassumption abort 2件、失敗0を確認する。内訳はParser正常114、URI正常381、constraint正常56、server正常108、追加回帰29件。正常suiteのfixture変更は上記とHTTP由来文書のものに限定する。

元Maven POMのcompile依存をjava-libraryのapiへ対応させる。HTTPはio/util、serverはHTTP/io/Servlet（utilはio経由）、securityはserverを公開する。HTTPのprovided Servlet、optional JMXやtest依存は公開へ追加しない。consumerは不完全なclasses variantを選ばず、原本の未変更classを保持した完成補修JARを使う。

## 同梱・由来・更新監視

元classと全nestedを除外して補修classへ置換し、固定hash、compiled集合・bytecode、Java8を検査する。由来文書とpatchは一意のMETA-INF/acecore/jetty-serverに保存する。最終Shadowは元server JARの再混入を拒否し、HTTP/security/server全由来とのbyte一致を検査する。

9.4原本はEPL1/Apache2、12系公式修正はEPL2/Apache2の選択ライセンス。本移植は共通のApache License 2.0を使用し、著作権とLICENSE/NOTICEを保持する。

元server座標をこのmanifestで監視し、既存55対象に加えて56 Gradle対象とする。公式Java8互換版へ戻す際はCore/旧Forge/securityのserver補修依存と除外を公式版へ戻し、全正常・修正unitと最終JARを再検証する。原本hashは自動追従しない。既存Minecraftサーバーへの配布は伴わない。

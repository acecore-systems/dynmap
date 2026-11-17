# Jetty Security のJava 8互換バックポート

元パッケージはMaven Centralの `org.eclipse.jetty:jetty-security:9.4.58.v20250814`。
補修版は `9.4.58.v20250814-acecore.1`。HTTP補修とは別の依存JARを生成する。

| 固定する原本 | SHA-256 |
| --- | --- |
| バイナリJAR | `301548ebf008551a3a4312b04df0ea0468acc2780f863b83cc9cf0d668e62252` |
| ソースJAR | `aa63bf59deba777ae7785b8cb357f752747a486a2292b692cf7f8c48bd8bba85` |
| ConstraintSecurityHandler.java | `d451537b9c52583ce879677a54cd71cb3d4487541edd81219a74e843c55cd079` |

## 公式修正と適応

公式 `fc604037f0568d712bc45f6944ae1c484693690e` の同じEE9 ConstraintSecurityHandler修正を既存9.4 APIへ限定移植する。[公式commit](https://github.com/jetty/jetty.project/commit/fc604037f0568d712bc45f6944ae1c484693690e)

- CVE-2026-18659はmethod-omission keyへのsubstring比較を、既存getOmittedMethodsが返す集合の完全一致へ変更する。既存null/suffix guardを保つ。
- CVE-2026-18662は同じomission keyのRoleInfoを再利用し、先行するforbidden状態をconfigure後にも維持する。既存protected methodを保持し、公式のinliningやmapping全体の再設計は移植しない。

5つのLocalConnector回帰試験は、完全一致とsubstring、複数omissionとkey suffix、重複制約のforbidden保持、役割の結合と順序、異なるomission集合を検証する。外部TCP接続は使用しない。公式9.4のConstraintTest、DataConstraintsTest、SpecExampleConstraintTestの正常56件は、原本・18659のみ・両修正で全件成功。回帰5件は原本1成功4失敗、18659のみ3成功2失敗、両修正5成功。両修正のJava8実行は計61/61成功。

## パッケージとライセンス

HTTPと同じ厳密patch、SHA、Java8 API制約、全compiled class一致の検証処理を共有する。元ConstraintSecurityHandlerと全nested classを除外し、補修した全classだけを元JARへ戻す。最終Shadowで未補修JAR再混入とclass不足・重複を拒否する。

由来文書とpatchは `META-INF/acecore/jetty-security/` に入り、HTTP用文書との衝突を避ける。最終JARは両パッケージの文書・patchとのbyte一致を検査する。

9.4原本はEPL1/Apache2、公式12系の修正はEPL2/Apache2の選択ライセンス。この移植では共通のApache License 2.0を使用し、著作権と元JARのLICENSE/NOTICEを保持する。

## 更新監視と残る範囲

既存54 Gradle manifestにこのmoduleを加えて55対象とし、元Jetty Security座標の監視を維持する。公式Java8互換版へ復帰するときは、Core/旧Forgeの補修project依存と元securityの除外を公式版へ戻し、全正常・修正unitと最終Shadow検査を再実施する。原本hashは自動更新しない。

Digest、SessionAuthenticationのcredential永続化、URI utility等の未補修コードは残る。18662と18659以外のアドバイザリーを、この変更や機能不使用だけでfixedにしない。既存サーバーへの配布は伴わない。

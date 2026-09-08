# 26.2の隔離操作試験

`ValidationPlugin.java` は検証専用です。本番JARには含めません。
localhost限定・合成ワールドのPaper/Spigot 26.2で、コンソールからだけ実行します。
水没サンプル同士が干渉しないよう水流を止め、時刻とランダムティックも固定します。

試験用ServerPlayerが通常の `ServerPlayerGameMode.useItemOn` / `destroyBlock` を呼びます。
Bukkitイベントの直接注入やDynmapの描画API呼び出しは行いません。
クライアントのログイン・パケット通信・マウス操作自体はこの試験の対象ではありません。

## ビルド

JDK 25で候補の `verifySpigotJar` を実行してから、同じ作業ディレクトリで次を実行します。
JDK 21を自動検出しない環境は通常の候補ビルドと同じtoolchainパラメーターを追加してください。

```sh
bash gradlew-minecraft26 -I validation/minecraft26/classpath.init.gradle :bukkit-helper-26-2:validationClasspath
mkdir -p build/validation/minecraft26/classes
javac -cp "$(cat build/validation/minecraft26/classpath.txt)" -d build/validation/minecraft26/classes validation/minecraft26/ValidationPlugin.java
cp validation/minecraft26/plugin.yml build/validation/minecraft26/classes/
jar --create --file build/validation/minecraft26/validation.jar -C build/validation/minecraft26/classes .
```

Windowsはwrapperを `.bat` にし、クラスパスを `Get-Content -Raw` で渡します。

## 入力と判定

ワールドのY=0に白い床を作り、X/Z=4..7のY=1を空けておきます。
X=12,Y=1,Z=12には `copper_golem_statue[copper_golem_pose=standing,facing=north,waterlogged=false]` を置きます。
先にsurfaceを描画し、更新が落ち着いてから次の各操作を個別に実行します。

1. `dynmapvalidate place`: ダイヤモンドブロックを16個設置し、未キャンセルのBlockPlaceEvent 16件を検査。
2. `dynmapvalidate break`: 16個を破壊し、未キャンセルのBlockBreakEvent 16件を検査。
3. `dynmapvalidate pose`: 像を右クリックする通常処理でポーズが変わることを検査。

各操作間に描画コマンドを挟まず、対象のsurface通常タイルのSHA-256を比較します。
設置で変更、破壊で設置前へ復元、ポーズ変更で再変更されることを確認します。
zoomタイルや離れたタイルの変更だけを合格にしないでください。
大量のサンプル変更直後は通常更新のキューも処理されるため、対象範囲をvisibilitylimitsで限定します。

別途fullrender/updaterenderの完了行、flat、zoom、HTTP、実際のブラウザ表示を確認します。
試験後は `stop` で正常終了し、使用した候補の版・SHA-256とログを保存します。

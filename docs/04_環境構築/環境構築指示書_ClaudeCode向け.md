# 環境構築指示書（Claude Code 向け）

AIブライダルプランニング支援システム（P2C：Creepy Code）

作成日：2026-10-06　改訂日：2026-10-07

> **2026-10-07 改訂の要点**：ソースコードは作らずフォルダ構成だけにする／パッケージを「機能ごと」に分ける／SQL はアプリの外（`db/`）に置く／設定ファイルは共通の1つだけにする／AI を Claude API から Gemini API（無料枠）に変更。あわせて基本設計書を v1.6 に、コーディング規約を改訂済み。

---

## 0. この指示書の使い方（人間向け）

1. Creepy-Code リポジトリを `C:\pleiades\2023-12\workspace\Creepy-Code` にクローンする（OneDrive の中には置かない）。
2. 授業で作った Oracle のコンテナ（`oracle21c` や `oracle-xe` など、名前は人によって違ってよい）を Docker Desktop で起動しておく。
3. そのフォルダで Claude Code を開き、次のように頼む。

   > `docs/04_環境構築/環境構築指示書_ClaudeCode向け.md` を読んで、この指示書どおりに環境構築して。作業の前に計画を見せて、私がOKしてから始めて。

4. 途中で Claude Code から質問されたら答える。最後に「8. 完成条件」がすべて満たされたか報告してもらう。

---

## 1. 目的

このリポジトリの中に、Spring Boot アプリの実装環境 `bridal-app/` を作る。メンバー全員（実質2名）が同じ手順で、自分の PC（Windows）のローカル環境でアプリを起動できる状態にするのがゴール。

**今回やるのは「フォルダ構成の構築」と「起動できる最小限の土台」だけ。** 機能のソースコード（Entity・Controller・画面など）は一切作らない。機能の実装はガントチャートでメンバーに割り当て済み。

---

## 2. 決定事項（チームで決めたことなので変えないこと）

| 項目 | 決定内容 | 理由・補足 |
|---|---|---|
| 実行環境 | **ローカルのみ。AWS は使わない** | 去年の先輩もローカルで実行していた。基本設計書 v1.6 の 3-2 節を参照 |
| Java | **17** | 授業（Pleiades 2023-12）と同じ |
| Spring Boot | **3.5 系の最新パッチ** | 授業は 3.1 系。書き方が同じ 3 系の最終版を使う。**4 系にはしない** |
| ビルド | Maven（Maven Wrapper 付き） | 授業と同じ |
| DB アクセス | **Spring Data JPA** | 授業で習った方式。基本設計書 v1.6 の 8-3 節 |
| DB | **授業で作った Oracle XE のコンテナを使い回す** | 新しいコンテナは作らない。`localhost:1521/XEPDB1` に、このプロジェクト専用ユーザー `CREEPY` を作る |
| JDBC ドライバ | `ojdbc11` | 授業と同じ |
| 画面 | Thymeleaf ＋ Thymeleaf Layout Dialect ＋ Bootstrap 5 | コーディング規約5章で `layout:decorate` を使うと決めている |
| 認証 | Spring Security（フォーム認証・BCrypt） | 基本設計書 v1.6 の 8-2 節。今回は依存関係に入れるだけで、設定クラスは作らない |
| AI | **Gemini API（Google、無料枠）** | 基本設計書 v1.6 の 3-3 節。今回は設定だけで、呼び出しのコードは作らない |
| ソースコード | **作らない** | 作るのは `pom.xml`・Maven Wrapper・`BridalApplication.java`・`application.properties` の4つだけ |
| パッケージ | **`com.creepycode` の下を機能ごとに分ける** | コーディング規約2章（改訂済み）。2人で分担したときにファイルがぶつかりにくくするため |
| 設定ファイル | **`application.properties` の1つだけ** | 各自の設定ファイル（`application-local.properties` など）は作らない。個人で変える値（AI のモードと API キー）は環境変数から読む |
| SQL の置き場所 | **リポジトリ直下の `db/`** | アプリ（`bridal-app/`）の外に置き、SQL Developer で開いて実行する |
| フォルダ名 | `bridal-app`（リポジトリ直下） | 設計書・マニュアルと同じリポジトリで管理する |
| IDE | Eclipse（Pleiades 2023-12）で「既存 Maven プロジェクト」としてインポートする | メンバー全員が使っている |
| DB クライアント | Oracle SQL Developer | メンバー全員が使っている |

### AWS の代わりに使うもの（基本設計書 v1.6 の 3-2 節と同じ）

| 設計当初の AWS | ローカルでの代わり |
|---|---|
| EC2 | 各自の PC で Eclipse から起動 |
| RDS（Oracle） | 各自の PC の Docker 上の Oracle Database XE（`XEPDB1` の `CREEPY` ユーザー） |
| S3（添付ファイル・会話履歴md） | `bridal-app/uploads/` フォルダ（Git には入れない） |
| SES（パスワードリセットメール） | 実際には送らず、リセット URL をログに出す |
| CloudWatch Logs | Spring Boot の標準ログ（コンソール） |

### Gemini API

- Google AI Studio で発行した API キーを使う。2026年6月以降は「Gemini API 専用」の制限が付いたキーでないと使えない。
- 無料枠では、送った内容が Google のサービス改善に使われる。**開発・発表では架空のテストデータだけを使い、実在の個人情報は送らない。**
- API キーなしでも開発できるように、`APP_AI_MODE=dummy`（固定の文章を返す）と `APP_AI_MODE=api`（本物を呼ぶ）を環境変数で切り替える。初期値は `dummy`。
- API キーは環境変数 `GEMINI_API_KEY` に設定する。**リポジトリには絶対に書かない。**

---

## 3. 参照する資料

| 資料 | 場所 | 使い方 |
|---|---|---|
| 基本設計書 v1.6（抜粋） | `docs/04_環境構築/参考_基本設計書v1.6抜粋.md` | 前提条件（1-2）・システム構成（3章）・テーブル定義（7章）・セキュリティ（8章）の根拠。**テーブル作成 SQL はこの 7-2 節に完全に従う** |
| 基本設計書 v1.6（正本） | `docs/00_ブライダル業界/03_基本設計書/v1.6/基本設計書_v1.6.docx` | 抜粋と食い違ったらこちらを優先する |
| コーディング規約 | 今は `src/コーディング規約.md`（手順1で `docs/04_開発ルール/` に移動する） | 命名規則・**パッケージ構成（2章）**・フォーマット（**インデントは半角スペース4つ**）。2章は改訂済みなので**中身は変更しない** |

---

## 4. 作業前の確認（最初に必ず行い、結果を報告すること）

次のどれかが満たされていなければ、**作業を始めずにユーザーに報告して指示を待つ。**

1. **Java 17 が使えるか**：`java -version` を確認する。PATH に無い場合は Pleiades 同梱の `C:\pleiades\2023-12\java\17\bin\java.exe` があるか確認する（Maven Wrapper の実行には `JAVA_HOME` をそこに向ける）。
2. **Oracle コンテナが動いているか**：`docker ps --filter "publish=1521"` で、ポート1521を公開しているコンテナが1つ動いているか確認する。**コンテナ名は人によって違うので、名前を決め打ちしない。**
3. **SYSTEM で XEPDB1 に入れるか**：上で見つけたコンテナに対して `docker exec -i <コンテナ名> sqlplus -s system/<パスワード>@//localhost:1521/XEPDB1` で `SELECT 1 FROM dual;` が通るか確認する。授業の初期パスワードは `systemsss` だが、違う場合はユーザーに聞く。
4. **Git の状態**：`git status` で未コミットの変更を確認する。次のファイルは、この指示書の改訂と一緒に**意図して変更したもの**なので、手順1のコミットに含めてよい。それ以外の変更があれば内容を報告し、どうするかユーザーに聞く（勝手に破棄・コミットしない）。
   - `docs/04_環境構築/環境構築指示書_ClaudeCode向け.md`（この指示書）
   - `docs/04_環境構築/参考_基本設計書v1.6抜粋.md`（新規）
   - `docs/00_ブライダル業界/03_基本設計書/v1.6/` の中身（新規）
   - `docs/00_ブライダル業界/03_基本設計書/説明資料/` の pptx（表記を v1.6 に合わせて修正・追加）
   - `src/コーディング規約.md`（2章を改訂）

---

## 5. ブランチとコミットのルール

- `main` を最新にしてから、`feature/env-setup` ブランチを作って作業する。
- コミットメッセージは既存ルールの形式（`feat:` `fix:` `docs:` `chore:` など）で、日本語で書く。
- 手順ごとに意味のまとまりでコミットを分ける（例：`docs: 基本設計書v1.6と関連資料を追加`、`chore: .gitattributes を追加`、`feat: bridal-app のフォルダ構成を作成`）。
- **push とプルリクエストの作成は、ユーザーに確認してから行う。**

---

## 6. 作業手順

### 手順1：リポジトリの整備

1. **資料の改訂分を先にコミットする**：「4. 作業前の確認」の4に挙げたファイルを `docs:` のコミットにまとめる。あわせて、v1.6 の抜粋に置き換わった `docs/04_環境構築/参考_基本設計書v1.5抜粋.md` を `git rm` する。
2. **`.gitattributes` をリポジトリ直下に作る。** 目的は、Windows と Git の間で改行コードが勝手に変わり、中身が同じなのに「変更あり」と表示される問題を防ぐこと。
   - `* text=auto`
   - `mvnw` は `eol=lf`、`*.cmd` `*.bat` は `eol=crlf`
   - `*.docx *.xlsx *.pptx *.pdf *.png *.jpg *.jpeg *.gif *.jar` は `binary`
   - 追加したら、既存ファイルの改行を正規化するコミット（`git add --renormalize .`）を**別コミット**にする。その前に、差分が改行だけであることを `git diff --ignore-cr-at-eol --stat` で確認して報告する。
3. **`.gitignore` に追記する**：`bridal-app/uploads/`。既存の記述は消さない。
4. **コーディング規約を移動する**：`git mv src/コーディング規約.md docs/04_開発ルール/コーディング規約.md`。**中身は改訂済みなので変更しない。** 空になった `src/` は削除する（Spring Boot の `src/main/java` と紛らわしいため）。

### 手順2：Spring Boot プロジェクトの作成

`bridal-app/` を Spring Initializr（ `https://start.spring.io` ）相当の内容で作る。

| 項目 | 値 |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | 3.5 系の最新パッチ |
| Group | `com.creepycode` |
| Artifact / Name | `bridal-app` |
| Package name | `com.creepycode` |
| Packaging | Jar |
| Java | 17 |
| メインクラス | `com.creepycode.BridalApplication` |

依存関係：

- Spring Web
- Thymeleaf
- Spring Data JPA
- Spring Security
- Validation
- Spring Boot DevTools
- Oracle Driver（`com.oracle.database.jdbc:ojdbc11`）
- `nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect`
- `org.thymeleaf.extras:thymeleaf-extras-springsecurity6`
- Bootstrap 5.3 系の WebJar（`org.webjars:bootstrap`）。CDN は使わず、Maven で管理する
- テスト：spring-boot-starter-test、spring-security-test

バージョンはできるだけ Spring Boot の依存関係管理に任せる。管理対象外のもの（Bootstrap WebJar など）だけバージョンを明記する。

Maven Wrapper（`mvnw` / `mvnw.cmd` / `.mvn/`）を含めること。

Spring Initializr が自動で作るもののうち、**`BridalApplication.java` 以外の Java ファイル（テストクラス `BridalApplicationTests.java` など）と `HELP.md` は作らない（作られた場合は削除する）。**

### 手順3：フォルダ構成

```
Creepy-Code/
├─ db/                              ← SQL Developer で開いて実行する SQL
│   ├─ 00_create_user.sql           ← SYSTEM で実行：CREEPY ユーザー作成
│   ├─ 01_create_tables.sql         ← CREEPY で実行：T-01〜T-15
│   ├─ 02_insert_master.sql         ← CREEPY で実行：マスタの初期データ
│   ├─ 03_insert_testdata.sql       ← CREEPY で実行：動作確認用データ
│   └─ 99_drop_tables.sql           ← CREEPY で実行：全テーブル削除（作り直し用）
└─ bridal-app/                      ← Eclipse で読み込むアプリ本体
    ├─ pom.xml / mvnw / mvnw.cmd / .mvn/
    ├─ uploads/                     ← 実行時に自動作成。Git に入れない（作成不要）
    └─ src/
        ├─ main/
        │   ├─ java/com/creepycode/
        │   │   ├─ BridalApplication.java
        │   │   ├─ common/
        │   │   │   ├─ controller/  config/  exception/  ai/  storage/
        │   │   ├─ auth/
        │   │   ├─ hearing/
        │   │   ├─ plan/
        │   │   ├─ chat/
        │   │   ├─ evaluation/
        │   │   └─ admin/
        │   │       （common 以外の各機能の中に controller/ service/ repository/ entity/ dto/）
        │   └─ resources/
        │       ├─ application.properties
        │       ├─ templates/
        │       │   └─ common/  auth/  hearing/  plan/  chat/  evaluation/  admin/
        │       └─ static/
        │           └─ css/  js/  images/
        └─ test/java/com/creepycode/
```

- 中身が空のフォルダには、すべて `.gitkeep` だけを置く（Git では空のフォルダが消えるため）。
- `package-info.java` やサンプルのクラス・HTML は作らない。
- どの画面・テーブルがどの機能フォルダに入るかは、コーディング規約2章の対応表のとおり。

### 手順4：設定ファイル（`application.properties` の1つだけ）

全員共通・Git に入れる。各行に、何のための設定かを日本語でコメントする。

- `spring.application.name=bridal-app`
- `server.port=8080`
- `spring.thymeleaf.cache=false`
- DB の接続設定（全員同じ値のため、ここに直接書く）
  - `spring.datasource.url=jdbc:oracle:thin:@localhost:1521/XEPDB1`
  - `spring.datasource.username=CREEPY`
  - `spring.datasource.password=creepycode`
  - `spring.datasource.driver-class-name=oracle.jdbc.OracleDriver`
- `spring.jpa.hibernate.ddl-auto=none`（テーブルは SQL ファイルで作る。**JPA に勝手にテーブルを作らせない**）
- `spring.jpa.show-sql=true`
- `spring.servlet.multipart.max-file-size=10MB` と `spring.servlet.multipart.max-request-size=10MB`（基本設計書 8-3）
- `server.servlet.session.timeout=30m`（基本設計書 8-2）
- `app.storage.dir=./uploads`
- AI の設定（環境変数から読む）
  - `app.ai.mode=${APP_AI_MODE:dummy}`
  - `app.ai.api-key=${GEMINI_API_KEY:}`

`spring.profiles.active` は書かない。`application-local.properties` などの個人用ファイルは作らない。

### 手順5：SQL ファイル（`db/` に置く）

すべての SQL ファイルは、SQL Developer のワークシートで「スクリプトの実行（F5）」をしても動くように書く。冒頭にコメントで「どのユーザーで実行するか」を書く。

**`00_create_user.sql`（SYSTEM で実行）**

- `ALTER SESSION SET CONTAINER = XEPDB1;`
- `CREATE USER CREEPY IDENTIFIED BY creepycode;`
- 権限は必要なものだけ与える：`CREATE SESSION`、`CREATE TABLE`、`CREATE SEQUENCE`、`CREATE VIEW`、`QUOTA UNLIMITED ON USERS`。`GRANT ALL PRIVILEGES` は使わない。
- **授業のユーザー（`SPRING_USER` `SSSUSER` など）には一切触らない。**

**`01_create_tables.sql`（CREEPY で実行）**

- `参考_基本設計書v1.6抜粋.md` の 7-2 節に書かれた T-01〜T-15 を、**カラム名・データ型・制約をそのまま**作る。
- 「PK / 自動採番」は `GENERATED BY DEFAULT AS IDENTITY` で作る。
- 外部キー（FK）・UNIQUE・複合ユニーク（T-11・T-13 の `(PLAN_ID, EVAL_SEQ)`）・CHECK 制約（`IS_VARIABLE` の Y/N、スコアの 1〜5、`ROLE`、`STATUS` の値）も作る。
- 親テーブルから順に作る（FK の参照先が先にできるように）。
- 各テーブル・カラムに `COMMENT ON` で日本語の説明を付ける（SQL Developer で説明が見えるようにするため）。
- 設計書で判断がつかない点（例：FK の `ON DELETE` の扱い）は**勝手に決めず**、作業報告に「設計書に記載なし・仮にこうした」と一覧で書く。

**`02_insert_master.sql`（CREEPY で実行）**

- `ARCHETYPE_MASTER`、`PRICE_MASTER` に、動作確認用の仮データを数件ずつ入れる。仮データであることをコメントで明記する。

**`03_insert_testdata.sql`（CREEPY で実行）**

- `USERS` に、ロールごとに1人ずつ（CUSTOMER / PLANNER / ADMIN）テストユーザーを作る。名前・メールアドレスは架空のものにする。
- パスワードは全員 `Password1`（基本設計書の条件「8文字以上・半角英数字の両方を含む」を満たす）。BCrypt でハッシュ化した値を入れ、どの平文のハッシュかをコメントに書く。

**`99_drop_tables.sql`（CREEPY で実行）**

- 全テーブルを子テーブルから順に `DROP TABLE ... CASCADE CONSTRAINTS PURGE` する。

### 手順6：メンバー向けドキュメント

1. **`docs/04_環境構築/環境構築手順書.md` を作る。** 読み手はプログラミング初心者のチームメンバー。専門用語には一言説明を付け、画面操作は「どのメニューを押すか」まで書く。内容：
   1. 前提：Pleiades 2023-12・Docker Desktop・授業の Oracle コンテナ・SQL Developer・SourceTree が入っていること
   2. リポジトリを `C:\pleiades\2023-12\workspace\Creepy-Code` にクローンする（OneDrive の中に置かない理由も一言）
   3. Docker Desktop で Oracle コンテナを起動する（コンテナ名は人によって違ってよいこと）
   4. SQL Developer で SYSTEM 接続を作り、`db/00_create_user.sql` を実行
   5. SQL Developer で CREEPY 接続（ユーザー `CREEPY` / パスワード `creepycode` / サービス名 `XEPDB1`）を作り、`db/01`〜`03` を順番に実行
   6. Eclipse で「ファイル → インポート → Maven → 既存 Maven プロジェクト」から `bridal-app` を読み込む
   7. `BridalApplication` を右クリック → 「実行 → Spring Boot アプリケーション」
   8. コンソールのログに DB 接続成功（`HikariPool-1 - Start completed.`）が出れば完了。なお、ブラウザで http://localhost:8080 を開くと Spring Security 標準のログイン画面が出るが、画面はまだ作っていないので問題ない
   9. AI を使うとき：Google AI Studio で API キーを発行し、Windows の環境変数 `GEMINI_API_KEY` と `APP_AI_MODE=api` を設定する（設定後は Eclipse の再起動が必要）。無料枠では送った内容が Google のサービス改善に使われるので、架空のテストデータだけを使うこと
   10. DB を作り直したいとき：`db/99_drop_tables.sql` → `db/01`〜`03` を再実行
   11. よくあるトラブル（ORA-12514 / ORA-01017 / ポート8080が使用中 / Java のバージョン違い / 文字化け）と対処
2. **`README.md` を更新する**：リポジトリの説明、フォルダ構成の一覧（`db/` と `bridal-app/` を含む）、手順書・コーディング規約・基本設計書 v1.6 へのリンク。

---

## 7. やってはいけないこと

- `BridalApplication.java` 以外の Java ファイル、HTML、CSS、JavaScript を作らない。
- `application-local.properties` などの個人用設定ファイルを作らない。
- AWS SDK や AWS 関連の設定を追加しない。
- 新しい Oracle コンテナを作らない。既存コンテナを停止・削除しない。
- 授業用のユーザー（`SPRING_USER` `SSSUSER` など）とそのテーブルに触らない。
- Spring Boot 4 系、Java 21 以上の機能を使わない。
- `docs/` の Word・PowerPoint・Excel ファイルを編集しない（改訂済みのものをコミットするだけ）。
- コーディング規約の中身を変更しない（移動だけ）。
- API キーをリポジトリのどこにも書かない。
- ユーザーの確認なしに push・強制 push・ブランチ削除・`git reset --hard` をしない。

---

## 8. 完成条件（最後にすべて確認して、証拠付きで報告すること）

| No. | 条件 | 証拠として見せるもの |
|---|---|---|
| 1 | `bridal-app` がビルドできる | `mvnw.cmd -q clean package -DskipTests` の成功ログ |
| 2 | SQL が上から順に全部通る | `00`→`01`→`02`→`03` の実行結果、`99`→`01`→`02`→`03` の再実行結果 |
| 3 | テーブルが設計書どおり | CREEPY の `USER_TABLES` の一覧が T-01〜T-15 の15件と一致すること、主要テーブルの `USER_TAB_COLUMNS` と設計書 7-2 節の対照表 |
| 4 | アプリが起動し、DB につながる | 起動ログに `HikariPool-1 - Start completed.` が出ること（ブラウザでの確認は不要） |
| 5 | フォルダ構成が規約どおり | `bridal-app/src` 以下のフォルダ一覧が、手順3とコーディング規約2章に一致すること。24画面・15テーブルがすべて、いずれかの機能フォルダに割り当てられていること |
| 6 | 余計なものを作っていない | `bridal-app` 内の Java ファイルが `BridalApplication.java` の1つだけであること、HTML・個人用設定ファイルが無いこと |
| 7 | 秘密情報が Git に入っていない | API キーがリポジトリのどこにも書かれていないこと（`git grep` の結果） |
| 8 | 改行コードの問題が解消している | `.gitattributes` 追加後の `git status` が、作業中の変更以外クリーンであること |
| 9 | 手順書どおりに再現できる | 手順書の各手順と、実際に行った作業の対応表 |
| 10 | 7章の禁止事項に触れていない | 7章の各項目に対する確認結果 |

---

## 9. プルリクエストに書くこと

このブランチには、環境構築に加えて次の資料改訂が含まれる。レビュアーが気づけるよう、PR の説明に一覧で書く。

1. **基本設計書 v1.6 を追加**（v1.5 はそのまま残す）
   - 開発・発表をローカル環境で行う方針に変更（1-2・3-1・3-2・8-3・8-4節、7章の S3 表記）
   - AI を Claude API から Gemini API（無料枠）に変更（1-2・3-1・3-3・6章）
   - SQL インジェクション対策を JDBCテンプレート から Spring Data JPA に変更（8-3節）
2. **コーディング規約2章を改訂**：パッケージを「機能ごと」に分ける構成と、画面・テーブルとの対応表
3. **説明資料（pptx）の表記を v1.6 に合わせて修正**
4. 手順5で「設計書に記載なし・仮にこうした」とした点の一覧
5. 要件定義書（提出済み）には「AWS を使用」の記載が残っている。修正するかはチームで判断する

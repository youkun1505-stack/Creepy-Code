# 環境構築指示書（Claude Code 向け）

AIブライダルプランニング支援システム（P2C：Creepy Code）

作成日：2026-10-06

---

## 0. この指示書の使い方（人間向け）

1. Creepy-Code リポジトリを `C:\pleiades\2023-12\workspace\Creepy-Code` にクローンする（OneDrive の中には置かない）。
2. 授業で作った Oracle のコンテナ（`oracle21c` や `oracle-xe` など、名前は人によって違ってよい）を Docker Desktop で起動しておく。
3. そのフォルダで Claude Code を開き、次のように頼む。

   > `docs/04_環境構築/環境構築指示書_ClaudeCode向け.md` を読んで、この指示書どおりに環境構築して。作業の前に計画を見せて、私がOKしてから始めて。

4. 途中で Claude Code から質問されたら答える。最後に「8. 完成条件」がすべて満たされたか報告してもらう。

---

## 1. 目的

このリポジトリの中に、Spring Boot アプリの実装環境 `bridal-app/` を作る。メンバー全員が同じ手順で、自分の PC（Windows）のローカル環境でアプリを起動できる状態にするのがゴール。

**今回やるのは環境構築だけ。** 画面や機能の本格的な実装はやらない（機能の実装はガントチャートでメンバーに割り当て済み）。作るのは、動作確認用の最小限のコードと、メンバー向けの見本だけにする。

---

## 2. 決定事項（チームで決めたことなので変えないこと）

| 項目 | 決定内容 | 理由・補足 |
|---|---|---|
| 実行環境 | **ローカルのみ。AWS は使わない** | 去年の先輩もローカルで実行していた。設計書3-2節のAWS構成は今回は使わない |
| Java | **17** | 授業（Pleiades 2023-12）と同じ |
| Spring Boot | **3.5 系の最新パッチ** | 授業は 3.1 系。書き方が同じ 3 系の最終版を使う。**4 系にはしない** |
| ビルド | Maven（Maven Wrapper 付き） | 授業と同じ |
| DB アクセス | **Spring Data JPA** | 授業で習った方式。設計書 8-3 節の「JDBCテンプレート」から変更（9章参照） |
| DB | **授業で作った Oracle XE のコンテナを使い回す** | 新しいコンテナは作らない。`localhost:1521/XEPDB1` に、このプロジェクト専用ユーザー `CREEPY` を作る |
| JDBC ドライバ | `ojdbc11` | 授業と同じ |
| 画面 | Thymeleaf ＋ Thymeleaf Layout Dialect ＋ Bootstrap 5 | コーディング規約5章で `layout:decorate` を使うと決めている |
| 認証 | Spring Security（フォーム認証・BCrypt） | 設計書 8-2 節 |
| フォルダ名 | `bridal-app`（リポジトリ直下） | 設計書・マニュアルと同じリポジトリで管理する |
| パッケージ | `com.creepycode` | コーディング規約2章 |
| IDE | Eclipse（Pleiades 2023-12）で「既存 Maven プロジェクト」としてインポートする | メンバー全員が使っている |
| DB クライアント | Oracle SQL Developer | メンバー全員が使っている |

### AWS の代わりに使うもの

| 設計書での AWS | ローカルでの代わり |
|---|---|
| EC2 | 各自の PC で Eclipse から起動 |
| RDS（Oracle） | 授業の Oracle XE コンテナ（`XEPDB1` の `CREEPY` ユーザー） |
| S3（添付ファイル・会話履歴md） | `bridal-app/uploads/` フォルダ（Git には入れない） |
| SES（パスワードリセットメール） | 実際には送らず、リセット URL をログに出す |
| CloudWatch Logs | Spring Boot の標準ログ（コンソール） |

### Claude API

- ローカルでもインターネット経由で呼ぶ。API キーは各自の `application-local.properties` に書き、**Git には絶対に入れない**。
- キーがなくても動くように、`app.ai.mode=dummy`（固定の文章を返す）と `app.ai.mode=api`（本物を呼ぶ）を切り替えられるようにする。初期値は `dummy`。
- 今回は**呼び出し口（インターフェース）とダミー実装だけ**作る。本物の API 実装は機能実装の担当者が行う。

---

## 3. 参照する資料

| 資料 | 場所 | 使い方 |
|---|---|---|
| 基本設計書 v1.5（抜粋） | `docs/04_環境構築/参考_基本設計書v1.5抜粋.md` | テーブル定義（7章）・認証（8-2）・データ保護（8-3）の根拠。**テーブル作成 SQL はこの7-2節に完全に従う** |
| 基本設計書 v1.5（正本） | `docs/00_ブライダル業界/03_基本設計書/v1.5/基本設計書_v1.5_1.docx` | 抜粋と食い違ったらこちらを優先する |
| コーディング規約 | 今は `src/コーディング規約.md`（手順1で `docs/04_開発ルール/` に移動する） | 命名規則・パッケージ構成・フォーマット（**インデントは半角スペース4つ**） |
| 授業の見本プロジェクト | （メンバーの PC の `C:\pleiades\2023-12\workspace\spring_practice` など。リポジトリには無い） | Entity・Repository の書き方を授業に合わせる参考。見られなければ無視してよい |

---

## 4. 作業前の確認（最初に必ず行い、結果を報告すること）

次のどれかが満たされていなければ、**作業を始めずにユーザーに報告して指示を待つ。**

1. **Java 17 が使えるか**：`java -version` を確認する。PATH に無い場合は Pleiades 同梱の `C:\pleiades\2023-12\java\17\bin\java.exe` があるか確認する（Maven Wrapper の実行には `JAVA_HOME` をそこに向ける）。
2. **Oracle コンテナが動いているか**：`docker ps --filter "publish=1521"` で、ポート1521を公開しているコンテナが1つ動いているか確認する。**コンテナ名は人によって違うので、名前を決め打ちしない。**
3. **SYSTEM で XEPDB1 に入れるか**：上で見つけたコンテナに対して `docker exec -i <コンテナ名> sqlplus -s system/<パスワード>@//localhost:1521/XEPDB1` で `SELECT 1 FROM dual;` が通るか確認する。授業の初期パスワードは `systemsss` だが、違う場合はユーザーに聞く。
4. **Git の状態**：`git status` で未コミットの変更がないか確認する。あれば内容を報告し、どうするかユーザーに聞く（勝手に破棄・コミットしない）。

---

## 5. ブランチとコミットのルール

- `main` を最新にしてから、`feature/env-setup` ブランチを作って作業する。
- コミットメッセージは既存ルールの形式（`feat:` `fix:` `docs:` `chore:` など）で、日本語で書く。
- 手順ごとに意味のまとまりでコミットを分ける（例：`chore: .gitattributes を追加`、`feat: bridal-app の雛形を作成`）。
- **push とプルリクエストの作成は、ユーザーに確認してから行う。**

---

## 6. 作業手順

### 手順1：リポジトリの整備

1. **`.gitattributes` をリポジトリ直下に作る。** 目的は、Windows と Git の間で改行コードが勝手に変わり、中身が同じなのに「変更あり」と表示される問題を防ぐこと。
   - `* text=auto`
   - `mvnw` は `eol=lf`、`*.cmd` `*.bat` は `eol=crlf`
   - `*.docx *.xlsx *.pptx *.pdf *.png *.jpg *.jpeg *.gif *.jar` は `binary`
   - 追加したら、既存ファイルの改行を正規化するコミット（`git add --renormalize .`）を**別コミット**にする。その前に、差分が改行だけであることを `git diff --ignore-cr-at-eol --stat` で確認して報告する。
2. **`.gitignore` に追記する**：`bridal-app/uploads/`。既存の記述は消さない（`application-local.properties` と `.project` `.classpath` `.settings/` はすでに除外済み）。
3. **コーディング規約を移動する**：`git mv src/コーディング規約.md docs/04_開発ルール/コーディング規約.md`。空になった `src/` は削除する（Spring Boot の `src/main/java` と紛らわしいため）。
4. **コーディング規約の2章「フロントエンド（テンプレート）」に2行追加する**（改訂履歴にも1行追加）。
   - `auth … ログイン・会員登録・パスワードリセット（P-02〜P-05）`
   - `admin … 管理者向け画面（P-18〜P-20, P-23, P-24）`
   - あわせてバックエンドの構成に `exception … 独自例外クラス`、`client … 外部API（Claude API）の呼び出し` を追加する。

### 手順2：Spring Boot プロジェクトの作成

`bridal-app/` を Spring Initializr（https://start.spring.io）相当の内容で作る。

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

### 手順3：フォルダ構成

```
bridal-app/
├─ pom.xml / mvnw / mvnw.cmd / .mvn/
├─ sql/
│   ├─ 00_create_user.sql       ← SYSTEM で実行：CREEPY ユーザー作成
│   ├─ 01_create_tables.sql     ← CREEPY で実行：T-01〜T-15
│   ├─ 02_insert_master.sql     ← CREEPY で実行：マスタの初期データ
│   ├─ 03_insert_testdata.sql   ← CREEPY で実行：動作確認用データ
│   └─ 99_drop_tables.sql       ← CREEPY で実行：全テーブル削除（作り直し用）
├─ uploads/                     ← 実行時に自動作成。Git に入れない
└─ src/
    ├─ main/
    │   ├─ java/com/creepycode/
    │   │   ├─ BridalApplication.java
    │   │   ├─ controller/  service/  repository/  entity/  dto/  config/
    │   │   ├─ exception/
    │   │   └─ client/
    │   └─ resources/
    │       ├─ application.properties
    │       ├─ application-local.properties.example
    │       ├─ templates/
    │       │   ├─ layout/  fragments/  auth/  customer/  planner/  admin/
    │       │   └─ index.html
    │       └─ static/
    │           ├─ css/  js/  images/
    └─ test/java/com/creepycode/
```

- 中身がまだ無いパッケージやフォルダには `.gitkeep` を置き、Git で空フォルダが消えないようにする。
- 各パッケージに `package-info.java` を置き、何を入れる場所かを1行のコメントで書く（コーディング規約2章の説明と同じ文言）。

### 手順4：設定ファイル

**`application.properties`（全員共通・Git に入れる）**

- `spring.application.name=bridal-app`
- `spring.profiles.active=local`
- `server.port=8080`
- `spring.thymeleaf.cache=false`
- `spring.jpa.hibernate.ddl-auto=none`（テーブルは SQL ファイルで作る。**JPA に勝手にテーブルを作らせない**）
- `spring.jpa.show-sql=true`
- `spring.servlet.multipart.max-file-size=10MB` と `max-request-size=10MB`（設計書 8-3）
- `server.servlet.session.timeout=30m`（設計書 8-2）
- `app.storage.dir=./uploads`
- `app.ai.mode=dummy`
- 各行に、何のための設定かを日本語でコメントする。

**`application-local.properties.example`（ひな形・Git に入れる）**

各自がこれをコピーして `application-local.properties` を作る。中身：

- `spring.datasource.url=jdbc:oracle:thin:@localhost:1521/XEPDB1`
- `spring.datasource.username=CREEPY`
- `spring.datasource.password=creepycode`
- `spring.datasource.driver-class-name=oracle.jdbc.OracleDriver`
- `app.ai.api-key=`（空欄。本物を使う人だけ書く）

`application-local.properties` 本体は `.gitignore` で除外済みなので、**作成はするがコミットしない**。コミット前に `git status` で含まれていないことを必ず確認する。

### 手順5：SQL ファイル

すべての SQL ファイルは、SQL Developer のワークシートで「スクリプトの実行（F5）」をしても動くように書く。冒頭にコメントで「どのユーザーで実行するか」を書く。

**`00_create_user.sql`（SYSTEM で実行）**

- `ALTER SESSION SET CONTAINER = XEPDB1;`
- `CREATE USER CREEPY IDENTIFIED BY creepycode;`
- 権限は必要なものだけ与える：`CREATE SESSION`、`CREATE TABLE`、`CREATE SEQUENCE`、`CREATE VIEW`、`QUOTA UNLIMITED ON USERS`。`GRANT ALL PRIVILEGES` は使わない。
- **授業のユーザー（`SPRING_USER` `SSSUSER` など）には一切触らない。**

**`01_create_tables.sql`（CREEPY で実行）**

- `参考_基本設計書v1.5抜粋.md` の 7-2 節に書かれた T-01〜T-15 を、**カラム名・データ型・制約をそのまま**作る。
- 「PK / 自動採番」は `GENERATED BY DEFAULT AS IDENTITY` で作る。
- 外部キー（FK）・UNIQUE・複合ユニーク（T-11・T-13 の `(PLAN_ID, EVAL_SEQ)`）・CHECK 制約（`IS_VARIABLE` の Y/N、スコアの 1〜5、`ROLE`、`STATUS` の値）も作る。
- 親テーブルから順に作る（USERS → … の順。FK の参照先が先にできるように）。
- 各テーブル・カラムに `COMMENT ON` で日本語の説明を付ける（SQL Developer で説明が見えるようにするため）。
- 設計書で判断がつかない点（例：FK の `ON DELETE` の扱い）は**勝手に決めず**、作業報告に「設計書に記載なし・仮にこうした」と一覧で書く。

**`02_insert_master.sql`（CREEPY で実行）**

- `ARCHETYPE_MASTER`、`PRICE_MASTER` に、動作確認用の仮データを数件ずつ入れる。仮データであることをコメントで明記する。

**`03_insert_testdata.sql`（CREEPY で実行）**

- `USERS` に、ロールごとに1人ずつ（CUSTOMER / PLANNER / ADMIN）テストユーザーを作る。
- パスワードは全員 `Password1`（設計書の条件「8文字以上・半角英数字の両方を含む」を満たす）。BCrypt でハッシュ化した値を入れ、どの平文のハッシュかをコメントに書く。

**`99_drop_tables.sql`（CREEPY で実行）**

- 全テーブルを子テーブルから順に `DROP TABLE ... CASCADE CONSTRAINTS PURGE` する。

### 手順6：動作確認用の最小コード

ここで作るのは「環境が正しく動くことの確認」と「メンバーが真似する見本」だけ。

1. **見本の Entity と Repository**：`entity/User.java`（T-01 USERS）と `repository/UserRepository.java`（`JpaRepository` を継承、`findByEmail` を1つ）。コーディング規約の命名規則に従う。授業の書き方（`@Entity` `@Table` `@Column`、getter/setter）に合わせる。Lombok は使わない（授業で使っていないため）。
2. **共通レイアウト**：`templates/layout/default.html`（Bootstrap を WebJar から読み込み、ヘッダー・フッターを持つ）。
3. **トップページ（疎通確認）**：`/` を開くと `index.html` が `layout:decorate` で共通レイアウトを継承して表示され、「DB接続OK：USERS テーブルの件数 = N 件」が出る。`IndexController` → `UserService` → `UserRepository` の順に呼ぶ（規約の実装順序の見本にする）。
4. **Spring Security の仮設定**：`config/SecurityConfig.java`。`PasswordEncoder` は BCrypt の Bean を定義する。今回はログイン機能を実装しないため、全ページを `permitAll` にし、`// TODO: ログイン機能実装時に権限設定を行う（担当者名）` を残す。
5. **AI 呼び出し口**：`client/AiClient.java`（インターフェース）と `client/DummyAiClient.java`（`app.ai.mode=dummy` のとき使われ、固定文字列を返す）。本物の実装クラスは作らない。
6. **テスト**：`BridalApplicationTests`（コンテキストが起動すること）。DB が無い環境でもテストが落ちないようにするかはユーザーに相談する。

### 手順7：メンバー向けドキュメント

1. **`docs/04_環境構築/環境構築手順書.md` を作る。** 読み手はプログラミング初心者のチームメンバー。専門用語には一言説明を付け、画面操作は「どのメニューを押すか」まで書く。内容：
   1. 前提：Pleiades 2023-12・Docker Desktop・授業の Oracle コンテナ・SQL Developer・SourceTree が入っていること
   2. リポジトリを `C:\pleiades\2023-12\workspace\Creepy-Code` にクローンする（OneDrive の中に置かない理由も一言）
   3. Docker Desktop で Oracle コンテナを起動する（コンテナ名は人によって違ってよいこと）
   4. SQL Developer で SYSTEM 接続を作り、`00_create_user.sql` を実行
   5. SQL Developer で CREEPY 接続（ユーザー `CREEPY` / パスワード `creepycode` / サービス名 `XEPDB1`）を作り、`01`〜`03` を順番に実行
   6. `application-local.properties.example` をコピーして `application-local.properties` を作る
   7. Eclipse で「ファイル → インポート → Maven → 既存 Maven プロジェクト」から `bridal-app` を読み込む
   8. `BridalApplication` を右クリック → 「実行 → Spring Boot アプリケーション」
   9. ブラウザで http://localhost:8080 を開き、「DB接続OK」が出れば完了
   10. DB を作り直したいとき：`99_drop_tables.sql` → `01`〜`03` を再実行
   11. よくあるトラブル（ORA-12514 / ORA-01017 / ポート8080が使用中 / Java のバージョン違い / 文字化け）と対処
2. **`README.md` を更新する**：リポジトリの説明、フォルダ構成の一覧、手順書へのリンク。

---

## 7. やってはいけないこと

- AWS SDK や AWS 関連の設定を追加しない。
- 新しい Oracle コンテナを作らない。既存コンテナを停止・削除しない。
- 授業用のユーザー（`SPRING_USER` `SSSUSER` など）とそのテーブルに触らない。
- Spring Boot 4 系、Java 21 以上の機能を使わない。
- `docs/` の Word・PowerPoint・Excel ファイルを編集しない。
- パスワード以外の秘密情報（API キーなど）や `application-local.properties` をコミットしない。
- ログイン・事前ヒアリングなどの機能を実装しない（手順6の範囲を超えない）。
- ユーザーの確認なしに push・強制 push・ブランチ削除・`git reset --hard` をしない。

---

## 8. 完成条件（最後にすべて確認して、証拠付きで報告すること）

| No. | 条件 | 証拠として見せるもの |
|---|---|---|
| 1 | `bridal-app` がビルドできる | `mvnw.cmd -q clean package -DskipTests` の成功ログ |
| 2 | SQL が上から順に全部通る | `00`→`01`→`02`→`03` の実行結果、`99`→`01`→`02`→`03` の再実行結果 |
| 3 | テーブルが設計書どおり | CREEPY の `USER_TABLES` の一覧が T-01〜T-15 の15件と一致すること、主要テーブルの `USER_TAB_COLUMNS` と設計書7-2節の対照表 |
| 4 | アプリが起動し、DB につながる | 起動ログと、http://localhost:8080 で「DB接続OK」が表示されること |
| 5 | 秘密情報が Git に入っていない | `git status` と `git ls-files` に `application-local.properties` が無いこと |
| 6 | 改行コードの問題が解消している | `.gitattributes` 追加後の `git status` が、作業中の変更以外クリーンであること |
| 7 | 手順書どおりに再現できる | 手順書の各手順と、実際に行った作業の対応表 |
| 8 | 7章の禁止事項に触れていない | 7章の各項目に対する確認結果 |

---

## 9. レビュアーへの申し送り（PR の説明に書くこと）

設計書 v1.5 と実装環境で違う点。設計書（docx）の修正はチームが行うので、Claude Code は PR の説明に一覧で書くだけでよい。

1. **設計書 8-3 節**：「SpringのJDBCテンプレートを使用」→「Spring Data JPA を使用（内部でプリペアドステートメントを使うため SQL インジェクション対策は維持される）」
2. **設計書 3-1・3-2 節**：開発・発表はローカル環境で行い、AWS の各サービスは2章の表のとおりローカルの仕組みで代わりにする
3. **コーディング規約 2 章**：テンプレートに `auth` `admin`、パッケージに `exception` `client` を追加した
4. 手順5で「設計書に記載なし・仮にこうした」とした点の一覧

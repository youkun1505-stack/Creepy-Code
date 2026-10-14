-- =========================================
-- 03_insert_testdata.sql
-- 実行場所：Supabase の SQL Editor（管理担当が1回だけ実行する。全員共通のDBなので各自では実行しない）
-- 内容：動作確認用のテストユーザーをロールごとに1人ずつ作る
--       01_create_tables.sql の後に実行する
--
-- ※ 2026-10-14 に共有の Supabase（プロジェクト名：Creepy Code Tamaura）で実行済み。もう一度実行するとエラーや二重登録になるので実行しない
-- ※ 名前・メールアドレスはすべて架空のもの（example.com はテスト用に予約されたドメイン）
-- ※ パスワードは3人とも「Password1」
--    PASSWORD_HASH は「Password1」を BCrypt（強度10）でハッシュ化した値
-- =========================================

-- テーブルは creepy スキーマに置く（00_create_schema.sql で作成済みであること）
SET search_path TO creepy;

-- 顧客（CUSTOMER）
INSERT INTO USERS (EMAIL, PASSWORD_HASH, NAME, ROLE, CREATED_AT, UPDATED_AT)
VALUES ('customer@example.com', '$2a$10$LivispeBHZIEmRlIUzNb1.qVd9jebU/MOuwHSfXdqeRdej6w6ioJu', 'テスト 顧客', 'CUSTOMER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- プランナー（PLANNER）
INSERT INTO USERS (EMAIL, PASSWORD_HASH, NAME, ROLE, CREATED_AT, UPDATED_AT)
VALUES ('planner@example.com', '$2a$10$XcgdP6Jvq76KJkKiP./fT.DvgSwFubHcwZa61gHy2L92k1dA7CkMm', 'テスト プランナー', 'PLANNER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 管理者（ADMIN）
INSERT INTO USERS (EMAIL, PASSWORD_HASH, NAME, ROLE, CREATED_AT, UPDATED_AT)
VALUES ('admin@example.com', '$2a$10$lKfLN0tVdGZHdRpAtB8ksuteYhCAJlTUeFDXv237VrxsHZlMXMR56', 'テスト 管理者', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- =========================================
-- 03_insert_testdata.sql
-- 実行ユーザー：CREEPY
-- 内容：動作確認用のテストユーザーをロールごとに1人ずつ作る
--       01_create_tables.sql の後に実行する
--
-- ※ 現時点では実行しない（環境構築ではスキーマ作成 00 までにとどめる）
-- ※ 名前・メールアドレスはすべて架空のもの（example.com はテスト用に予約されたドメイン）
-- ※ パスワードは3人とも「Password1」
--    PASSWORD_HASH は「Password1」を BCrypt（強度10）でハッシュ化した値
-- =========================================

-- 顧客（CUSTOMER）
INSERT INTO USERS (EMAIL, PASSWORD_HASH, NAME, ROLE, CREATED_AT, UPDATED_AT)
VALUES ('customer@example.com', '$2a$10$LivispeBHZIEmRlIUzNb1.qVd9jebU/MOuwHSfXdqeRdej6w6ioJu', 'テスト 顧客', 'CUSTOMER', SYSTIMESTAMP, SYSTIMESTAMP);

-- プランナー（PLANNER）
INSERT INTO USERS (EMAIL, PASSWORD_HASH, NAME, ROLE, CREATED_AT, UPDATED_AT)
VALUES ('planner@example.com', '$2a$10$XcgdP6Jvq76KJkKiP./fT.DvgSwFubHcwZa61gHy2L92k1dA7CkMm', 'テスト プランナー', 'PLANNER', SYSTIMESTAMP, SYSTIMESTAMP);

-- 管理者（ADMIN）
INSERT INTO USERS (EMAIL, PASSWORD_HASH, NAME, ROLE, CREATED_AT, UPDATED_AT)
VALUES ('admin@example.com', '$2a$10$lKfLN0tVdGZHdRpAtB8ksuteYhCAJlTUeFDXv237VrxsHZlMXMR56', 'テスト 管理者', 'ADMIN', SYSTIMESTAMP, SYSTIMESTAMP);

COMMIT;

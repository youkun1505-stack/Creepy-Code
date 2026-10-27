-- =========================================
-- 00_create_user.sql
-- 実行ユーザー：SYSTEM
-- 内容：このプロジェクト専用のユーザー（スキーマ）CREEPY を作る
--
-- ※ 授業のユーザー（SPRING_USER・SSSUSER など）には一切触らない
-- ※ 2回目に実行すると ORA-01920（ユーザー名が既にある）のエラーになる。
--    すでに作成済みという意味なので、そのままで問題ない
-- =========================================

-- 授業でも使っている XEPDB1 の中に作る
ALTER SESSION SET CONTAINER = XEPDB1;

-- ユーザー（＝スキーマ）を作る。データは USERS 表領域に置き、容量の上限はなし
CREATE USER CREEPY IDENTIFIED BY creepycode
    DEFAULT TABLESPACE USERS
    QUOTA UNLIMITED ON USERS;

-- 必要な権限だけを与える（GRANT ALL PRIVILEGES は使わない）
GRANT CREATE SESSION TO CREEPY;
GRANT CREATE TABLE TO CREEPY;
GRANT CREATE SEQUENCE TO CREEPY;
GRANT CREATE VIEW TO CREEPY;

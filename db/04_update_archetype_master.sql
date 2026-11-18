-- =========================================
-- 04_update_archetype_master.sql
-- 実行場所：Supabase の SQL Editor（管理担当が1回だけ実行する。全員共通のDBなので各自では実行しない）
-- 内容：ARCHETYPE_MASTER（かんたん質問 STEP1「どんな式にしたい？」のカード）を、2026-10-27 に決めた5種類にする
--       02_insert_master.sql の後に実行する
--
-- ※ 2026-10-27 に共有の Supabase（プロジェクト名：Creepy Code Tamaura）で実行済み。もう一度実行しない
-- ※ 02 で入れた【仮】の3件（アットホーム派・ゴージャス派・ナチュラル派）を新しいカードの 1〜3 に書き換え、4・5 を追加する
--   書き換えるのは名前と説明文だけで、ARCHETYPE_ID は変わらない（画面は ARCHETYPE_ID の順に並べる）
-- ※ このファイルを書いたときは「まだイメージできない」を ARCHETYPE_ID = NULL で表す予定だったが、
--   2026-10-27 に取りやめ、代わりに6枚目「シンプルに、無理なく」を 05_add_archetype_simple.sql で追加した
-- ※ もう一度実行しても二重登録にならないように、追加は「同じ名前がまだ無いときだけ」にしている
-- =========================================

-- テーブルは creepy スキーマに置く（00_create_schema.sql で作成済みであること）
SET search_path TO creepy;

BEGIN;

-- 1〜3：【仮】の3件を書き換える（02 で入れた順＝ARCHETYPE_ID の順）
UPDATE ARCHETYPE_MASTER
SET NAME = '家族や親しい人と、ゆったりと',
    DESCRIPTION = '少人数で、一人ひとりとゆっくり話せる式'
WHERE NAME = 'アットホーム派';

UPDATE ARCHETYPE_MASTER
SET NAME = 'ゲスト全員で、わいわいと',
    DESCRIPTION = '余興や演出で、みんなで盛り上がる式'
WHERE NAME = 'ゴージャス派';

UPDATE ARCHETYPE_MASTER
SET NAME = '写真や映像に、たくさん残したい',
    DESCRIPTION = 'あとで何度も見返したくなる式'
WHERE NAME = 'ナチュラル派';

-- 4・5：追加する
INSERT INTO ARCHETYPE_MASTER (NAME, DESCRIPTION, IMAGE_URL)
SELECT '華やかに、ふたりが主役の一日を', 'ドレスや会場、演出で特別な一日に', NULL
WHERE NOT EXISTS (SELECT 1 FROM ARCHETYPE_MASTER WHERE NAME = '華やかに、ふたりが主役の一日を');

INSERT INTO ARCHETYPE_MASTER (NAME, DESCRIPTION, IMAGE_URL)
SELECT '和の伝統を大切に、厳かに', '白無垢や神社など、日本らしく誓う式', NULL
WHERE NOT EXISTS (SELECT 1 FROM ARCHETYPE_MASTER WHERE NAME = '和の伝統を大切に、厳かに');

COMMIT;

-- 確認用：5件が、上の 1〜5 の順に並んでいれば成功
SELECT ARCHETYPE_ID, NAME, DESCRIPTION FROM ARCHETYPE_MASTER ORDER BY ARCHETYPE_ID;

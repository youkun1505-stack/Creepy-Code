-- =========================================
-- 05_add_archetype_simple.sql
-- 実行場所：Supabase の SQL Editor（管理担当が1回だけ実行する。全員共通のDBなので各自では実行しない）
-- 内容：ARCHETYPE_MASTER に、かんたん質問 STEP1 の6枚目のカード「シンプルに、無理なく」を追加する
--       04_update_archetype_master.sql の後に実行する
--
-- ※ 2026-10-27 に「まだイメージできない」をなくし、代わりにこのカードを入れると決めた
--   （方向が決まらない回答を残すと、AI のプランとプランナーへの引き継ぎで手がかりが減るため）
--   「まだイメージできない」を選びそうな人（費用や準備の負担を抑えたい人・おまかせにしたい人）の受け皿にする
-- ※ もう一度実行しても二重登録にならないように、追加は「同じ名前がまだ無いときだけ」にしている
-- =========================================

-- テーブルは creepy スキーマに置く（00_create_schema.sql で作成済みであること）
SET search_path TO creepy;

-- 6：追加する
INSERT INTO ARCHETYPE_MASTER (NAME, DESCRIPTION, IMAGE_URL)
SELECT 'シンプルに、無理なく', '費用や準備の負担をおさえて、大事なところだけにしぼる式', NULL
WHERE NOT EXISTS (SELECT 1 FROM ARCHETYPE_MASTER WHERE NAME = 'シンプルに、無理なく');

-- 確認用：6件が、1「家族や親しい人と、ゆったりと」〜 6「シンプルに、無理なく」の順に並んでいれば成功
SELECT ARCHETYPE_ID, NAME, DESCRIPTION FROM ARCHETYPE_MASTER ORDER BY ARCHETYPE_ID;

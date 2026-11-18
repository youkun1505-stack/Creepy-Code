-- =========================================
-- 06_insert_visual_images.sql
-- 実行場所：Supabase の SQL Editor（管理担当が1回だけ実行する。全員共通のDBなので各自では実行しない）
-- 内容：VISUAL_IMAGES（かんたん質問 STEP2「好きな雰囲気は？」の画像）に 18件を入れる
--       05_add_archetype_simple.sql の後に実行する
--
-- ※ STEP2 は2段階（2026-10-27 に決定）
--   1回目：テイストごとに1枚（6枚）。STYLE_TAGS は {"taste":"natural"} の形
--   2回目：「好き」のテイストだけ、細かいテイスト2つを並べて比べる（12枚）。STYLE_TAGS は {"taste":"natural","detail":"garden"} の形
--   2回目の2枚の組は同じ種類（比べやすくするため）、1回目の1枚はそれと違う種類にしてある
-- ※ 種類（CATEGORY）：VENUE＝式場／DRESS＝衣装（ドレス・和装）／DECORATION＝装飾
--   テイスト（taste）：natural＝ナチュラル／gorgeous＝華やか／chic＝シック・大人／cute＝かわいい・淡色／japanese＝和／
--                      unique＝個性・ふたりらしさ
--   細かいテイスト（detail）：garden＝ガーデン／botanical＝ボタニカル／luxury＝ラグジュアリー／princess＝プリンセス／
--                              modern＝モダン／classic＝クラシック／korean＝淡色・韓国風／girly＝ガーリー／
--                              traditional＝伝統／wamodern＝和モダン／hobby＝趣味・テーマ／fes＝カジュアル・フェス
-- ※ 画像はいまは仮（bridal-app/src/main/resources/static/images/visual/ の SVG）。
--   本物の写真にするときは、ファイルを置き、IMAGE_URL を UPDATE する（IMAGE_ID と STYLE_TAGS は変えない）
-- ※ 画面に出す順は、テイストの順（HearingVisualService が STYLE_TAGS を見て並べる）。入れる順は問わない
-- ※ もう一度実行しても二重登録にならないように、追加は「同じ IMAGE_URL がまだ無いときだけ」にしている
-- =========================================

-- テーブルは creepy スキーマに置く（00_create_schema.sql で作成済みであること）
SET search_path TO creepy;

BEGIN;

-- 1：1回目：装飾｜ナチュラル
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DECORATION', '/images/visual/decoration-natural.svg', '{"taste":"natural"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/decoration-natural.svg');

-- 2：1回目：式場｜華やか
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'VENUE', '/images/visual/venue-gorgeous.svg', '{"taste":"gorgeous"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/venue-gorgeous.svg');

-- 3：1回目：衣装｜シック・大人
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DRESS', '/images/visual/dress-chic.svg', '{"taste":"chic"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/dress-chic.svg');

-- 4：1回目：式場｜かわいい・淡色
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'VENUE', '/images/visual/venue-cute.svg', '{"taste":"cute"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/venue-cute.svg');

-- 5：1回目：装飾｜和
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DECORATION', '/images/visual/decoration-japanese.svg', '{"taste":"japanese"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/decoration-japanese.svg');

-- 6：1回目：式場｜個性・ふたりらしさ
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'VENUE', '/images/visual/venue-unique.svg', '{"taste":"unique"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/venue-unique.svg');

-- 7：2回目：式場｜ガーデン
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'VENUE', '/images/visual/venue-natural-garden.svg', '{"taste":"natural","detail":"garden"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/venue-natural-garden.svg');

-- 8：2回目：式場｜ボタニカル
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'VENUE', '/images/visual/venue-natural-botanical.svg', '{"taste":"natural","detail":"botanical"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/venue-natural-botanical.svg');

-- 9：2回目：衣装｜ラグジュアリー
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DRESS', '/images/visual/dress-gorgeous-luxury.svg', '{"taste":"gorgeous","detail":"luxury"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/dress-gorgeous-luxury.svg');

-- 10：2回目：衣装｜プリンセス
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DRESS', '/images/visual/dress-gorgeous-princess.svg', '{"taste":"gorgeous","detail":"princess"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/dress-gorgeous-princess.svg');

-- 11：2回目：式場｜モダン
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'VENUE', '/images/visual/venue-chic-modern.svg', '{"taste":"chic","detail":"modern"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/venue-chic-modern.svg');

-- 12：2回目：式場｜クラシック
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'VENUE', '/images/visual/venue-chic-classic.svg', '{"taste":"chic","detail":"classic"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/venue-chic-classic.svg');

-- 13：2回目：装飾｜淡色・韓国風
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DECORATION', '/images/visual/decoration-cute-korean.svg', '{"taste":"cute","detail":"korean"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/decoration-cute-korean.svg');

-- 14：2回目：装飾｜ガーリー
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DECORATION', '/images/visual/decoration-cute-girly.svg', '{"taste":"cute","detail":"girly"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/decoration-cute-girly.svg');

-- 15：2回目：衣装｜伝統
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DRESS', '/images/visual/dress-japanese-traditional.svg', '{"taste":"japanese","detail":"traditional"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/dress-japanese-traditional.svg');

-- 16：2回目：衣装｜和モダン
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DRESS', '/images/visual/dress-japanese-wamodern.svg', '{"taste":"japanese","detail":"wamodern"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/dress-japanese-wamodern.svg');

-- 17：2回目：装飾｜趣味・テーマ
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DECORATION', '/images/visual/decoration-unique-hobby.svg', '{"taste":"unique","detail":"hobby"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/decoration-unique-hobby.svg');

-- 18：2回目：装飾｜カジュアル・フェス
INSERT INTO VISUAL_IMAGES (CATEGORY, IMAGE_URL, STYLE_TAGS)
SELECT 'DECORATION', '/images/visual/decoration-unique-fes.svg', '{"taste":"unique","detail":"fes"}'
WHERE NOT EXISTS (SELECT 1 FROM VISUAL_IMAGES WHERE IMAGE_URL = '/images/visual/decoration-unique-fes.svg');

COMMIT;

-- 確認用：18件（1〜6 が1回目、7〜18 が2回目）が入っていれば成功
SELECT IMAGE_ID, CATEGORY, IMAGE_URL, STYLE_TAGS FROM VISUAL_IMAGES ORDER BY IMAGE_ID;

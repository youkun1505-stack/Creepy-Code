-- =========================================
-- 00_create_schema.sql
-- 実行場所：Supabase の SQL Editor（管理担当が1回だけ実行する。全員共通のDBなので各自では実行しない）
-- 内容：このプロジェクト専用のスキーマ（テーブルを入れる箱）creepy を作る
--
-- ※ Supabase に最初からある public スキーマは使わない
--    public に置いたテーブルは、Supabase の Data API（インターネット経由の入口）から
--    見える設定になっているため。creepy スキーマは Data API の対象外なので、
--    アプリ（bridal-app）からの DB 接続でしか読み書きできない
-- ※ 2026-10-14 に共有の Supabase（プロジェクト名：Creepy Code Tamaura）で実行済み
-- ※ 2回実行してもエラーにならない（すでにある場合は何もしない）
-- =========================================

CREATE SCHEMA IF NOT EXISTS creepy;

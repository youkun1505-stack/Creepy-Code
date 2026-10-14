# Creepy-Code

企画開発用リポジトリ

AIブライダルプランニング支援システム（P2C：Creepy Code）の設計書・マニュアルと、アプリ本体（Spring Boot）をまとめて管理しています。

## 使っている技術

- Java 17 / Spring Boot 3.5 / Maven
- Spring Data JPA / Supabase（PostgreSQL。チーム全員で1つのプロジェクトを共有）
- Thymeleaf ＋ Thymeleaf Layout Dialect ＋ Bootstrap 5
- Spring Security（フォーム認証・BCrypt）
- AI：Sign in with ChatGPT（ChatGPT のプランの利用枠で呼ぶ。API キーは使わない。初期状態はダミーモード）
  - Java から直接は使えないため、Node.js の中継プログラム（`ai-relay/`）を経由する

アプリは各自の PC（ローカル環境）で動かし、データベースだけインターネット上の Supabase を使います（AWS は使いません）。
データベースの接続情報は環境変数で渡し、このリポジトリには書きません（公開リポジトリのため）。

## フォルダ構成

```
Creepy-Code/
├─ bridal-app/        … アプリ本体（Eclipse で「既存 Maven プロジェクト」として読み込む）
├─ ai-relay/          … AI 用の中継プログラム（Node.js。本物の AI を使う PC でだけ動かす）
├─ db/                … Supabase の SQL Editor で実行する SQL（管理担当が実行）
│   ├─ 00〜03_*.sql         … スキーマ・テーブル・仮データ・テストユーザーの作成（共有DBで実行済み）
│   └─ 保留/                … 全テーブル削除の SQL（ふだんは実行しない）
└─ docs/
    ├─ 00_ブライダル業界/   … 業界調査・基本設計書など
    ├─ 01_テンプレート/
    ├─ 02_提出物/
    ├─ 03_Git&Sourcetree操作マニュアル/
    ├─ 04_環境構築/         … 環境構築手順書
    └─ 04_開発ルール/       … コーディング規約
```

## 資料

- [Pullしたらやること](docs/04_環境構築/Pullしたらやること.md) … まずはこれ。アプリを動かすまでの最短手順
- [環境構築手順書](docs/04_環境構築/環境構築手順書.md) … くわしい手順とトラブル対処
- [ai-relay の使い方](ai-relay/README.md) … 本物の AI を使うときの中継プログラム
- [コーディング規約](docs/04_開発ルール/コーディング規約.md) … 命名規則・パッケージ構成（機能ごと）など
- [基本設計書 v1.6](docs/00_ブライダル業界/03_基本設計書/v1.6/基本設計書_v1.6.docx)

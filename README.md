# Creepy-Code

企画開発用リポジトリ

AIブライダルプランニング支援システム（P2C：Creepy Code）の設計書・マニュアルと、アプリ本体（Spring Boot）をまとめて管理しています。

## 使っている技術

- Java 17 / Spring Boot 3.5 / Maven
- Spring Data JPA / Oracle Database XE（各自の PC の Docker 上）
- Thymeleaf ＋ Thymeleaf Layout Dialect ＋ Bootstrap 5
- Spring Security（フォーム認証・BCrypt）
- Gemini API（無料枠。初期状態はダミーモード）

開発・発表はすべてローカル環境で行います（AWS は使いません）。

## フォルダ構成

```
Creepy-Code/
├─ bridal-app/        … アプリ本体（Eclipse で「既存 Maven プロジェクト」として読み込む）
├─ db/                … SQL Developer で実行する SQL
│   ├─ 00_create_user.sql   … CREEPY ユーザー（スキーマ）の作成（SYSTEM で実行）
│   └─ 保留/                … テーブル作成などの SQL（今は実行しない）
└─ docs/
    ├─ 00_ブライダル業界/   … 業界調査・基本設計書など
    ├─ 01_テンプレート/
    ├─ 02_提出物/
    ├─ 03_Git&Sourcetree操作マニュアル/
    ├─ 04_環境構築/         … 環境構築手順書
    └─ 04_開発ルール/       … コーディング規約
```

## 資料

- [環境構築手順書](docs/04_環境構築/環境構築手順書.md) … 最初にこれを見て、自分の PC でアプリを起動する
- [コーディング規約](docs/04_開発ルール/コーディング規約.md) … 命名規則・パッケージ構成（機能ごと）など
- [基本設計書 v1.6](docs/00_ブライダル業界/03_基本設計書/v1.6/基本設計書_v1.6.docx)

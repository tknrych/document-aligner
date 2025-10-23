# ---------------------------------
# STAGE 1: ビルド環境 (MavenでJavaアプリをビルド)
# ---------------------------------
# ベースイメージはJava 17 に合わせる
FROM maven:3.9-eclipse-temurin-17 AS builder

# アプリケーションコードをコピー
WORKDIR /build
COPY pom.xml .
COPY src ./src

# Mavenでアプリケーションをビルド
# (dmcjava.jarはビルド時には不要。実行時にProcessBuilderで呼び出すため)
RUN mvn package -DskipTests

# ---------------------------------
# STAGE 2: 実行環境 (TextPorter + Spring Boot)
# ---------------------------------
# ベースイメージは元のDockerfile と合わせる
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# 1. TextPorterのネイティブライブラリと関連ファイルをコピー (元のDockerfile より)
COPY workdir/Linux_X86_64/Lib /app/Lib
COPY workdir/Linux_X86_64/dmc_java /app/dmc_java

# 2. 環境変数を設定 (元のDockerfile より)
# .so ファイルを検索するためのパス
ENV LD_LIBRARY_PATH /app/Lib
# 文字コード変換データ (base2) とライセンスファイルのパス
ENV DMC_TBLPATH /app/Lib/base2/

# 3. ビルドしたSpring Bootアプリをコピー
COPY --from=builder /build/target/*.jar app.jar

# 4. アップロードファイル用と抽出テキスト用の一時ディレクトリを作成
RUN mkdir /app/uploads
RUN mkdir /app/extracted_text

# Webサーバーのポート (Spring Bootのデフォルトは8080)
EXPOSE 8080

# 5. 実行
# 元のENTRYPOINT にあった -Xss4m を引き継ぎ、
# Spring Bootアプリ (app.jar) を起動する
ENTRYPOINT ["java", "-Xss4m", "-jar", "app.jar"]
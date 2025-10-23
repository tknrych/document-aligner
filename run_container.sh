#!/bin/bash

# 実行するコンテナイメージ名
IMAGE_NAME="localhost/document-aligner:latest"

# 実行中のコンテナに付ける名前 (停止/ログ確認時に使用)
CONTAINER_NAME="document-aligner-server"

echo "--- 以前のコンテナが残っていれば停止・削除します ---"
podman stop "$CONTAINER_NAME" 2>/dev/null
podman rm "$CONTAINER_NAME" 2>/dev/null

echo "--- Webサーバーコンテナを起動します ---"
echo "イメージ: $IMAGE_NAME"
echo "プロキシを設定します"
echo "Azure APIキーを .env ファイルから読み込みます"
echo "------------------------------------"

podman run \
  --name "$CONTAINER_NAME" \
  -it \
  --rm \
  -e http_proxy=http://proxy.jp.ricoh.com:8080/ \
  -e https_proxy=http://proxy.jp.ricoh.com:8080/ \
  -e no_proxy=127.0.0.1,localhost,10.41.40.228,10.41.40.229 \
  --env-file ./.env \
  -p 8080:8080 \
  "$IMAGE_NAME"

echo "--- 処理が完了しました ---"
echo "Webサーバーが http://localhost:8080 で起動しました。"
echo "コンテナを停止するには: podman stop $CONTAINER_NAME"
echo "ログを確認するには:     podman logs -f $CONTAINER_NAME"
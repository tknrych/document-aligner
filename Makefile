export http_proxy  := http://proxy.jp.ricoh.com:8080/
export https_proxy := http://proxy.jp.ricoh.com:8080/
export no_proxy    := 127.0.0.1,localhost,10.41.40.228,10.41.40.229

IMAGE_NAME=localhost/document-aligner:latest 

build: 
	podman build --no-cache -t $(IMAGE_NAME) -f Dockerfile .


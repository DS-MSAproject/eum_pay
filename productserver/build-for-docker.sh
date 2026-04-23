# itemserver 이미지 생성
docker build -t eum/productserver:0.0.1-SNAPSHOT --build-arg JAR_FILE=build/libs/productserver-0.0.1-SNAPSHOT.jar ./productserver
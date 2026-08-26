# 在 docker 下先执行安装，之后可以在如 Mac M1 电脑上，执行构建 amd、arm 镜像，也可以只在 Mac 电脑构建 amd 镜像(去掉另外一个配置就可以)
# docker buildx create --name mybuilder
# docker buildx use mybuilder
# docker buildx inspect --bootstrap

docker buildx build --load --platform linux/amd64,linux/arm64 -t fuzhengwei/ai-agent-station-front-app:1.0 .
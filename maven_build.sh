#!/bin/bash
#
PROJECT_DIR=$(
  cd "$(dirname "$0")"
  pwd
)
BUILD_TARGET="$PROJECT_DIR/target"

mvn clean package -Dmaven.test.skip=true

if [ -f "$BUILD_TARGET/llama-java-chat-1.1.0.jar" ]; then
  mkdir -p "$BUILD_TARGET/build"
  cp -r $BUILD_TARGET/llama-java-chat-1.1.0.jar $BUILD_TARGET/build
  cp -r $PROJECT_DIR/conf $BUILD_TARGET/build
  cp -r $PROJECT_DIR/server.sh $BUILD_TARGET/build
  cd "$BUILD_TARGET"
  tar -zvcf llama-java-chat.tar.gz --exclude=.DS_Store build/*
fi



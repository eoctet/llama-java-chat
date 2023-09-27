#!/bin/bash
mvn clean package -Dmaven.test.skip=true -P $1
exit
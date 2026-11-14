#!/bin/sh
cd "$(dirname "$0")"
java -Dfile.encoding=UTF-8 src/Servidor.java

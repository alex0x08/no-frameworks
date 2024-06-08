#!/bin/sh

rm -rf target/

javac -cp ./src/main/java -d target/classes src/main/java/com/Ox08/noframeworks/FeelsLikeAServer.java
cp -R ./src/main/resources/* target/classes/
echo 'Manifest-Version: 1.0' > target/manifest.mf
echo 'Main-Class: com.Ox08.noframeworks.FeelsLikeAServer' >> target/manifest.mf


jar cfm  target/likeAServer.jar target/manifest.mf -C target/classes .




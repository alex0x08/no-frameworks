[![ru](https://img.shields.io/badge/lang-ru-red.svg)](https://github.com/alex0x08/teleporta/blob/main/README.ru.md)

# No frameworks

This is my small guestbook application written in plain Java, without any frameworks and libraries. At all.
There is no Spring/Hibernate, no JSP/JSF, no Servlets - nothing that you've seen before.

Features:
* REST & JSON
* Users and authentication
* Data persistence
* Simple EL parser
* Template engine.

All of these completely *without* any frameworks or libraries.

* ~ 800 lines of code,
* ~ 1200 with comments
* ~70kb executable JAR

# Articles

This project has been created for my [article](https://blog.0x08.ru/no-frameworks) (russian), which has been [published on Habr](https://habr.com/ru/articles/841574/).


# In action

Below some screenshots of working application.
[See it](https://www.youtube.com/watch?v=13R17_-_w5w) in action on Youtube. 

All on desktop:
![In short](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks.jpg?raw=true)

Main screen with English locale:
![In action](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks-main-en.png?raw=true)

Main screen with Russian locale:
![In action](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks-main-ru.png?raw=true)

Login screen:
![Auth](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks-auth.png?raw=true)

Logged In:
![Authenticated](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks-authenticated.png?raw=true)

# How to build

There is a bash script in root folder, which can be used to build project without any external build tools:

```
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH
./build.sh
```
Another option - build with Apache Maven:

```
mvn clean package
```


The final executable `jar`, could be found in the `target` folder.

[![en](https://img.shields.io/badge/lang-en-grey.svg)](https://github.com/alex0x08/no-frameworks/blob/main/README.md)

# No frameworks

Это проект приложения 'гостевой книги', реализованный на чистой Java, без каких-либо внешних зависимостей, фреймворков или библиотек. Совсем.
Тут нет ни Spring/Hibernate ни JSP/JSF ни сервлетов - ничего что вы уже видели в других проектах.

Фичи:
* Реализация REST & JSON
* Пользователи и авторизация
* Хранилище данных
* Простейший парсер выражений (аналог EL)
* Простейший движок шаблонизатора страниц.

Все это *абсолютно без* каких-либо фреймворков или библиотек.

* ~ 800 строк кода,
* ~ 1200 с комментариями
* ~ 70kb итоговый выполняемый JAR

# Статьи

Проект был создан для моей [статьи](https://blog.0x08.ru/no-frameworks), которая позже была [опубликована на Хабре](https://habr.com/ru/articles/841574/).

# В действии

Ниже несколько скриншотов работающего приложения с демонстрацией функционала.
Также можно [посмотреть в действии](https://www.youtube.com/watch?v=13R17_-_w5w) на Youtube.

Все вместе:
![In short](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks.jpg?raw=true)

Основной экран с английской локалью:
![In action](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks-main-en.png?raw=true)

Основной экран с русской локалью:
![In action](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks-main-ru.png?raw=true)

Авторизация:
![Auth](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks-auth.png?raw=true)

После авторизации:
![Authenticated](https://github.com/alex0x08/no-frameworks/blob/main/images/no-frameworks-authenticated.png?raw=true)

# Сборка

В корне проекта находится шелл-скрипт на Bash, которым можно собрать приложение без каких-либо внешних инструментов сборки:

```
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH
./build.sh
```
В качестве альтернативного способа можно использовать Apache Maven:

```
mvn clean package
```

Итоговое выполняемое приложение будет находиться в каталоге 'target'.

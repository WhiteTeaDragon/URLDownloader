Чтобы собрать проект из исходников, надо выполнить в текущей директории команду
    `mvn clean package`
    
После этого надо перейти в папку target, где будет находиться файл ud.jar

Пример запуска программы:
    `java -jar ud.jar -u "http://www.1543.ru/"`

Есть три аргумента командной строки, не включая help, для просмотра информации о них надо запустить команду
    `java -jar ud.jar -h`
## Hello

#### HelloUDP
* Клиент и сервер, взаимодействующие по UDP.
* Класс `HelloUDPClient` отправляет запросы на сервер, принимает результаты и выводит их на консоль.
    * Аргументы командной строки:
        * имя или ip-адрес компьютера, на котором запущен сервер;
        * номер порта, на который отсылать запросы;
        * префикс запросов (строка);
        * число параллельных потоков запросов;
        * число запросов в каждом потоке.
    * Запросы одновременно отсылаются в указанном числе потоков. Каждый поток ожидает обработки своего запроса и выводит сам запрос и результат его обработки на консоль. Если запрос не был обработан, он посылается заново.
    * Запросы формируются по схеме `<префикс запросов><номер потока>_<номер запроса в потоке>`.
* Класс `HelloUDPServer` принимает задания, отсылаемые классом `HelloUDPClient` и отвечает на них.
    * Аргументы командной строки:
        * номер порта, по которому будут приниматься запросы;
        * число рабочих потоков, которые будут обрабатывать запросы.
    * Ответ на запрос: `Hello, <текст запроса>`.
    * Если сервер не успевает обрабатывать запросы, прием запросов может быть временно приостановлен.

#### HelloNonblockingUDP

* Клиент и сервер, взаимодействующие по UDP, используется только неблокирующий ввод-вывод.
* Класс `HelloUDPNonblockingClient` имеет функциональность аналогичную `HelloUDPClient`, но без создания новых потоков.
* Класс `HelloUDPNonblockingServer` имеет функциональность аналогичную `HelloUDPServer`, но все операции с сокетом производятся в одном потоке.
* В реализации нет активных ожиданий, в том числе через `Selector`.

#### Тестирование
* Для того, чтобы протестировать программу:
   * Скачайте
      * тесты
          * [info.kgeorgiy.java.advanced.base.jar](artifacts/info.kgeorgiy.java.advanced.base.jar)
          * [info.kgeorgiy.java.advanced.hello.jar](artifacts/info.kgeorgiy.java.advanced.hello.jar)
      * и библиотеки к ним:
          * [junit-4.11.jar](lib/junit-4.11.jar)
          * [hamcrest-core-1.3.jar](lib/hamcrest-core-1.3.jar)
          * [jsoup-1.8.1.jar](lib/jsoup-1.8.1.jar)
          * [quickcheck-0.6.jar](lib/quickcheck-0.6.jar)
   * Откомпилируйте программу
   * Протестируйте программу
      * Текущая директория должна:
         * содержать все скачанные `.jar` файлы;
         * содержать скомпилированные классы;
         * __не__ содержать скомпилированные самостоятельно тесты.
      * Сервер UDP: ```java -cp . -p . -m info.kgeorgiy.java.advanced.hello server-evil ru.ifmo.rain.shaposhnikov.hello.HelloUDPServer```
      * Клиент UDP: ```java -cp . -p . -m info.kgeorgiy.java.advanced.hello client-evil ru.ifmo.rain.shaposhnikov.hello.HelloUDPClient```
      * Cервер NonblockingUDP: ```java -cp . -p . -m info.kgeorgiy.java.advanced.hello server-evil ru.ifmo.rain.shaposhnikov.hello.HelloUDPNonblockingServer```
      * Клиент NonblockingUDP: ```java -cp . -p . -m info.kgeorgiy.java.advanced.hello client-evil ru.ifmo.rain.shaposhnikov.hello.HelloUDPNonblockingClient```
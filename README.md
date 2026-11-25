<p align="center"><img src=".github/icon.png" width="128" alt="Avragram"></p>

<h1 align="center">Avragram</h1>

<p align="center">Неофициальный Android-клиент Telegram с удалёнными сообщениями, войсченджером и стилями.</p>

## Скачать

Последняя версия лежит в разделе **[Releases](../../releases/latest)**.

| Устройство | Файл |
|---|---|
| Современные телефоны (arm64-v8a), например Honor Magic 7 Lite | `Avragram-arm64.apk` |
| Старые 32-битные устройства (armeabi-v7a), например Galaxy Note 10.1 | `Avragram-armv7.apk` |

Требуется Android 11 или новее.

## Возможности

- **Удалённые сообщения** остаются в чате с пометкой «удалено».
- **Медиа «на один просмотр»** сохраняются в галерею, скриншоты разрешены.
- **Войсченджер** работает в голосовых, кружках и звонках. Включается отдельно для каждого чата: ⋮ → «Войсченджер».
- **Спам**: повтор сообщения N раз с интервалом от 100 мс. Включается через ⋮ → «Спам».
- **Стили**, которые меняют не только цвета, но и форму интерфейса: B Glass (светлый и тёмный), Воздух, Обсидиан, Сигнал, Неоновый закат, Сакура, Океан, Матрица и другие. По умолчанию стоит обычная тема Telegram.
- **13 иконок приложения** с самолётиком.
- **Единая вкладка настроек**, где собраны настройки Telegram, клиента и стилей.
- **Поддержка плагинов из exteraGram.**
- **Исправление ввода стилусом**: клавиатура открывается с первого касания.

## Создатели

- [@mangofag](https://t.me/mangofag)
- [@obijenniy_baka](https://t.me/obijenniy_baka)

О проблемах сообщайте через Настройки → «Сообщить о проблеме». Укажите версию, тему и шаги воспроизведения. Переписки, номера телефонов и ключи не отправляйте.

## Основа и лицензия

Avragram создан на основе **[exteraGram](https://github.com/exteraSquad/exteraGram)** (канал [@exteraGram](https://t.me/exteraGram)). Он не является официальной версией exteraGram или Telegram.
Распространяется по лицензии GNU GPL v3 с дополнительными условиями exteraGram.

## Сборка из исходников

1. `git clone --recursive` этого репозитория.
2. Создайте в корне файл `API_KEYS` и укажите в нём свои `APP_ID` и `APP_HASH`. Получить их можно на https://my.telegram.org. Также нужны пароли от своего keystore. Файл в репозиторий не входит.
3. `./gradlew :TMessagesProj:assembleFullArm64Release` или `assembleFullArmv7Release`.

## Шрифты

Встроенные шрифты распространяются по лицензии SIL Open Font License 1.1: Manrope, Inter, Unbounded, Golos Text, Onest, JetBrains Mono, Rubik, Montserrat, Nunito, Comfortaa, Exo 2, Oswald, Raleway, Lora, Playfair Display, Caveat, Press Start 2P, Kurale, Ruslan Display (© Oleg Snarsky, Denis Masharov, Vladimir Rabdu), Russo One (© Jovanny Lemonad). Ruslan Display и Russo One включены без изменений.

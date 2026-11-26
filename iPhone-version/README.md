# Avragram для iPhone

iPhone-версия Avragram собрана на основе [Swiftgram](https://github.com/Swiftgram/Telegram-iOS). Swiftgram — открытый форк Telegram для iOS, распространяется под GPL. Исходники Swiftgram сюда не копируются, потому что весят несколько гигабайт. Вместо этого здесь хранится только то, что меняет Avragram:

| Путь | Что там |
| --- | --- |
| `SWIFTGRAM_COMMIT` | версия Swiftgram, на которой собирается Avragram |
| `VERSION` | версия Avragram для iPhone |
| `prepare.sh` | превращает исходники Swiftgram в Avragram: название, иконка, api_id, свой код и патчи |
| `branding/` | иконка Avragram |
| `overlay/` | новые модули Avragram на Swift, копируются поверх Swiftgram |
| `patches/` | правки кода Swiftgram и Telegram |

## Сборка

IPA собирается в GitHub Actions на macOS с Xcode 27, процесс описан в `.github/workflows/avragram-ios.yml`. Запустить сборку можно так: Actions → **Avragram iOS** → **Run workflow**. Готовый файл `Avragram-iOS.ipa` появляется в пре-релизе `ios-v<версия>`.

api_id и api_hash не хранятся в репозитории. Сборка берёт их из секретов репозитория `AVRA_API_ID` и `AVRA_API_HASH`.

Собрать на своём Mac (нужен Xcode 27.1):

```sh
git clone https://github.com/Swiftgram/Telegram-iOS.git sg && cd sg
git checkout $(cat ../iPhone-version/SWIFTGRAM_COMMIT) && git submodule update --init --recursive
AVRA_API_ID=… AVRA_API_HASH=… bash ../iPhone-version/prepare.sh .
python3 build-system/Make/ImportCertificates.py --path build-system/fake-codesigning/certs
python3 build-system/Make/Make.py build --configurationPath=build-system/avragram-configuration.json \
  --codesigningInformationPath=build-system/fake-codesigning --configuration=release_arm64 --buildNumber=1
```

## Установка на iPhone (iOS 17 и новее, в том числе iOS 27)

Файл подписан тестовой подписью, поэтому iPhone переподписывает его твоим Apple ID. Платный аккаунт разработчика для этого не нужен.

1. Поставь **SideStore** (https://sidestore.io) по инструкции на сайте. Компьютер понадобится один раз: на нём создаётся файл сопряжения, программа `jitterbugpair` работает и на Linux.
2. На iPhone включи **Настройки → Конфиденциальность и безопасность → Режим разработчика** и перезагрузи телефон.
3. Скачай `Avragram-iOS.ipa` из пре-релиза `ios-v…` и открой его в SideStore. На вопрос о расширениях приложения ответь, что их нужно удалить: бесплатный Apple ID разрешает мало идентификаторов приложений.
4. С бесплатным Apple ID приложение работает 7 дней. SideStore продлевает подпись сам, когда телефон в той же Wi-Fi сети.

Ограничения сборки через SideStore: без расширений не будет виджетов, «Поделиться» и Siri. Пуш-уведомления могут не приходить, потому что Apple отправляет их только приложениям с настоящим App ID Telegram. Сообщения приходят, пока приложение открыто или работает в фоне.

## Что перенесено с Android

Всё доступно в Настройках → **Avragram** (строка вместо «Swiftgram»).

- [x] Название, иконка Avragram (и для iOS 26+ в формате Icon Composer), свой api_id
- [x] Оформление: 35 тем Avragram (палитра, обои-градиент, скругление пузырей, шрифт), 20 шрифтов на весь интерфейс, скругление пузырей
- [x] Видео при запуске: три видео Avragram вперемешку, своё видео или фото с длительностью, предпросмотр
- [x] Значок 🅰 у пользователей Avragram (тот же обмен, что на Android)
- [x] Мут и «Сказано за спиной», сохранение удалённых сообщений
- [x] Спам, саундпад (12 звуков и свои), voice changer для голосовых (10 эффектов и высота)
- [x] Калькулятор и `/calc"…"` в сообщениях
- [x] Развлекуха: AvraBlast, AvraSnake, AvraDrop, AvraRun, AvraBird, Avra48 с рекордами по сложностям
- [x] AvraChess: бот (4 уровня), вдвоём, онлайн с Эло и контролем времени, карточки вызова и итога, обзор игры с оценками ходов, 11 досок, 11 наборов фигур, звуки
- [x] Морской бой: бот (3 уровня) и онлайн с проверкой расстановки, звуки
- [x] Онлайн-игры совместимы с Android-версией: тот же протокол скрытых сообщений
- [x] Проверка новых сборок iPhone-версии на GitHub

Не перенесено: Python-плагины (так решено), «С кем в сети», анимации переходов между экранами и стеклянные стили. В iOS-клиенте Telegram у них совсем другое устройство.

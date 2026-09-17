# MotoHub — Android APK project

Это Android-приложение-обёртка для сайта MotoHub.

**Адрес сайта:** https://motohub.relaxdev.ru/

## Что уже настроено
- Android 7.0+ (minSdk 24)
- полноэкранный WebView без браузерной панели
- JavaScript + localStorage/sessionStorage
- cookies и авторизация сохраняются между запусками
- WebSocket для чата поддерживается WebView
- загрузка фото/видео через формы сайта
- кнопка «Назад» возвращает по истории сайта
- внешние ссылки открываются во внешнем браузере
- HTTPS only
- название и иконка MotoHub

## Сборка APK
1. Откройте папку `motohub-android` в Android Studio.
2. Дождитесь Gradle Sync.
3. Выберите `app`.
4. Build → Build APK(s).

Для релизного APK используйте Build → Generate Signed App Bundle / APK.

> В этой среде Android SDK не установлен, поэтому готовый бинарный APK здесь не собирался. Проект подготовлен для сборки в Android Studio.

## Важно
Приложение использует серверную версию сайта. Пока `https://motohub.relaxdev.ru/` не работает после деплоя, приложение покажет недоступный сайт.

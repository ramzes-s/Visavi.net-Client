# Visavi.net Client for Android

![Android](https://img.shields.io/badge/Android-28%2B-brightgreen.svg?style=flat&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg?style=flat&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack-Compose-blue.svg?style=flat&logo=android)
![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey.svg)

Стороннее мобильное приложение для Android, предназначенное для удобной работы с ресурсом **[Visavi.net](https://visavi.net)**.

> ⚠️ **Дисклеймер**:
> Проект **не имеет прямого отношения** к автору CMS Rotor ([visavi/rotor](https://github.com/visavi/rotor)) и является независимым клиентом, взаимодействующим с общедоступным API сайта Visavi.net.

---

## 🌟 Основные возможности

- 💬 **Форум**: Просмотр категорий, чтение тем, публикация ответов и создание новых тем.
- ✉️ **Диалоги и личные сообщения**: Удобный чат-интерфейс для переписки с пользователями.
- 👤 **Профиль пользователя**: Просмотр аватаров, статусов и пользовательских данных.
- 🖼️ **Медиаконтент**: Лайтбокс для комфортного просмотра изображений во весь экран.
- 🛠️ **Панель форматирования**: Поддержка быстрой вставки форматирования и BB-кодов при ответе.
- 🔔 **Фоновые уведомления**: Встроенный сервис информирования о новых личных сообщениях (`NewMessagesService`).
- 🛡️ **Антифлуд**: Менеджер предотвращения повторной отправки дублирующихся сообщений (`AntifloodManager`).
- 🎨 **Современный дизайн**: Полностью на Jetpack Compose с элементами эффекта Glassmorphic и адаптацией под системные темы.

---

## 🛠 Технологический стек

- **Язык программирования**: Kotlin
- **UI Фреймворк**: Jetpack Compose, Material 3
- **Стекломорфизм / Кастомный дизайн**: Glassmorphism UI компоненты
- **Сеть и API**: Retrofit 2, Gson, OkHttp Logging Interceptor
- **Загрузка и кеширование изображений**: Coil Compose
- **Архитектура**: Android Architecture Components (ViewModel, StateFlow)

---

## 🚀 Сборка и запуск

### Требования
- Android Studio Ladybug (или новее)
- JDK 11+
- Android SDK 36 (минимально поддерживаемая версия Android 9.0 / API 28)

### Инструкция по сборке

1. Клонируйте репозиторий:
   ```bash
   git clone https://github.com/ramzes-s/Visavi.net-Client.git
   cd Visavi.net-Client
   ```

2. Откройте проект в Android Studio.

3. Соберите отладочную версию APK:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📄 Лицензия

Этот проект распространяется под лицензией **Creative Commons Attribution-NonCommercial 4.0 International ([CC BY-NC 4.0](LICENSE))**.

Вы можете свободно использовать, копировать и модифицировать данный исходный код в **некоммерческих** целях при условии обязательного указания авторства.

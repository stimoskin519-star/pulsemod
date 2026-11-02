# RMenu (Fabric 1.21.11, клиентский мод)

Меню в стиле Angry Birds, запись от первого лица, просмотр реплея от 1-го/3-го лица,
скачивание в файлы, оптимизация FPS и метки.

## Клавиши (меняются в «Управление» -> RMenu)
Right Shift — меню · F8 — запись · F9 — пауза реплея · F7 — вид 1-е/3-е лицо · B — метка

## Сборка
1. JDK 21.
2. https://fabricmc.net/develop -> 1.21.11 -> перенесите точные номера
   (yarn_mappings, loader_version, fabric_version, loom_version) в gradle.properties.
3. `gradle wrapper`, затем `./gradlew build` -> build/libs/rmenu-1.0.0.jar в mods/ + Fabric API.

## Папки
- replay_recordings/ — ваши записи
- replay_exports/ — «скачанные» копии (_1p / _3p); положите сюда чужой .rmrp, чтобы импортировать
- config/rmenu.properties, config/rmenu_marks.txt — настройки и метки

## Для максимума FPS
Мод меняет настройки и сам подстраивает дальность («Авто-FPS»), но рендер он не переписывает.
Для большого прироста поставьте рядом Sodium (+ Lithium, Entity Culling) и запускайте
Java 21 с флагами: -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -Xms2G -Xmx4G

# КАМЕРА — Трекер вяления, копчения и ферментации (Android APK)

Офлайн Android-приложение (`com.kamera.curing`, minSdk 24 / targetSdk 35) для учёта вяленого мяса, посола, копчения, вакуумной регидратации оболочки и ферментации острых перцев.

## Что внутри
- `android/src/com/kamera/curing/MainActivity.java` — нативная оболочка с WebView, обработкой системных инсетов и клавиатуры (`WindowInsets.Type.ime()`), динамической окраской статус-бара под цвет этапа, локальным сохранением в `filesDir/kamera_batches_v1.json`, тактильным откликом и системным Share-бэкапом.
- `android/assets/www/index.html` — весь интерфейс, каталог из 14 мясных отрубов и пресетов перцев, нелинейная модель прогноза времени (закон Фика с вычетом пауз в вакууме) и визуализации.
- `generate_icons.py` — генерация иконок приложения для всех плотностей `mdpi`–`xxxhdpi`.
- `build_apk.sh` — сборка и подпись APK (`aapt2` + `javac` + `d8` + `zipalign` + `apksigner`).

## Пересборка APK
```bash
cd kamera_app
./build_apk.sh
```

# CLAUDE.md — правила работы в этом репозитории

Главный проект — Android-приложение **Музыкалка** в `muzykalka/`. Остальные папки (`roman_merzlikin_bday/`) — не трогать.

Перед любой работой прочитай: `muzykalka/docs/ARCHITECTURE.md` → `muzykalka/docs/ROADMAP.md` → `muzykalka/docs/DECISIONS.md`.
Архитектура и правила продуманы заранее. Твоя задача — **реализовывать тикеты, а не перепроектировать**.

## Цикл работы

1. Возьми первый тикет `[ ]` в текущей версии ROADMAP, у которого выполнены зависимости. Пометь `[~]`.
2. Реализуй строго в границах модулей (ARCHITECTURE §3). Логика — в `core:*`, Android — тонкий адаптер.
3. Тесты из критериев приёмки. Для `M-02..M-04` — сними `@Disabled` в `DspAcceptanceTest`; пороги не ослаблять.
4. `cd muzykalka && ./gradlew check assembleRelease` — должно быть зелёным. Никогда не коммить красное.
5. Отметь тикет `[x]` в ROADMAP. Коммит: `<ID>: <что сделано>` (один тикет — один или несколько коммитов, но не смесь тикетов).
6. Push в ветку, которую назначила сессия. Сообщи пользователю: что готово, как проверить на телефоне.

Останавливайся и спрашивай пользователя, если: нужно проверить на телефоне (звук, микрофон, R8);
тикет требует изменить правило из ARCHITECTURE §5, пороги `Graduation`, ключ подписи или схему БД без миграции;
критерий приёмки недостижим.

## Нельзя (без явного разрешения пользователя)

- Менять/перегенерировать `muzykalka/signing/sideload.jks` (потеря данных на телефоне при обновлении).
- `fallbackToDestructiveMigration`, удаление таблиц/колонок без `Migration` и теста.
- Импортировать `android.*`/`androidx.*`/корутины в `core:*`; глушить предупреждения в `core:*`.
- Обходить `RoundEngine`: судить ответы, писать попытки или играть correction мимо него.
- Ослаблять инварианты: `RoundConfig` (60–90 с), `Evaluation` (correction обязателен), `HintStep` (по одной ступеньке).
- Показывать абсолютные имена аккордов вне `HintStep.FULL_SHEET`; ноты буквами — вне метки тональности, Охоты ур.1–3, обратной охоты и тюнера.
- Встраивать чужие песни в приложение сверх сборника `SongBook` (правило 7, ADR-013, ADR-041): только гармония ступенями;
  сборник кладётся в библиотеку сам (по разрешению пользователя), но на полку (карточка не «пора»), без перезаписи правок
  пользователя и без возврата того, что он удалил (`BookSeedStore`).
- Править `core/srs/src/test/resources/fsrs6-golden.json` руками (только через `scripts/fsrs_golden.py`).
- Добавлять модули и зависимости без записи в `DECISIONS.md`. Версии — только в `gradle/libs.versions.toml`.

## Стиль

- Весь текст интерфейса — по-русски, в `strings.xml` или в доменных форматтерах (`RuNames`, `DegreeFormatter`). Код, комментарии, коммиты — по-английски допустимо, ID тикета обязателен.
- Ступени и имена в UI — только через `DegreeFormatter`/`RuNames`, с учётом настроек записи.
- Цвет ступени — только `DegreeColors`. Цели касания в игре — не меньше `GameTouchTarget` (72 dp).
- Compose: состояние — `StateFlow` во ViewModel, экран — stateless composable + превью.
- Время — параметром (`nowMs`, `today`), не `System.currentTimeMillis()` в логике. Случайность — инжектируемый `Random`.

## Окружение (облачная сессия)

Свежий контейнер (SDK нет, Maven Central даёт 429):

```bash
muzykalka/scripts/setup-android-sdk.sh
mkdir -p ~/.gradle/init.d
cat > ~/.gradle/init.d/maven-mirror.init.gradle.kts <<'EOF'
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
beforeSettings {
    pluginManagement.repositories { maven(mirror) }
    dependencyResolutionManagement.repositories { maven(mirror) }
}
EOF
grep -q robolectricRepoUrl ~/.gradle/gradle.properties 2>/dev/null || \
  echo "muzykalka.robolectricRepoUrl=https://maven-central.storage-download.googleapis.com/maven2" >> ~/.gradle/gradle.properties
```

Команды (из `muzykalka/`): `./gradlew :core:music:test` (быстро, один модуль) · `./gradlew check` · `./gradlew assembleRelease`.
APK: `muzykalka/app/build/outputs/apk/release/app-release.apk`. Эмулятора нет: UI проверяется Robolectric-тестами,
звук — синтетическими сигналами в `core:dsp`, остальное — на телефоне у пользователя.

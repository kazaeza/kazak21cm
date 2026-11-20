# Шрифты в облачной сессии

`figcraft-cli info` в конце печатает `missing font "…": N text layers (drawn in a fallback font)`. Это шрифты,
которых нет в облаке: тексты рисуются запасным шрифтом, и рендер отличается от того, что видит человек.
Файл при этом не портится, имена шрифтов в нём остаются прежними. Список можно получить и командой
`text.missingFonts`.

## Что проверено (2026-11-20, FigCraft 0.18.1)

- FigCraft берёт **установленные в системе** шрифты (папка с файлами + `fc-cache -f`). Подмену имён через
  fontconfig (`<alias>`, `<match>`) он **не использует**: Arial не находится, даже если `fc-match Arial`
  отдаёт Liberation Sans.
- Свои копии шрифтов ищутся по имени семейства и стилю из самого файла, поэтому заменитель надо
  **переименовать** внутри файла (таблица `name`).
- `font.download` (скачивание из хранилища Google Fonts) по описанию работает только если человек сам
  включил загрузку шрифтов в Text ▸ Missing fonts, так что в облаке на это рассчитывать нельзя.

## Как поставить

1. **Бесплатные шрифты Google Fonts** лежат в `raw.githubusercontent.com/google/fonts/main/ofl/<семейство>/…ttf`
   (достижимо из облака). Имена файлов: `Caveat[wght].ttf` (скобки кодируй как `%5B`/`%5D`),
   `Pacifico-Regular.ttf` и т.п. Копируй в `/usr/local/share/fonts/<папка>/` и делай `fc-cache -f`.
   Так найдены Abril Fatface, Angkor, Arimo, Bona Nova, Caveat, Cormorant Garamond, Cuprum, Great Vibes,
   Lobster, Mea Culpa, Pacifico, Playfair Display SC (12 семейств, 21 файл).
2. **Платные шрифты** (Arial, Times New Roman, Courier New, Georgia, Book Antiqua, HelveticaNeueCyr,
   SF Pro, Cochin и т.п.) скачать нельзя (лицензия). Ставится близкий по виду заменитель под тем же
   именем: `scripts/make_font_standins.py OUT_DIR Oswald[wght].ttf` (нужен `pip install fonttools`;
   пакеты `fonts-liberation`, `fonts-inter` и `apt-get install fonts-urw-base35` для P052).
   Соответствия: Arial → Liberation Sans, Times New Roman → Liberation Serif, Courier New →
   Liberation Mono, Book Antiqua / Georgia / Cochin → P052 (Palatino, есть кириллица), HelveticaNeueCyr →
   Liberation Sans, SF Pro → Inter, Bebas Neue Cyrillic → Oswald (у Google Bebas Neue кириллицы нет).
   Перед выбором заменителя проверь, что в нём есть кириллица (`getBestCmap()`).
3. Проверь: `figcraft-cli info файл` не должен показывать ненулевых `missing font`.

## Оговорки

- Заменители годятся **только для превью и проверки раскладки** в облаке: ширина и высота букв чуть
  отличаются от настоящих, переносы строк могут уехать. Не вписывай такие шрифты в итоговый файл и не раздавай
  переименованные копии: у платных шрифтов чужие лицензии и торговые марки.
- Если в файле есть блоки с автоподбором размера, `text.refit` пересчитает их под новые шрифты
  (учитывай это перед сохранением: `save_file` запишет уже пересчитанные размеры).
- Шрифты ставятся в контейнер сессии и пропадают вместе с ней: ставь заново в начале работы.

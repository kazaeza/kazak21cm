# Сторис «привет! 🪿❤️» в FigCraft

`privet.steps.json` — скрипт для `figcraft-cli`: кадр **Instagram story** (1080×1920) с розовым градиентом и
текстом «привет! 🪿❤️» по центру.

> Статус: скрипт подготовлен по справочнику скилла (`.claude/skills/figcraft/`), но **ещё не запускался**,
> потому что `figcraft-cli` в этой среде не установлен.

## Как собрать файл

```bash
figcraft-cli run figcraft_story/privet.steps.json --out figcraft_story/privet.figcraft
figcraft-cli export figcraft_story/privet.figcraft -o figcraft_story/privet.png --scale 1
```

`run` выполняет шаги как один откат-пакет: при ошибке ничего не сохранится.

## Что проверить после сборки

- Кириллица рисуется Inter Bold (он есть среди встроенных шрифтов).
- Эмодзи 🪿❤️ могут отрисоваться пустыми квадратами, если в системе нет цветного эмодзи-шрифта. Проверьте
  `figcraft-cli render … -o preview.png` и при необходимости положите эмодзи отдельным слоем (`image.place`).
- Откройте `figcraft_story/privet.figcraft` в приложении FigCraft или проверьте `figcraft-cli info`.

## Установка figcraft-cli

См. `.claude/skills/figcraft/SOURCE.md` (апстрим: https://github.com/ai-2070/claude-storytold-skills, скилл
`storytold-install`) или релизы https://github.com/storytold/figcraft/releases.

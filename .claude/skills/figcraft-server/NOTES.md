# Что проверено (2026-11-19, FigCraft 0.18.1)

Скилл написан по документации FigCraft (`docs/server.md`, `docs/mcp.md`, `docs/collab-protocol.md`,
`docs/online-sharing-terms.md`, коммит `4e35fb9` репозитория storytold/figcraft) и по исходнику
`crates/collab-net/src/tls.rs`.

Проверено в облачной сессии:
- **Публичный сервер.** `https://collab.craftapps.dev/healthz` отвечает `ok`, WebSocket-рукопожатие
  через прокси даёт `101 Switching Protocols`.
- **TLS.** Прокси облака перешифровывает TLS даже у прямых соединений (сертификат от
  «Anthropic Egress Gateway SDS Issuing CA»). Цепочка проверяется с `/root/.ccr/ca-bundle.crt`.
  `FIGCRAFT_CA_FILE` читает все сертификаты из PEM: так написано в `tls.rs`, `pem_file_iter`.
- **`get_figcraft.sh`.** Скачивание, сверка SHA-256, распаковка, а при подмене архива удаление
  файла и выход с кодом 1.
- **`figcraft_live.py`.** Прогнан на заглушке, которая изображает `figcraft-cli mcp`. Шесть
  случаев: вход и правки, отказ при входе, ошибка команды, маскировка и `--show-secrets`, плохой
  файл шагов, падение CLI на старте.

Не проверено, потому что запуск бинарников FigCraft в этой сессии запрещён классификатором auto mode:
- настоящий `figcraft-cli mcp`, `collab.join`, `collab.share` и `collab.publish`;
- `figcraft-server`, в том числе сквозной тест «опубликовал → зашёл по ссылке → правка видна».

Как проверить, когда запуск разрешён: поднять `figcraft-server` на 127.0.0.1 и опубликовать в него
документ из одного `figcraft-cli mcp` (`collab.publish` с адресом из `figcraft-server key`). Затем
зайти по ссылке правки помощником, поменять слой, сделать `sync` и проверить изменение `get_node`
в первом процессе или после повторного входа.

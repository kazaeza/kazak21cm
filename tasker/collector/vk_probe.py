"""Пилот ВК: записывает сетевые ответы vk.com, пока пользователь сам ходит по сайту.

Только чтение. Скрипт не печатает в поле сообщения, не нажимает кнопки и не ставит
реакции. Единственное действие скрипта: медленно прокручивает историю вверх колесом мыши.

Порядок:
  1. Скрипт открывает окно Chrome с отдельным профилем (tasker/.browser-profile/).
  2. Пользователь сам входит в ВК и открывает нужную беседу. Логин, пароль и коды
     скрипт не видит и не записывает: запись ответов включается только после шага 3.
  3. Пользователь нажимает Enter в терминале. Скрипт включает запись.
  4. Скрипт медленно прокручивает историю вверх (не чаще одной подгрузки в 3 секунды).
     При признаках капчи останавливается и ничего не обходит.
  5. Пользователь нажимает Enter, скрипт сохраняет сводку.

Результат (всё в tasker/data/probe/, папка в .gitignore, не коммитить и никуда не отправлять):
  responses.jsonl  каждый ответ vk.com: url, метод, тело запроса, статус, content-type, тело ответа
  meta.json        адрес открытой беседы и время записи
  summary.json     только адреса и счётчики, без текстов переписки

Внимание: в responses.jsonl попадает не только целевая беседа, но и список диалогов
с превью других личных переписок. Это личные данные.

Запуск:
  python -m venv .venv && .venv/bin/pip install -r collector/requirements.txt   (Windows: .venv\\Scripts\\)
  python collector/vk_probe.py
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent  # tasker/
PROFILE_DIR = ROOT / ".browser-profile"
OUT_DIR = ROOT / "data" / "probe"

# Хосты, ответы которых пишем. Страница авторизации (id.vk.com) в записи не нужна.
VK_SUFFIXES = ("vk.com", "vk.ru", "vkontakte.ru")
SKIP_HOSTS = ("id.vk.com", "id.vk.ru", "login.vk.com", "login.vk.ru", "oauth.vk.com", "oauth.vk.ru")
RECORD_TYPES = ("xhr", "fetch", "document")
MAX_BODY_BYTES = 5 * 1024 * 1024
SCROLL_PAUSE_SEC = 3.0  # не чаще одной подгрузки в 2-3 секунды; берём с запасом
CAPTCHA_MARKERS = ("captcha", "капча")
ACT_RE = re.compile(r"(?:^|&)act=([A-Za-z0-9_.]+)")


def host_matches(host: str, extra: list[str]) -> bool:
    host = host.lower()
    if host in SKIP_HOSTS:
        return False
    suffixes = VK_SUFFIXES + tuple(extra)
    return any(host == s or host.endswith("." + s) for s in suffixes)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--start-url", default="https://vk.com")
    ap.add_argument("--scrolls", type=int, default=4, help="сколько раз прокрутить историю вверх (3-5)")
    ap.add_argument("--channel", default="chrome", help="chrome (установленный Chrome) или chromium")
    ap.add_argument("--executable-path", default=None, help="путь к браузеру, если channel не подходит")
    ap.add_argument("--host", action="append", default=[], help="дополнительный хост для записи (для тестов)")
    ap.add_argument("--headless", action="store_true", help="только для тестов на заглушке; для ВК не нужен")
    args = ap.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    PROFILE_DIR.mkdir(parents=True, exist_ok=True)

    responses_path = OUT_DIR / "responses.jsonl"
    counter: Counter[tuple[str, str, str, int]] = Counter()
    state = {"recording": False, "captcha": False, "saved": 0}

    launch_kwargs: dict = {"headless": args.headless, "no_viewport": True}
    if args.executable_path:
        launch_kwargs["executable_path"] = args.executable_path
    else:
        launch_kwargs["channel"] = args.channel

    with sync_playwright() as pw, responses_path.open("a", encoding="utf-8") as out:
        ctx = pw.chromium.launch_persistent_context(str(PROFILE_DIR), **launch_kwargs)
        page = ctx.pages[0] if ctx.pages else ctx.new_page()

        def on_response(resp) -> None:
            if not state["recording"]:
                return
            try:
                req = resp.request
                if req.resource_type not in RECORD_TYPES:
                    return
                url = resp.url
                host = url.split("/")[2].split(":")[0] if "//" in url else ""
                if not host_matches(host, args.host):
                    return
                try:
                    body = resp.body()
                except Exception:  # редиректы, прерванные запросы
                    body = b""
                truncated = len(body) > MAX_BODY_BYTES
                text = body[:MAX_BODY_BYTES].decode("utf-8", errors="replace")
                if any(m in url.lower() or m in text[:20000].lower() for m in CAPTCHA_MARKERS):
                    state["captcha"] = True
                record = {
                    "ts": datetime.now(timezone.utc).isoformat(),
                    "url": url,
                    "method": req.method,
                    "post_data": req.post_data,
                    "status": resp.status,
                    "content_type": resp.headers.get("content-type", ""),
                    "truncated": truncated,
                    "body": text,
                }
                out.write(json.dumps(record, ensure_ascii=False) + "\n")
                out.flush()
                state["saved"] += 1
                path = url.split("?")[0]
                # Старый мессенджер ВК различает вызовы параметром act (проверить на живой странице).
                m = ACT_RE.search(url.split("?", 1)[1] if "?" in url else "") or ACT_RE.search(req.post_data or "")
                counter[(req.method, path, m.group(1) if m else "", resp.status)] += 1
            except Exception as exc:  # запись не должна ронять сессию
                print(f"[probe] не удалось записать ответ: {exc}", file=sys.stderr)

        page.goto(args.start_url)
        print("\nШаг 1. Войдите в ВК сами в открытом окне (логин, пароль, код).")
        print("Шаг 2. Откройте нужную беседу.")
        print("Если появится капча или запрос подтверждения, решите это сами. Скрипт ничего не обходит.")
        input("Когда беседа открыта, нажмите Enter здесь. Запись ответов начнётся после этого... ")

        meta = {"chat_url": page.url, "started_at": datetime.now(timezone.utc).isoformat()}
        (OUT_DIR / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

        ctx.on("response", on_response)
        state["recording"] = True

        # Перезагрузим беседу, чтобы начальная загрузка тоже попала в запись.
        page.reload(wait_until="domcontentloaded")
        time.sleep(SCROLL_PAUSE_SEC)

        w, h = page.evaluate("[window.innerWidth, window.innerHeight]")
        page.mouse.move(w * 0.6, h * 0.5)  # центр области истории; точное место проверить на живой странице
        for i in range(args.scrolls):
            if state["captcha"]:
                print("\n[probe] Похоже на капчу или запрос подтверждения. Останавливаюсь, ничего не обхожу.")
                print("Решите это сами в окне и запустите скрипт заново, если нужно.")
                break
            page.mouse.wheel(0, -2500)
            print(f"[probe] прокрутка {i + 1}/{args.scrolls}, записано ответов: {state['saved']}")
            time.sleep(SCROLL_PAUSE_SEC)

        input("\nПрокрутку можно досмотреть вручную (медленно). Когда закончили, нажмите Enter... ")
        state["recording"] = False
        ctx.close()

    summary = {
        "saved_responses": state["saved"],
        "captcha_detected": state["captcha"],
        "endpoints": [
            {"method": m, "path": p, "act": a, "status": s, "count": c}
            for (m, p, a, s), c in counter.most_common()
        ],
    }
    (OUT_DIR / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nГотово. Записано ответов: {state['saved']}. Сводка: {OUT_DIR / 'summary.json'}")
    print("Папка data/ в .gitignore. Не коммитьте её и не отправляйте.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

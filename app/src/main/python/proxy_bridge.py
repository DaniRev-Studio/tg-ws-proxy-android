"""
proxy_bridge.py — мост между Android Java-сервисом и Python-прокси.
"""
from __future__ import annotations

import asyncio
import logging
import os
import socket as _socket
from typing import Optional, Any

from proxy.config import proxy_config, parse_dc_ip_list
from proxy.tg_ws_proxy import _run
from proxy.utils import get_link_host

# ── Service instance (set by Java before calling run_and_report) ──────────────
# This is the actual ProxyService *instance* (not class), passed via set_service()
_service_instance: Optional[Any] = None


def set_service(instance: Any) -> None:
    """Called from ProxyService.java to register the instance before starting."""
    global _service_instance
    _service_instance = instance


def _svc() -> Any:
    if _service_instance is None:
        raise RuntimeError("ProxyService instance not set — call set_service() first")
    return _service_instance


def _log(msg: str) -> None:
    """Append a line to the log. appendLog is static so class call is fine."""
    try:
        # appendLog is static — can call on class or instance
        _svc().appendLog(msg)
    except Exception:
        pass


# ── Logging handler ───────────────────────────────────────────────────────────
class _JniLogHandler(logging.Handler):
    def emit(self, record):
        _log(self.format(record))


# ── State ─────────────────────────────────────────────────────────────────────
_stop_event: Optional[asyncio.Event] = None
_loop: Optional[asyncio.AbstractEventLoop] = None


# ── Public API ────────────────────────────────────────────────────────────────
def run_and_report(host: str = "127.0.0.1",
                   port: int = 1443,
                   secret: str = "",
                   dc_ips_raw: str = "2:149.154.167.220\n4:149.154.167.220",
                   cf_domain: str = "",
                   cf_worker: str = "",
                   fallback_cf: bool = True) -> None:
    global _stop_event, _loop

    # Chaquopy passes Java types — cast everything to native Python types first
    host        = str(host)
    port        = int(port)
    secret      = str(secret)
    dc_ips_raw  = str(dc_ips_raw)
    cf_domain   = str(cf_domain).strip()
    cf_worker   = str(cf_worker).strip()
    fallback_cf = bool(fallback_cf)

    # ── Logging ───────────────────────────────────────────────────────────────
    handler = _JniLogHandler()
    handler.setFormatter(logging.Formatter(
        "%(asctime)s %(levelname)s %(message)s", datefmt="%H:%M:%S"))
    root = logging.getLogger()
    root.setLevel(logging.INFO)
    root.handlers.clear()
    root.addHandler(handler)
    logging.getLogger("asyncio").setLevel(logging.WARNING)

    # ── DC IPs ────────────────────────────────────────────────────────────────
    dc_list = [l.strip() for l in dc_ips_raw.strip().splitlines() if l.strip()]
    if not dc_list:
        dc_list = ["2:149.154.167.220", "4:149.154.167.220"]
    try:
        dc_redirects = parse_dc_ip_list(dc_list)
    except Exception as e:
        _log(f"ОШИБКА DC IP: {e} — используются дефолтные")
        dc_redirects = {2: "149.154.167.220", 4: "149.154.167.220"}

    # ── Secret ────────────────────────────────────────────────────────────────
    # Chaquopy passes Java String as jstring — must cast to Python str explicitly
    secret = str(secret or "").strip().lower()
    _log(f"Secret получен: '{secret}' (длина {len(secret)})")
    if len(secret) == 32 and all(c in "0123456789abcdef" for c in secret):
        _log("Secret принят из настроек")
    else:
        secret = os.urandom(16).hex()
        _log(f"Secret автогенерирован: {secret}")
        try:
            _svc().saveGeneratedSecret(secret)
        except Exception:
            pass

    # ── Free port ─────────────────────────────────────────────────────────────
    final_port = _find_free_port(host, port)
    if final_port != port:
        _log(f"Порт {port} занят → использую {final_port}")

    # ── Config ────────────────────────────────────────────────────────────────
    proxy_config.host                = host
    proxy_config.port                = final_port
    proxy_config.secret              = secret
    proxy_config.dc_redirects        = dc_redirects
    proxy_config.buffer_size         = 128 * 1024
    proxy_config.pool_size           = 2
    proxy_config.fallback_cfproxy    = fallback_cf
    proxy_config.cfproxy_user_domain = cf_domain
    proxy_config.cfproxy_worker_domain = cf_worker
    proxy_config.proxy_protocol      = False

    # Start CF domain refresh in background (fetches working domains from GitHub)
    if fallback_cf:
        try:
            from proxy.config import start_cfproxy_domain_refresh
            start_cfproxy_domain_refresh()
            if cf_domain:
                _log(f"CF домен: {cf_domain}")
            elif cf_worker:
                _log(f"CF Worker: {cf_worker}")
            else:
                _log("CF: используется публичный пул доменов")
        except Exception as e:
            _log(f"CF refresh warning: {e}")

    link_host  = get_link_host(proxy_config.host)
    # If binding to 127.0.0.1, the tg:// link still uses 127.0.0.1
    # (correct for same-device use). If binding to 0.0.0.0, get_link_host
    # already returns the real LAN IP. Either way, show both in log.
    proxy_link = (
        f"tg://proxy?server={link_host}"
        f"&port={proxy_config.port}"
        f"&secret=dd{proxy_config.secret}"
    )

    # ── Notify Java via instance method ───────────────────────────────────────
    try:
        _svc().updateNotifFields(proxy_config.secret, proxy_config.port, proxy_link)
    except Exception as e:
        _log(f"JNI updateNotifFields error: {e}")
        return

    _log(f"Адрес:  {host}:{proxy_config.port}")
    _log(f"Secret: {proxy_config.secret}")
    _log(f"DCs:    {', '.join(f'DC{k}={v}' for k, v in sorted(dc_redirects.items()))}")
    _log(f"")
    _log(f"Ссылка: {proxy_link}")
    _log(f"")
    _log("Прокси запущен ✓  —  нажми 'Подключить TG'")

    # ── Event loop ────────────────────────────────────────────────────────────
    _loop = asyncio.new_event_loop()
    asyncio.set_event_loop(_loop)
    _stop_event = asyncio.Event()

    async def _run_with_traffic():
        """Run proxy + periodic traffic reporter."""
        async def _traffic_reporter():
            from proxy.stats import stats
            from proxy.utils import human_bytes
            while not _stop_event.is_set():
                try:
                    up   = human_bytes(int(stats.bytes_up))
                    down = human_bytes(int(stats.bytes_down))
                    _svc().updateTraffic(up, down)
                    _svc().refreshTrafficNotification()
                except Exception:
                    pass
                try:
                    await asyncio.wait_for(
                        asyncio.shield(_stop_event.wait()), timeout=3.0)
                except asyncio.TimeoutError:
                    pass

        reporter = asyncio.create_task(_traffic_reporter())
        try:
            await _run(_stop_event)
        finally:
            reporter.cancel()
            try:
                await reporter
            except asyncio.CancelledError:
                pass

    try:
        _loop.run_until_complete(_run_with_traffic())
    except Exception as e:
        _log(f"Ошибка прокси: {e}")
    finally:
        try:
            # Cancel all pending tasks gracefully to avoid
            # "Task was destroyed but it is pending!" errors
            pending = asyncio.all_tasks(_loop)
            if pending:
                for task in pending:
                    task.cancel()
                # Give tasks a moment to handle CancelledError
                _loop.run_until_complete(
                    asyncio.gather(*pending, return_exceptions=True)
                )
        except Exception:
            pass
        try:
            # Shutdown async generators (Python 3.10+)
            _loop.run_until_complete(_loop.shutdown_asyncgens())
        except Exception:
            pass
        try:
            _loop.close()
        except Exception:
            pass
        _loop       = None
        _stop_event = None
        _log("Прокси остановлен")


def stop_proxy() -> None:
    """Signal the running event loop to stop. Thread-safe."""
    lp, ev = _loop, _stop_event
    if lp is None or lp.is_closed():
        return
    if ev is not None:
        lp.call_soon_threadsafe(ev.set)
    else:
        # Fallback: cancel all tasks directly
        def _cancel_all():
            for task in asyncio.all_tasks(lp):
                task.cancel()
        lp.call_soon_threadsafe(_cancel_all)


def _find_free_port(host: str, preferred: int) -> int:
    for p in [preferred, preferred + 1, preferred + 2, 18443, 0]:
        with _socket.socket(_socket.AF_INET, _socket.SOCK_STREAM) as s:
            s.setsockopt(_socket.SOL_SOCKET, _socket.SO_REUSEADDR, 1)
            try:
                s.bind((host, p))
                return p if p != 0 else s.getsockname()[1]
            except OSError:
                continue
    return preferred

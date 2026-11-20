import hmac
import json
import os
import tempfile
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi.responses import HTMLResponse, RedirectResponse
from fastapi.security import HTTPBasic, HTTPBasicCredentials
from pydantic import BaseModel, Field

app = FastAPI(title="YTPro Control API", version="0.2.0")
security = HTTPBasic()
CONFIG_PATH = Path(os.getenv("CONFIG_FILE", "/var/data/ytpro_config.json"))

DEFAULTS: dict[str, Any] = {
    "app_enabled": True,
    "maintenance_mode": False,
    "min_supported_version_code": 1,
    "latest_version_code": 1,
    "latest_version_name": "0.1.0",
    "update_url": "",
    "maintenance_message": "YTPro está en mantenimiento. Vuelve a intentarlo más tarde.",
    "disabled_message": "YTPro todavía no está habilitada por el administrador.",
}


def admin_auth(credentials: HTTPBasicCredentials = Depends(security)) -> str:
    expected_user = os.getenv("ADMIN_USERNAME", "")
    expected_password = os.getenv("ADMIN_PASSWORD", "")
    if not expected_user or not expected_password:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Panel no configurado. Define ADMIN_USERNAME y ADMIN_PASSWORD en Render.",
        )
    user_ok = hmac.compare_digest(credentials.username.encode(), expected_user.encode())
    password_ok = hmac.compare_digest(credentials.password.encode(), expected_password.encode())
    if not (user_ok and password_ok):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales incorrectas",
            headers={"WWW-Authenticate": "Basic"},
        )
    return credentials.username


def load_config() -> dict[str, Any]:
    config = DEFAULTS.copy()
    # Environment variables provide the initial values on first startup.
    env_map = {
        "APP_ENABLED": ("app_enabled", bool),
        "MAINTENANCE_MODE": ("maintenance_mode", bool),
        "MIN_SUPPORTED_VERSION_CODE": ("min_supported_version_code", int),
        "LATEST_VERSION_CODE": ("latest_version_code", int),
        "LATEST_VERSION_NAME": ("latest_version_name", str),
        "UPDATE_URL": ("update_url", str),
        "MAINTENANCE_MESSAGE": ("maintenance_message", str),
        "DISABLED_MESSAGE": ("disabled_message", str),
    }
    for env_name, (key, kind) in env_map.items():
        raw = os.getenv(env_name)
        if raw is not None:
            if kind is bool:
                config[key] = raw.strip().lower() in {"1", "true", "yes", "on"}
            else:
                try:
                    config[key] = kind(raw)
                except (TypeError, ValueError):
                    pass
    try:
        if CONFIG_PATH.exists():
            saved = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
            if isinstance(saved, dict):
                config.update({k: v for k, v in saved.items() if k in DEFAULTS})
    except (OSError, json.JSONDecodeError):
        pass
    return config


def save_config(config: dict[str, Any]) -> None:
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix="ytpro-", suffix=".json", dir=str(CONFIG_PATH.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as file:
            json.dump(config, file, ensure_ascii=False, indent=2)
            file.flush()
            os.fsync(file.fileno())
        os.replace(temp_name, CONFIG_PATH)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


class AdminConfig(BaseModel):
    app_enabled: bool
    maintenance_mode: bool
    min_supported_version_code: int = Field(ge=1)
    latest_version_code: int = Field(ge=1)
    latest_version_name: str = Field(min_length=1, max_length=40)
    update_url: str = Field(default="", max_length=500)
    maintenance_message: str = Field(max_length=500)
    disabled_message: str = Field(max_length=500)


@app.get("/")
def root():
    return RedirectResponse(url="/admin", status_code=302)


@app.get("/health")
def health():
    return {"ok": True, "service": "ytpro-control-api"}


@app.get("/admin", response_class=HTMLResponse)
def admin_panel(_: str = Depends(admin_auth)):
    return HTMLResponse(r"""
<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>YTPro · Administración</title>
<style>
:root{color-scheme:dark;--bg:#090b0b;--card:#141919;--line:#293333;--txt:#edf4f1;--muted:#9baaa5;--accent:#ff3548;--ok:#36d98b}
*{box-sizing:border-box}body{margin:0;background:radial-gradient(circle at top right,#251116,transparent 38%),var(--bg);color:var(--txt);font:15px system-ui, sans-serif}
main{max-width:850px;margin:36px auto;padding:0 18px}.top{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}
h1{font-size:27px;margin:0}.sub{color:var(--muted);margin:6px 0 22px}.pill{border:1px solid var(--line);padding:7px 12px;border-radius:20px;color:var(--ok)}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:20px;margin:14px 0}
h2{font-size:17px;margin:0 0 16px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}
label{display:block;color:var(--muted);font-size:13px;margin-bottom:6px}input,textarea{width:100%;background:#090e0e;border:1px solid var(--line);border-radius:9px;color:var(--txt);padding:11px;font:inherit}
textarea{min-height:75px;resize:vertical}.check{display:flex;align-items:center;gap:10px;margin:12px 0;color:var(--txt)}.check input{width:18px;height:18px;accent-color:var(--accent)}
button{background:var(--accent);border:0;color:white;font-weight:700;padding:12px 18px;border-radius:10px;font-size:15px;cursor:pointer}
#notice{margin-left:12px;color:var(--ok)}.hint{color:var(--muted);font-size:12px;margin-top:8px}
@media(max-width:600px){.grid{grid-template-columns:1fr}main{margin:22px auto}.card{padding:16px}}
</style></head><body><main>
<div class="top"><div><h1>YTPro Control</h1><div class="sub">Panel privado de administración</div></div><div class="pill">● API protegida</div></div>
<div class="card"><h2>Estado de la aplicación</h2>
<label class="check"><input id="app_enabled" type="checkbox"> Aplicación habilitada</label>
<label class="check"><input id="maintenance_mode" type="checkbox"> Activar modo mantenimiento</label>
<div class="grid"><div><label>Versión mínima obligatoria (código)</label><input id="min_supported_version_code" type="number" min="1"></div>
<div><label>Última versión disponible (código)</label><input id="latest_version_code" type="number" min="1"></div>
<div><label>Nombre de versión</label><input id="latest_version_name" maxlength="40"></div>
<div><label>Enlace de actualización</label><input id="update_url" type="url" placeholder="https://..."></div></div></div>
<div class="card"><h2>Mensajes para los usuarios</h2>
<label>Mensaje de mantenimiento</label><textarea id="maintenance_message" maxlength="500"></textarea>
<label style="margin-top:12px">Mensaje cuando la app está deshabilitada</label><textarea id="disabled_message" maxlength="500"></textarea></div>
<div><button id="save">Guardar configuración</button><span id="notice"></span></div>
<p class="hint">Los cambios se aplican al consultar el estado de la API. Mantén privadas las credenciales de administración.</p>
</main><script>
const ids=["app_enabled","maintenance_mode","min_supported_version_code","latest_version_code","latest_version_name","update_url","maintenance_message","disabled_message"];
async function load(){const r=await fetch("/admin/config");if(!r.ok){document.querySelector("#notice").textContent="No se pudo cargar la configuración";return}
const c=await r.json();for(const id of ids){const e=document.getElementById(id);if(e.type==="checkbox")e.checked=!!c[id];else e.value=c[id]??""}}
document.getElementById("save").addEventListener("click",async()=>{const c={};for(const id of ids){const e=document.getElementById(id);c[id]=e.type==="checkbox"?e.checked:e.type==="number"?Number(e.value):e.value}
const n=document.getElementById("notice");n.textContent="Guardando…";try{const r=await fetch("/admin/config",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(c)});const data=await r.json();if(!r.ok)throw new Error(data.detail||"Error al guardar");n.textContent="Configuración guardada";}catch(e){n.textContent=e.message}});
load();
</script></body></html>
""")


@app.get("/admin/config")
def get_admin_config(_: str = Depends(admin_auth)):
    return load_config()


@app.put("/admin/config")
def put_admin_config(payload: AdminConfig, _: str = Depends(admin_auth)):
    if payload.min_supported_version_code > payload.latest_version_code:
        raise HTTPException(status_code=400, detail="La versión mínima no puede superar la última versión.")
    if payload.update_url and not payload.update_url.startswith(("https://", "http://")):
        raise HTTPException(status_code=400, detail="El enlace debe comenzar con http:// o https://.")
    config = payload.model_dump()
    try:
        save_config(config)
    except OSError as exc:
        raise HTTPException(
            status_code=507,
            detail="No se pudo guardar. Comprueba que el disco persistente de Render esté montado en /var/data.",
        ) from exc
    return {"ok": True, "message": "Configuración guardada", "config": config}


@app.get("/v1/status")
def status(versionCode: int = Query(default=1, ge=1)):
    config = load_config()
    enabled = config["app_enabled"]
    maintenance = config["maintenance_mode"]
    minimum = config["min_supported_version_code"]
    latest = config["latest_version_code"]
    latest_name = config["latest_version_name"]
    update_url = config["update_url"]

    if not enabled:
        return {"allowed": False, "state": "disabled", "code": "APP_DISABLED",
                "message": config["disabled_message"], "latestVersionCode": latest,
                "latestVersionName": latest_name, "updateUrl": update_url}
    if maintenance:
        return {"allowed": False, "state": "maintenance", "code": "MAINTENANCE",
                "message": config["maintenance_message"], "latestVersionCode": latest,
                "latestVersionName": latest_name, "updateUrl": update_url}
    if versionCode < minimum:
        return {"allowed": False, "state": "update_required", "code": "UPDATE_REQUIRED",
                "message": "Debes instalar la actualización obligatoria para continuar.",
                "minimumVersionCode": minimum, "latestVersionCode": latest,
                "latestVersionName": latest_name, "updateUrl": update_url}
    return {"allowed": True, "state": "active", "code": "OK", "message": "YTPro está activa.",
            "latestVersionCode": latest, "latestVersionName": latest_name,
            "updateAvailable": versionCode < latest, "updateUrl": update_url}

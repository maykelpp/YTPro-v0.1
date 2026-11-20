import os
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="YTPro Control API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)

def env_bool(name: str, default: bool = False) -> bool:
    return os.getenv(name, str(default)).strip().lower() in {"1", "true", "yes", "on"}

@app.get("/health")
def health():
    return {"ok": True, "service": "ytpro-control-api"}

@app.get("/v1/status")
def status(versionCode: int = Query(default=1, ge=1)):
    enabled = env_bool("APP_ENABLED", True)
    maintenance = env_bool("MAINTENANCE_MODE", False)
    minimum = int(os.getenv("MIN_SUPPORTED_VERSION_CODE", "1"))
    latest = int(os.getenv("LATEST_VERSION_CODE", str(minimum)))
    latest_name = os.getenv("LATEST_VERSION_NAME", "0.1.0")
    update_url = os.getenv("UPDATE_URL", "")
    maintenance_message = os.getenv(
        "MAINTENANCE_MESSAGE",
        "YTPro está en mantenimiento. Vuelve a intentarlo más tarde."
    )
    disabled_message = os.getenv(
        "DISABLED_MESSAGE",
        "YTPro todavía no está habilitada por el administrador."
    )

    if not enabled:
        return {
            "allowed": False,
            "state": "disabled",
            "code": "APP_DISABLED",
            "message": disabled_message,
            "latestVersionCode": latest,
            "latestVersionName": latest_name,
            "updateUrl": update_url,
        }

    if maintenance:
        return {
            "allowed": False,
            "state": "maintenance",
            "code": "MAINTENANCE",
            "message": maintenance_message,
            "latestVersionCode": latest,
            "latestVersionName": latest_name,
            "updateUrl": update_url,
        }

    if versionCode < minimum:
        return {
            "allowed": False,
            "state": "update_required",
            "code": "UPDATE_REQUIRED",
            "message": "Debes instalar la actualización obligatoria para continuar.",
            "minimumVersionCode": minimum,
            "latestVersionCode": latest,
            "latestVersionName": latest_name,
            "updateUrl": update_url,
        }

    return {
        "allowed": True,
        "state": "active",
        "code": "OK",
        "message": "YTPro está activa.",
        "latestVersionCode": latest,
        "latestVersionName": latest_name,
        "updateAvailable": versionCode < latest,
        "updateUrl": update_url,
    }


from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from src.api.routers.audio import audio_router
from src.api.routers.auth import auth_router
from src.api.routers.transcription import transcription_router
from src.api.routers.user import user_router
from src.config.settings import settings
from src.db.database import engine
from src.exceptions.base import AppError


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield
    await engine.dispose()


app = FastAPI(lifespan=lifespan, root_path="/api")

if settings.CORS_ORIGINS:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.CORS_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

app.include_router(user_router)
app.include_router(auth_router)
app.include_router(audio_router)
app.include_router(transcription_router)


@app.exception_handler(AppError)
async def app_exception_handler(request: Request, exc: AppError):
    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.detail},
    )


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        reload=settings.UVICORN_RELOAD,
        workers=settings.UVICORN_WORKERS_COUNT
    )

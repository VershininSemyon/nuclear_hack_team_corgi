
from typing import Annotated

from fastapi import Cookie, Depends, HTTPException, status

from src.db.database import async_session_factory
from src.db.unitofwork import UnitOfWork
from src.integrations.s3 import S3Service, s3_service
from src.schemas.user import UserReadSchema
from src.services.auth import AuthService
from src.services.user import UserService
from src.services.audio import AudioService
from src.services.transcription import TranscriptionService


def get_uow() -> UnitOfWork:
    return UnitOfWork(async_session_factory)

UOWDep = Annotated[UnitOfWork, Depends(get_uow)]


def get_s3_service() -> S3Service:
    return s3_service

S3ServiceDep = Annotated[S3Service, Depends(get_s3_service)]


def get_user_service(uow: UOWDep) -> UserService:
    return UserService(uow)

UserServiceDep = Annotated[UserService, Depends(get_user_service)]


def get_auth_service(uow: UOWDep) -> AuthService:
    return AuthService(uow)

AuthServiceDep = Annotated[AuthService, Depends(get_auth_service)]


def get_audio_service(uow: UOWDep, s3: S3ServiceDep) -> AudioService:
    return AudioService(uow, s3)

AudioServiceDep = Annotated[AudioService, Depends(get_audio_service)]


def get_transcription_service(uow: UOWDep) -> TranscriptionService:
    return TranscriptionService(uow)

TranscriptionServiceDep = Annotated[TranscriptionService, Depends(get_transcription_service)]


async def get_current_user(
    auth_service: AuthServiceDep,
    access_token: str | None = Cookie(default=None),
) -> UserReadSchema:
    if not access_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Не предоставлен access токен",
        )
    return await auth_service.authenticate_user(access_token)

CurrentUserDep = Annotated[UserReadSchema, Depends(get_current_user)]

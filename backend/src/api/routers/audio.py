
import uuid

from fastapi import APIRouter, File, Form, Response, UploadFile, status

from src.api.dependencies import AudioServiceDep, CurrentUserDep
from src.schemas.audio import (
    AudioCreateSchema,
    AudioReadSchema,
    AudioUpdateSchema,
    AudioWithDataSchema,
)

audio_router = APIRouter(prefix="/audios", tags=["Audio"])


@audio_router.post(
    "/",
    response_model=AudioReadSchema,
    status_code=status.HTTP_201_CREATED,
)
async def create_audio(
    current_user: CurrentUserDep,
    audio_service: AudioServiceDep,
    file: UploadFile = File(...),
    title: str = Form(...),
    description: str | None = Form(None),
) -> AudioReadSchema:
    file_data = await file.read()

    return await audio_service.create_audio(
        user_id=current_user.id,
        audio_data=AudioCreateSchema(title=title, description=description),
        file_data=file_data,
        content_type=file.content_type,
    )


@audio_router.get(
    "/",
    response_model=list[AudioWithDataSchema],
    status_code=status.HTTP_200_OK,
)
async def list_audios(
    current_user: CurrentUserDep,
    audio_service: AudioServiceDep,
) -> list[AudioWithDataSchema]:
    return await audio_service.get_user_audios_with_data(current_user.id)


@audio_router.get(
    "/{audio_id}",
    response_model=AudioWithDataSchema,
    status_code=status.HTTP_200_OK,
)
async def get_audio(
    audio_id: uuid.UUID,
    current_user: CurrentUserDep,
    audio_service: AudioServiceDep,
) -> AudioWithDataSchema:
    return await audio_service.get_audio_with_data(current_user.id, audio_id)


@audio_router.put(
    "/{audio_id}",
    response_model=AudioReadSchema,
    status_code=status.HTTP_200_OK,
)
async def update_audio(
    audio_id: uuid.UUID,
    data: AudioUpdateSchema,
    current_user: CurrentUserDep,
    audio_service: AudioServiceDep,
) -> AudioReadSchema:
    return await audio_service.update_audio(current_user.id, audio_id, data)


@audio_router.delete(
    "/{audio_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
async def delete_audio(
    audio_id: uuid.UUID,
    current_user: CurrentUserDep,
    audio_service: AudioServiceDep,
) -> Response:
    await audio_service.delete_audio(current_user.id, audio_id)

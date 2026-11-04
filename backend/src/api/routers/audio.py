
import uuid

from fastapi import APIRouter, File, Form, UploadFile, status
from src.api.dependencies import AudioServiceDep, CurrentUserDep
from src.schemas.audio import AudioCreateSchema, AudioReadSchema

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
    response_model=list[AudioReadSchema],
    status_code=status.HTTP_200_OK,
)
async def list_audios(
    current_user: CurrentUserDep,
    audio_service: AudioServiceDep,
) -> list[AudioReadSchema]:
    return await audio_service.get_user_audios(current_user.id)


@audio_router.get(
    "/{audio_id}/download-url",
    status_code=status.HTTP_200_OK,
)
async def get_audio_download_url(
    audio_id: uuid.UUID,
    current_user: CurrentUserDep,
    audio_service: AudioServiceDep,
) -> dict[str, str]:
    url = await audio_service.get_download_url(current_user.id, audio_id)
    return {"url": url}

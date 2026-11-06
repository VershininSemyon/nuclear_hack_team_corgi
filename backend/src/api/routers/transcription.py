
import uuid

from fastapi import APIRouter, status

from src.api.dependencies import CurrentUserDep, TranscriptionServiceDep
from src.schemas.transcription import TranscriptionCreateSchema, TranscriptionReadSchema, TranscriptionUpdateSchema

transcription_router = APIRouter(prefix="/audios/{audio_id}/transcriptions", tags=["Transcription"])


@transcription_router.post(
    "/",
    response_model=TranscriptionReadSchema,
    status_code=status.HTTP_201_CREATED
)
async def create_transcription(
    audio_id: uuid.UUID,
    transcription_data: TranscriptionCreateSchema,
    user: CurrentUserDep,
    transcription_service: TranscriptionServiceDep
):
    return await transcription_service.create_transcription(user.id, audio_id, transcription_data)


@transcription_router.get(
    "/",
    response_model=list[TranscriptionReadSchema],
    status_code=status.HTTP_200_OK
)
async def get_audio_transcriptions(
    audio_id: uuid.UUID,
    user: CurrentUserDep,
    transcription_service: TranscriptionServiceDep
):
    return await transcription_service.get_audio_transcriptions(user.id, audio_id)


@transcription_router.get(
    "/{transcription_id}",
    response_model=TranscriptionReadSchema,
    status_code=status.HTTP_200_OK
)
async def get_transcription(
    audio_id: uuid.UUID,
    transcription_id: uuid.UUID,
    user: CurrentUserDep,
    transcription_service: TranscriptionServiceDep
):
    return await transcription_service.get_transcription(user.id, audio_id, transcription_id)


@transcription_router.delete(
    "/{transcription_id}",
    status_code=status.HTTP_204_NO_CONTENT
)
async def delete_transcription(
    audio_id: uuid.UUID,
    transcription_id: uuid.UUID,
    user: CurrentUserDep,
    transcription_service: TranscriptionServiceDep
):
    await transcription_service.delete_transcription(user.id, audio_id, transcription_id)


@transcription_router.put(
    "/{transcription_id}",
    response_model=TranscriptionReadSchema,
    status_code=status.HTTP_200_OK
)
async def update_transcription(
    audio_id: uuid.UUID,
    transcription_id: uuid.UUID,
    transcription_data: TranscriptionUpdateSchema,
    user: CurrentUserDep,
    transcription_service: TranscriptionServiceDep
):
    return await transcription_service.update_transcription(user.id, audio_id, transcription_id, transcription_data)
